package de.kempmobil.ktor.mqtt

public data class TopicFilter(
    public val filter: String,
    public val subscriptionOptions: SubscriptionOptions = SubscriptionOptions.DEFAULT
)