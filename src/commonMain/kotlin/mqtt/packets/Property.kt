package mqtt.packets

enum class Property(val value: UInt) {
    PAYLOAD_FORMAT_INDICATOR(1u),
    MESSAGE_EXPIRY_INTERVAL(2u),
    CONTENT_TYPE(3u),
    RESPONSE_TOPIC(8u),
    CORRELATION_DATA(9u),
    SUBSCRIPTION_IDENTIFIER(11u),
    SESSION_EXPIRY_INTERVAL(17u),
    ASSIGNED_CLIENT_IDENTIFIER(18u),
    SERVER_KEEP_ALIVE(19u),
    AUTHENTICATION_METHOD(21u),
    AUTHENTICATION_DATA(22u),
    REQUEST_PROBLEM_INFORMATION(23u),
    WILL_DELAY_INTERVAL(24u),
    REQUEST_RESPONSE_INFORMATION(25u),
    RESPONSE_INFORMATION(26u),
    SERVER_REFERENCE(28u),
    REASON_STRING(31u),
    RECEIVE_MAXIMUM(33u),
    TOPIC_ALIAS_MAXIMUM(34u),
    TOPIC_ALIAS(35u),
    MAXIMUM_QOS(36u),
    RETAIN_AVAILABLE(37u),
    USER_PROPERTY(38u),
    MAXIMUM_PACKET_SIZE(39u),
    WILDCARD_SUBSCRIPTION_AVAILABLE(40u),
    SUBSCRIPTION_IDENTIFIER_AVAILABLE(41u),
    SHARED_SUBSCRIPTION_AVAILABLE(42u);

    companion object {
        fun valueOf(value: UInt) = values().firstOrNull { it.value == value }
    }
}
