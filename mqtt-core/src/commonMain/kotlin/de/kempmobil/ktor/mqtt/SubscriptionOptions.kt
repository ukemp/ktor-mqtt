package de.kempmobil.ktor.mqtt

public data class SubscriptionOptions(
    public val qoS: QoS = QoS.AT_MOST_ONCE,
    public val isNoLocal: Boolean = false,
    public val retainAsPublished: Boolean = false,
    public val retainHandling: RetainHandling = RetainHandling.SEND_ON_SUBSCRIBE
) {

    public companion object {

        public val DEFAULT: SubscriptionOptions = SubscriptionOptions()
    }
}