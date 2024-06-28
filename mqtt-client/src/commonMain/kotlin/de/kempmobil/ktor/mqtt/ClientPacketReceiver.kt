package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import de.kempmobil.ktor.mqtt.packet.*

internal class ClientPacketReceiver : PacketReceiver {

    override fun onConnect(connect: Connect) {
        Logger.w { "Illegal client packet received: $connect" }
    }

    override fun onConnack(connack: Connack) {
        Logger.v { "New packet received: $connack" }
    }

    override fun onPublish(publish: Publish) {
        Logger.v { "New packet received: $publish" }
    }

    override fun onPuback(puback: Puback) {
        Logger.v { "New packet received: $puback" }
    }

    override fun onPubrec(pubrec: Pubrec) {
        Logger.v { "New packet received: $pubrec" }
    }

    override fun onPubrel(pubrel: Pubrel) {
        Logger.v { "New packet received: $pubrel" }
    }

    override fun onPubcomp(pubcomp: Pubcomp) {
        Logger.v { "New packet received: $pubcomp" }
    }

    override fun onSubscribe(subscribe: Subscribe) {
        Logger.w { "Illegal client packet received: $subscribe" }
    }

    override fun onSuback(suback: Suback) {
        Logger.v { "New packet received: $suback" }
    }

    override fun onUnsubscribe(unsubscribe: Unsubscribe) {
        Logger.w { "Illegal client packet received: $unsubscribe" }
    }

    override fun onUnsuback(unsuback: Unsuback) {
        Logger.v { "New packet received: $unsuback" }
    }

    override fun onPingreq() {
        Logger.w { "Illegal client packet received: PINGREQ" }
    }

    override fun onPingresp() {
        Logger.v { "New packet received: PINGRESP" }
    }

    override fun onDisconnect(disconnect: Disconnect) {
        Logger.v { "New packet received: $disconnect" }
    }

    override fun onAuth(auth: Auth) {
        Logger.v { "New packet received: $auth" }
    }

    override fun onMalformedPacket(exception: MalformedPacketException) {
        Logger.e(throwable = exception) { "Malformed packet received" }
    }
}