package de.kempmobil.ktor.mqtt

public data class TopicFilter(
    public val filter: Topic,
    public val subscriptionOptions: SubscriptionOptions = SubscriptionOptions.DEFAULT
)

public fun buildFilters(init: TopicFilterBuilder.() -> Unit): List<TopicFilter> {
    return TopicFilterBuilder().also(init).build()
}

public class TopicFilterBuilder {

    private val filters = mutableListOf<TopicFilter>()

    public fun add(
        topic: String,
        qoS: QoS = QoS.AT_MOST_ONCE,
        isNoLocal: Boolean = false,
        retainAsPublished: Boolean = false,
        retainHandling: RetainHandling = RetainHandling.SEND_ON_SUBSCRIBE
    ) {
        filters.add(TopicFilter(Topic(topic), SubscriptionOptions(qoS, isNoLocal, retainAsPublished, retainHandling)))
    }

    public operator fun String.unaryPlus() {
        filters.add(TopicFilter(Topic(this)))
    }

    public fun build(): List<TopicFilter> = filters.toList()
}