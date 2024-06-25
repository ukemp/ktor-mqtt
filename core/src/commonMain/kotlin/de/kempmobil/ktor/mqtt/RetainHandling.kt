package de.kempmobil.ktor.mqtt

/**
 * Retain handling is used in the SUBSCRIBE packet.
 */
public enum class RetainHandling(public val value: Int) {

    SEND_ON_SUBSCRIBE(0),
    SEND_IF_NOT_EXISTS(1),
    DO_NOT_SEND(2);
}