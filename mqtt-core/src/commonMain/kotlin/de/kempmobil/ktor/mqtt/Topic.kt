package de.kempmobil.ktor.mqtt

import kotlin.jvm.JvmInline

/**
 * May represent a topic or a topic filter expression. Hence, valid topics are:
 *
 * - `sport/tennis/player1`
 * - `sport/tennis/player1/#`
 * - `sport/tennis/+` and also
 * - `/`
 */
@JvmInline
public value class Topic(public val name: String) {

    public fun containsWildcard(): Boolean {
        return name.indexOfAny(charArrayOf('#', '+')) != -1
    }

    public fun isNotBlank(): Boolean = name.isNotBlank()

    override fun toString(): String {
        return name
    }
}

/**
 * Converts a list of strings into a list of Topic items.
 */
public inline fun topics(vararg topic: String): List<Topic> = topic.map { Topic(it) }
