package de.kempmobil.ktor.mqtt

public enum class QoS(public val value: Int) {

    /**
     * QoS 0: At most once delivery
     */
    AT_MOST_ONCE(0),

    /**
     * QoS 1: At least once delivery
     */
    AT_LEAST_ONCE(1),

    /**
     * QoS 2: Exactly once delivery
     */
    EXACTLY_ONE(2);

    /**
     * Ensures that this QoS is not greater than the specified [maximumQoS].
     */
    public fun coerceAtMost(maximumQoS: QoS): QoS {
        return if (this.value > maximumQoS.value) maximumQoS else this
    }

    internal companion object {

        fun from(value: Int): QoS {
            return when (value) {
                0 -> AT_MOST_ONCE
                1 -> AT_LEAST_ONCE
                2 -> EXACTLY_ONE
                else -> throw MalformedPacketException("Unknown QoS value: $value")
            }
        }
    }
}