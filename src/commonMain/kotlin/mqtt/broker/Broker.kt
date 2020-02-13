package mqtt.broker

import currentTimeMillis
import mqtt.MQTTException
import mqtt.Subscription
import mqtt.matchesWildcard
import mqtt.packets.Qos
import mqtt.packets.mqttv5.MQTTProperties
import mqtt.packets.mqttv5.MQTTPublish
import mqtt.packets.mqttv5.ReasonCode
import removeIf
import socket.ServerSocketLoop
import socket.tls.TLSSettings

class Broker(
    val port: Int = 1883,
    val host: String = "127.0.0.1",
    val backlog: Int = 10000,
    val tlsSettings: TLSSettings? = null,
    val authentication: Authentication? = null,
    val enhancedAuthenticationProviders: Map<String, EnhancedAuthenticationProvider> = mapOf(),
    val authorization: Authorization? = null,
    val maximumSessionExpiryInterval: UInt = 0xFFFFFFFFu,
    val receiveMaximum: UShort? = null,
    val maximumQos: Qos? = null,
    val retainedAvailable: Boolean = true,
    val maximumPacketSize: UInt = 32768u,
    val maximumTopicAlias: Int? = null,
    val wildcardSubscriptionAvailable: Boolean = true,
    val subscriptionIdentifiersAvailable: Boolean = true,
    val sharedSubscriptionsAvailable: Boolean = true,
    val serverKeepAlive: Int? = null,
    val responseInformation: String? = null,
    val packetInterceptor: PacketInterceptor? = null,
    val bytesMetrics: BytesMetrics? = null
) {
    // TODO support WebSocket, section 6

    private val server = ServerSocketLoop(this)
    val sessions = mutableMapOf<String, Session>()
    private val retainedList = mutableMapOf<String, Pair<MQTTPublish, String>>()

    fun listen() {
        server.run()
    }

    private fun publishShared(
        publisherClientId: String,
        shareName: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        dup: Boolean,
        properties: MQTTProperties,
        payload: UByteArray?
    ) {
        // Get the sessions which subscribe to this shared session and get the one which hasn't received a message for the longest time
        val session = sessions.minBy {
            it.value.hasSharedSubscriptionMatching(shareName, topicName)?.timestampShareSent ?: Long.MAX_VALUE
        }?.value
        session?.hasSharedSubscriptionMatching(shareName, topicName)?.let { subscription ->
            publish(publisherClientId, retain, topicName, qos, dup, properties, payload, session, subscription)
            subscription.timestampShareSent = currentTimeMillis()
        }
    }

    fun sendWill(session: Session?) {
        val will = session?.will ?: return
        val properties = MQTTProperties()
        properties.payloadFormatIndicator = will.payloadFormatIndicator
        properties.messageExpiryInterval = will.messageExpiryInterval
        properties.contentType = will.contentType
        properties.responseTopic = will.responseTopic
        properties.correlationData = will.correlationData
        properties.userProperty += will.userProperty
        publish(session.clientId, will.retain, will.topic, will.qos, false, properties, will.payload)
        // The will must be removed after sending
        session.will = null
    }

    fun publish(
        publisherClientId: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        dup: Boolean,
        properties: MQTTProperties,
        payload: UByteArray?
    ) {
        if (!retainedAvailable && retain)
            throw MQTTException(ReasonCode.RETAIN_NOT_SUPPORTED)
        maximumQos?.let {
            if (qos > it)
                throw MQTTException(ReasonCode.QOS_NOT_SUPPORTED)
        }

        val sharedDone = mutableListOf<String>()

        sessions.forEach { session ->
            session.value.hasSubscriptionsMatching(topicName).forEach { subscription ->
                if (subscription.isShared() && sharedSubscriptionsAvailable) {
                    if (subscription.shareName!! !in sharedDone) { // Check we only publish once per shared subscription
                        publishShared(
                            publisherClientId,
                            subscription.shareName,
                            retain,
                            topicName,
                            qos,
                            dup,
                            properties,
                            payload
                        )
                        sharedDone += subscription.shareName
                    }
                } else {
                    publish(
                        publisherClientId,
                        retain,
                        topicName,
                        qos,
                        dup,
                        properties,
                        payload,
                        session.value,
                        subscription
                    )
                }
            }
        }
    }

    private fun publish(
        publisherClientId: String,
        retain: Boolean,
        topicName: String,
        qos: Qos,
        dup: Boolean,
        properties: MQTTProperties,
        payload: UByteArray?,
        session: Session,
        subscription: Subscription
    ) {
        if (subscription.options.noLocal && publisherClientId == session.clientId) {
            return
        }

        subscription.subscriptionIdentifier?.let {
            properties.subscriptionIdentifier.clear()
            properties.subscriptionIdentifier.add(it)
        }

        session.clientConnection?.publish(
            retain,
            topicName,
            Qos.min(subscription.options.qos, qos),
            dup,
            properties,
            payload
        )
    }

    fun setRetained(topicName: String, message: MQTTPublish, clientId: String) {
        if (retainedAvailable) {
            if (message.payload?.isNotEmpty() == true)
                retainedList[topicName] = Pair(message, clientId)
            else
                retainedList.remove(topicName)
        } else {
            throw MQTTException(ReasonCode.RETAIN_NOT_SUPPORTED)
        }
    }

    private fun removeExpiredRetainedMessages() {
        retainedList.removeIf {
            val message = it.value.first
            message.messageExpiryIntervalExpired()
        }
    }

    fun getRetained(topicFilter: String): List<Pair<MQTTPublish, String>> {
        removeExpiredRetainedMessages()
        return retainedList.filter { it.key.matchesWildcard(topicFilter) }.map { it.value }
    }

    private fun Session.isExpired(): Boolean {
        val timestamp = getExpiryTime()
        return timestamp != null && timestamp < currentTimeMillis()
    }

    fun cleanUpOperations() {
        val iterator = sessions.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            if (session.value.isConnected()) { // Check the keep alive timer is being respected
                session.value.clientConnection!!.checkKeepAliveExpired()
            } else {
                if (session.value.isExpired()) {
                    session.value.will?.let {
                        sendWill(session.value)
                    }
                    iterator.remove()
                } else {
                    session.value.will?.let {
                        // Check if the will delay interval has expired, if yes send the will
                        if (session.value.sessionDisconnectedTimestamp!! + (it.willDelayInterval.toLong() * 1000L) > currentTimeMillis())
                            sendWill(session.value)
                    }
                }
            }
        }
    }

    fun stop(serverReference: String? = null, temporarilyMoved: Boolean = false) {
        val reasonCode = if (serverReference != null) {
            if (temporarilyMoved) {
                ReasonCode.USE_ANOTHER_SERVER
            } else {
                ReasonCode.SERVER_MOVED
            }
        } else {
            ReasonCode.SERVER_SHUTTING_DOWN
        }
        sessions.filter { it.value.isConnected() }.forEach {
            it.value.clientConnection?.disconnect(reasonCode, serverReference)
        }
        server.stop()
    }
}
