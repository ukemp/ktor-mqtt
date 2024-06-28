package de.kempmobil.ktor.mqtt

public data class TopicFilter(
    public val filter: Topic,
    public val subscriptionOptions: SubscriptionOptions = SubscriptionOptions.DEFAULT
)