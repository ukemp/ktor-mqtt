package de.kempmobil.ktor.mqtt.packet

internal interface PacketReceiver {

    fun onConnect(connect: Connect)

    fun onConnack(connack: Connack)

    fun onPublish(publish: Publish)

    fun onPuback(puback: Puback)

    fun onPubrec(pubrec: Pubrec)

    fun onPubrel(pubrel: Pubrel)

    fun onPubcomp(pubcomp: Pubcomp)

    fun onSubscribe(subscribe: Subscribe)

    fun onSuback(suback: Suback)

    fun onUnsubscribe(unsubscribe: Unsubscribe)

    fun onUnsuback(unsuback: Unsuback)

    fun onPingreq()

    fun onPingresp()

    fun onDisconnect(disconnect: Disconnect)

    fun onAuth(auth: Auth)
}
