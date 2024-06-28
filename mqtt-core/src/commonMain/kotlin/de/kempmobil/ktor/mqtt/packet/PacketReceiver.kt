package de.kempmobil.ktor.mqtt.packet

public interface PacketReceiver {

    public fun onConnect(connect: Connect)

    public fun onConnack(connack: Connack)

    public fun onPublish(publish: Publish)

    public fun onPuback(puback: Puback)

    public fun onPubrec(pubrec: Pubrec)

    public fun onPubrel(pubrel: Pubrel)

    public fun onPubcomp(pubcomp: Pubcomp)

    public fun onSubscribe(subscribe: Subscribe)

    public fun onSuback(suback: Suback)

    public fun onUnsubscribe(unsubscribe: Unsubscribe)

    public fun onUnsuback(unsuback: Unsuback)

    public fun onPingreq()

    public fun onPingresp()

    public fun onDisconnect(disconnect: Disconnect)

    public fun onAuth(auth: Auth)
}
