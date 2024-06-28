package de.kempmobil.ktor.mqtt

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

public class MalformedPacketException(message: String? = null) : Exception(message)


/**
 * Throws a [MalformedPacketException] when `condition` is `false`, with the specified message as the exception message.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun wellFormedWhen(condition: Boolean, lazyMessage: () -> Any) {
    contract {
        returns() implies condition
    }
    if (!condition) {
        val message = lazyMessage()
        throw MalformedPacketException(message.toString())
    }
}
