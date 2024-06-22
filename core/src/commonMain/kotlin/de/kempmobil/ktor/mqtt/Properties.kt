package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.*
import io.ktor.utils.io.core.*
import kotlinx.io.bytestring.ByteString
import kotlin.jvm.JvmInline

/**
 * Represents the MQTT property as defined in chapter 2.2.2 of the MQTT specification.
 */
public sealed interface Property<T> {

    /**
     * The value of this property
     */
    public val value: T

    /**
     * The number of bytes which are used by this property when encoded in MQTT format.
     */
    public fun byteCount(): Int
}

/**
 * provides a null-safe byte count
 */
internal val <T> Property<T>?.byteCount: Int
    get() = this?.byteCount() ?: 0


public fun <T> BytePacketBuilder.write(property: Property<T>) {
    with(property as WritableProperty) {
        writeByte(identifier.toByte())
        writeValue(value)
    }
}

public fun <T> ByteReadPacket.readAllProperties(): List<Property<T>> {
    return buildList {
        while (canRead()) {
            add(readProperty())
        }
    }
}

@Suppress("UNCHECKED_CAST")
public fun <T> ByteReadPacket.readProperty(): Property<T> {
    return when (val identifier = readByte().toInt()) {
        1 -> PayloadFormatIndicator(readByte()) as Property<T>
        2 -> MessageExpiryInterval(readInt()) as Property<T>
        3 -> ContentType(readMqttString()) as Property<T>
        8 -> ResponseTopic(readMqttString()) as Property<T>
        9 -> CorrelationData(readMqttByteString()) as Property<T>
        11 -> SubscriptionIdentifier(readVariableByteInt()) as Property<T>
        17 -> SessionExpiryInterval(readInt()) as Property<T>
        18 -> AssignedClientIdentifier(readMqttString()) as Property<T>
        19 -> ServerKeepAlive(readShort()) as Property<T>
        21 -> AuthenticationMethod(readMqttString()) as Property<T>
        22 -> AuthenticationData(readMqttByteString()) as Property<T>
        23 -> RequestProblemInformation(readByte()) as Property<T>
        24 -> WillDelayInterval(readInt()) as Property<T>
        25 -> RequestResponseInformation(readByte()) as Property<T>
        26 -> ResponseInformation(readMqttString()) as Property<T>
        28 -> ServerReference(readMqttString()) as Property<T>
        31 -> ReasonString(readMqttString()) as Property<T>
        33 -> ReceiveMaximum(readShort()) as Property<T>
        34 -> TopicAliasMaximum(readShort()) as Property<T>
        35 -> TopicAlias(readShort()) as Property<T>
        36 -> MaximumQoS(readByte()) as Property<T>
        37 -> RetainAvailable(readByte()) as Property<T>
        38 -> UserProperty(readStringPair()) as Property<T>
        39 -> MaximumPacketSize(readInt()) as Property<T>
        40 -> WildcardSubscriptionAvailable(readByte()) as Property<T>
        41 -> SubscriptionIdentifierAvailable(readByte()) as Property<T>
        42 -> SharedSubscriptionAvailable(readByte()) as Property<T>
        else -> throw MalformedPacketException("Unknown property identifier: $identifier")
    }
}

/**
 * Tries to read all bytes of this [ByteReadPacket] and convert them into a list of properties.
 */
public fun ByteReadPacket.readProperties(): List<Property<*>> {
    return buildList {
        while (canRead()) {
            add(readProperty<Property<*>>())
        }
    }
}

@JvmInline
public value class PayloadFormatIndicator(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 1

    public override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class MessageExpiryInterval(override val value: Int) : WritableProperty<Int> {

    public override val identifier: Int
        get() = 2

    override val writeValue: BytePacketBuilder.(Int) -> Unit
        get() = IntWriter

    override fun byteCount(): Int = 5
}

@JvmInline
public value class ContentType(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 3

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class ResponseTopic(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 8

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class CorrelationData(override val value: ByteString) : WritableProperty<ByteString> {

    public override val identifier: Int
        get() = 9

    override val writeValue: BytePacketBuilder.(ByteString) -> Unit
        get() = ByteStringWriter

    override fun byteCount(): Int = value.size + 1
}

@JvmInline
public value class SubscriptionIdentifier(override val value: Int) : WritableProperty<Int> {

    // This is a "variable byte integer" property (the only one)
    public override val identifier: Int
        get() = 11

    override val writeValue: BytePacketBuilder.(Int) -> Unit
        get() = { writeVariableByteInt(value) }

    override fun byteCount(): Int = value.variableByteIntSize() + 1
}

@JvmInline
public value class SessionExpiryInterval(override val value: Int) : WritableProperty<Int> {

    public override val identifier: Int
        get() = 17

    override val writeValue: BytePacketBuilder.(Int) -> Unit
        get() = IntWriter

    override fun byteCount(): Int = 5
}

@JvmInline
public value class AssignedClientIdentifier(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 18

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class ServerKeepAlive(override val value: Short) : WritableProperty<Short> {

    public override val identifier: Int
        get() = 19

    override val writeValue: BytePacketBuilder.(Short) -> Unit
        get() = ShortWriter

    override fun byteCount(): Int = 3
}

@JvmInline
public value class AuthenticationMethod(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 21

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class AuthenticationData(override val value: ByteString) : WritableProperty<ByteString> {

    public override val identifier: Int
        get() = 22

    override val writeValue: BytePacketBuilder.(ByteString) -> Unit
        get() = ByteStringWriter

    override fun byteCount(): Int = value.size + 1
}

@JvmInline
public value class RequestProblemInformation(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 23

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class WillDelayInterval(override val value: Int) : WritableProperty<Int> {

    public override val identifier: Int
        get() = 24

    override val writeValue: BytePacketBuilder.(Int) -> Unit
        get() = IntWriter

    override fun byteCount(): Int = 5
}

@JvmInline
public value class RequestResponseInformation(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 25

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class ResponseInformation(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 26

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class ServerReference(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 28

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class ReasonString(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 31

    override val writeValue: BytePacketBuilder.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3
}

@JvmInline
public value class ReceiveMaximum(override val value: Short) : WritableProperty<Short> {

    public override val identifier: Int
        get() = 33

    override val writeValue: BytePacketBuilder.(Short) -> Unit
        get() = ShortWriter

    override fun byteCount(): Int = 3
}

@JvmInline
public value class TopicAliasMaximum(override val value: Short) : WritableProperty<Short> {

    public override val identifier: Int
        get() = 34

    override val writeValue: BytePacketBuilder.(Short) -> Unit
        get() = ShortWriter

    override fun byteCount(): Int = 3
}

@JvmInline
public value class TopicAlias(override val value: Short) : WritableProperty<Short> {

    public override val identifier: Int
        get() = 35

    override val writeValue: BytePacketBuilder.(Short) -> Unit
        get() = ShortWriter

    override fun byteCount(): Int = 3
}

@JvmInline
public value class MaximumQoS(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 36

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class RetainAvailable(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 37

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class UserProperty(override val value: StringPair) : WritableProperty<StringPair> {

    public override val identifier: Int
        get() = 38

    override val writeValue: BytePacketBuilder.(StringPair) -> Unit
        get() = { write(it) }

    override fun byteCount(): Int {
        return value.name.utf8Size() + value.value.utf8Size() + 5
    }
}

@JvmInline
public value class MaximumPacketSize(override val value: Int) : WritableProperty<Int> {

    public override val identifier: Int
        get() = 39

    override val writeValue: BytePacketBuilder.(Int) -> Unit
        get() = IntWriter

    override fun byteCount(): Int = 5
}

@JvmInline
public value class WildcardSubscriptionAvailable(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 40

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class SubscriptionIdentifierAvailable(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 41

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

@JvmInline
public value class SharedSubscriptionAvailable(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 42

    override val writeValue: BytePacketBuilder.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2
}

private interface WritableProperty<T> : Property<T> {

    val identifier: Int

    val writeValue: BytePacketBuilder.(T) -> Unit
}

private val ByteWriter: BytePacketBuilder.(Byte) -> Unit = {
    writeByte(it)
}

private val ShortWriter: BytePacketBuilder.(Short) -> Unit = {
    writeShort(it)
}

private val IntWriter: BytePacketBuilder.(Int) -> Unit = {
    writeInt(it)
}

private val StringWriter: BytePacketBuilder.(String) -> Unit = {
    writeMqttString(it)
}

private val ByteStringWriter: BytePacketBuilder.(ByteString) -> Unit = {
    writeMqttByteString(it)  // Do NOT(!) use ByteWriteChannel.writeFully(...) as this will not write the size of the byte array
}
