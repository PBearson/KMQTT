package mqtt.broker.interfaces

interface Authorization {

    fun authorize(clientId: String, topicName: String, isSubscription: Boolean): Boolean
}
