package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker

public data class TopicFilter(
    public val filter: Topic,
    public val subscriptionOptions: SubscriptionOptions = SubscriptionOptions.DEFAULT
) {
    init {
        require(filter.isNotBlank()) { "Empty topics are not allowed in topic filters" }
    }
}

public fun List<TopicFilter>.hasSharedTopic(): Boolean = any { it.filter.isShared() }

public fun List<TopicFilter>.hasWildcard(): Boolean = any { it.filter.containsWildcard() }

public fun buildFilterList(init: TopicFilterBuilder.() -> Unit): List<TopicFilter> {
    return TopicFilterBuilder().also(init).build()
}

@MqttDslMarker
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