package de.kempmobil.ktor.mqtt

import kotlin.jvm.JvmInline

/**
 * May represent a topic or a topic filter expression. Hence, valid topics are:
 *
 * - `sport/tennis/player1`
 * - `sport/tennis/player1/#`
 * - `sport/tennis/+` and also
 * - `/`
 *
 * But not the empty string.
 */
@JvmInline
public value class Topic(public val name: String) {

    init {
        wellFormedWhen(name.isNotEmpty()) { "Empty topic" }
    }

    override fun toString(): String {
        return name
    }
}