package de.kempmobil.ktor.mqtt

public enum class QoS(internal val value: Int) {

    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONE(2);
}