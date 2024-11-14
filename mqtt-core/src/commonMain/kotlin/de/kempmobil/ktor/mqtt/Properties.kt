package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.*
import io.ktor.network.sockets.*
import kotlinx.io.*
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
}

/**
 * Returns the property of the specified type, when contained in the list.
 *
 * @throws MalformedPacketException when the property is not contained exactly once in the list
 */
internal inline fun <reified T : Property<*>> List<Property<*>>.single(): T {
    val instances = filterIsInstance<T>()
    return if (instances.size == 1) {
        instances.first()
    } else {
        throw MalformedPacketException("Property of type: ${T::class} is not contained exactly once: ${instances.size}")
    }
}

/**
 * Returns the property of the specified type, when contained in the list or `null` otherwise
 *
 * @throws MalformedPacketException when the property is contained more than once
 */
internal inline fun <reified T : Property<*>> List<Property<*>>.singleOrNull(): T? {
    val instances = filterIsInstance<T>()
    return if (instances.isEmpty()) {
        null
    } else if (instances.size == 1) {
        instances.first()
    } else {
        throw MalformedPacketException("A property which may appear only once, exists ${instances.size} times: $instances")
    }
}

internal fun <T> Sink.write(property: Property<T>) {
    with(property as WritableProperty) {
        writeByte(identifier.toByte())
        writeValue(value)
    }
}

/**
 * Writes all specified properties, which are non-null. Also prepends the byte count as a variable byte integer.
 */
internal fun Sink.writeProperties(vararg properties: Property<*>?) {
    val byteCount = properties.sumOf { (it as? WritableProperty)?.byteCount() ?: 0 }
    writeVariableByteInt(byteCount)

    properties.forEach {
        if (it != null) write(it)
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <T> Source.readProperty(): Property<T> {
    return when (val identifier = (readByte().toInt() and 0xFF)) {
        1 -> PayloadFormatIndicator.from(readByte()) as Property<T>
        2 -> MessageExpiryInterval(readUInt()) as Property<T>
        3 -> ContentType(readMqttString()) as Property<T>
        8 -> ResponseTopic(readMqttString()) as Property<T>
        9 -> CorrelationData(readMqttByteString()) as Property<T>
        11 -> SubscriptionIdentifier(readVariableByteInt()) as Property<T>
        17 -> SessionExpiryInterval(readUInt()) as Property<T>
        18 -> AssignedClientIdentifier(readMqttString()) as Property<T>
        19 -> ServerKeepAlive(readUShort()) as Property<T>
        21 -> AuthenticationMethod(readMqttString()) as Property<T>
        22 -> AuthenticationData(readMqttByteString()) as Property<T>
        23 -> byteToBoolean(readByte()) { RequestProblemInformation(it) } as Property<T>
        24 -> WillDelayInterval(readUInt()) as Property<T>
        25 -> byteToBoolean(readByte()) { RequestResponseInformation(it) } as Property<T>
        26 -> ResponseInformation(readMqttString()) as Property<T>
        28 -> ServerReference(readMqttString()) as Property<T>
        31 -> ReasonString(readMqttString()) as Property<T>
        33 -> ReceiveMaximum(readUShort()) as Property<T>
        34 -> TopicAliasMaximum(readUShort()) as Property<T>
        35 -> TopicAlias(readUShort()) as Property<T>
        36 -> MaximumQoS(readByte()) as Property<T>
        37 -> byteToBoolean(readByte()) { RetainAvailable(it) } as Property<T>
        38 -> UserProperty(readStringPair()) as Property<T>
        39 -> MaximumPacketSize(readUInt()) as Property<T>
        40 -> byteToBoolean(readByte()) { WildcardSubscriptionAvailable(it) } as Property<T>
        41 -> byteToBoolean(readByte()) { SubscriptionIdentifierAvailable(it) } as Property<T>
        42 -> byteToBoolean(readByte()) { SharedSubscriptionAvailable(it) } as Property<T>
        else -> throw MalformedPacketException("Unknown property identifier: $identifier")
    }
}

/**
 * Reads all properties from this packet by first reading the variable int byte count and then the properties.
 */
internal fun Source.readProperties(): List<Property<*>> {
    val byteCount = readVariableByteInt()
    var bytesRead = 0

    return buildList {
        while (bytesRead < byteCount) {
            val property = readProperty<Property<*>>()
            bytesRead += (property as WritableProperty).byteCount()
            add(property)
        }
    }
}


@JvmInline
public value class PayloadFormatIndicator private constructor(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 1

    public override val writeValue: Sink.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }

    public companion object {

        public fun from(byte: Byte): PayloadFormatIndicator {
            return when (byte) {
                0.toByte() -> NONE
                1.toByte() -> UTF_8
                else -> throw MalformedPacketException("Value of $byte not allowed for payload format indicator")
            }
        }

        public val NONE: PayloadFormatIndicator = PayloadFormatIndicator(0)

        public val UTF_8: PayloadFormatIndicator = PayloadFormatIndicator(1)
    }
}

@JvmInline
public value class MessageExpiryInterval(override val value: UInt) : WritableProperty<UInt> {

    public override val identifier: Int
        get() = 2

    override val writeValue: Sink.(UInt) -> Unit
        get() = UIntWriter

    override fun byteCount(): Int = 5

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class ContentType(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 3

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

@JvmInline
public value class ResponseTopic(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 8

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

@JvmInline
public value class CorrelationData(override val value: ByteString) : WritableProperty<ByteString> {

    public override val identifier: Int
        get() = 9

    override val writeValue: Sink.(ByteString) -> Unit
        get() = ByteStringWriter

    override fun byteCount(): Int = value.size + 1

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class SubscriptionIdentifier(override val value: Int) : WritableProperty<Int> {

    init {
        wellFormedWhen(value != 0) { "Subscription identifiers must not be zero" }
    }

    // This is a "variable byte integer" property (the only one)
    public override val identifier: Int
        get() = 11

    override val writeValue: Sink.(Int) -> Unit
        get() = { writeVariableByteInt(value) }

    override fun byteCount(): Int = value.variableByteIntSize() + 1

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class SessionExpiryInterval(override val value: UInt) : WritableProperty<UInt> {

    public override val identifier: Int
        get() = 17

    override val writeValue: Sink.(UInt) -> Unit
        get() = UIntWriter

    override fun byteCount(): Int = 5

    public val doesNotExpire: Boolean
        get() = value == UInt.MAX_VALUE

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class AssignedClientIdentifier(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 18

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

@JvmInline
public value class ServerKeepAlive(override val value: UShort) : WritableProperty<UShort> {

    public override val identifier: Int
        get() = 19

    override val writeValue: Sink.(UShort) -> Unit
        get() = UShortWriter

    override fun byteCount(): Int = 3

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class AuthenticationMethod(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 21

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

@JvmInline
public value class AuthenticationData(override val value: ByteString) : WritableProperty<ByteString> {

    public override val identifier: Int
        get() = 22

    override val writeValue: Sink.(ByteString) -> Unit
        get() = ByteStringWriter

    override fun byteCount(): Int = value.size + 1

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class RequestProblemInformation(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 23

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class WillDelayInterval(override val value: UInt) : WritableProperty<UInt> {

    public override val identifier: Int
        get() = 24

    override val writeValue: Sink.(UInt) -> Unit
        get() = UIntWriter

    override fun byteCount(): Int = 5

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class RequestResponseInformation(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 25

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class ResponseInformation(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 26

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

@JvmInline
public value class ServerReference(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 28

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

/**
 * Tries to parse the list of servers according to the recommendations of the
 * [MQTT 5 specification](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Server_redirection).
 *
 * Note that in case no port number is specified, it will be set to 0 in the returned [SocketAddress]
 */
public val ServerReference.servers: List<SocketAddress>
    get() {
        return if (value.isBlank()) {
            emptyList()
        } else {
            value.trim().split(Regex("\\s+")).mapNotNull { str ->
                try {
                    if (str.startsWith("[")) {
                        val endIndex = str.indexOf(']')
                        val server = str.substring(1..endIndex - 1)
                        val port = str.substring(str.indexOf(':', endIndex) + 1)
                        InetSocketAddress(server.trim(), port.toInt())
                    } else if (str.contains(":")) {
                        InetSocketAddress(str.substringBefore(":"), str.substringAfter(":").toInt())
                    } else {
                        InetSocketAddress(str, 0)
                    }
                } catch (ex: Exception) {
                    Logger.e(throwable = ex) { "Failed to parse server reference: '$str'" }
                    null
                }
            }
        }
    }

@JvmInline
public value class ReasonString(override val value: String) : WritableProperty<String> {

    public override val identifier: Int
        get() = 31

    override val writeValue: Sink.(String) -> Unit
        get() = StringWriter

    override fun byteCount(): Int = value.utf8Size() + 3

    override fun toString(): String {
        return value
    }
}

public fun String?.toReasonString(): ReasonString? = if (this != null) ReasonString(this) else null

/**
 * Converts this into a string or uses the string representation of the specified reason code if this is `null`.
 */
public fun ReasonString?.ifNull(reasonCode: ReasonCode): String {
    return "${reasonCode.code} ${this?.value ?: reasonCode.name}"
}

@JvmInline
public value class ReceiveMaximum(override val value: UShort) : WritableProperty<UShort> {

    public override val identifier: Int
        get() = 33

    override val writeValue: Sink.(UShort) -> Unit
        get() = UShortWriter

    override fun byteCount(): Int = 3

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class TopicAliasMaximum(override val value: UShort) : WritableProperty<UShort> {

    public override val identifier: Int
        get() = 34

    override val writeValue: Sink.(UShort) -> Unit
        get() = UShortWriter

    override fun byteCount(): Int = 3

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class TopicAlias(override val value: UShort) : WritableProperty<UShort> {

    public override val identifier: Int
        get() = 35

    override val writeValue: Sink.(UShort) -> Unit
        get() = UShortWriter

    override fun byteCount(): Int = 3

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class MaximumQoS(override val value: Byte) : WritableProperty<Byte> {

    public override val identifier: Int
        get() = 36

    override val writeValue: Sink.(Byte) -> Unit
        get() = ByteWriter

    override fun byteCount(): Int = 2

    public val qoS: QoS
        get() = QoS.from(value.toInt())

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class RetainAvailable(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 37

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class UserProperty(override val value: StringPair) : WritableProperty<StringPair> {

    public override val identifier: Int
        get() = 38

    override val writeValue: Sink.(StringPair) -> Unit
        get() = { write(it) }

    override fun byteCount(): Int {
        return value.name.utf8Size() + value.value.utf8Size() + 5 // 1 for the identifier 2 + 2 for the string lengths
    }

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class MaximumPacketSize(override val value: UInt) : WritableProperty<UInt> {

    public override val identifier: Int
        get() = 39

    override val writeValue: Sink.(UInt) -> Unit
        get() = UIntWriter

    override fun byteCount(): Int = 5

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class WildcardSubscriptionAvailable(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 40

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

@JvmInline
public value class SubscriptionIdentifierAvailable(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 41

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

/**
 * Returns `true` when this is either `null` or its value is `true`. (From the MQTT specification: "If not present, then
 * Subscription Identifiers are supported")
 */
public fun SubscriptionIdentifierAvailable?.isAvailable(): Boolean = this == null || this.value

@JvmInline
public value class SharedSubscriptionAvailable(override val value: Boolean) : WritableProperty<Boolean> {

    public override val identifier: Int
        get() = 42

    override val writeValue: Sink.(Boolean) -> Unit
        get() = BooleanWriter

    override fun byteCount(): Int = 2

    override fun toString(): String {
        return value.toString()
    }
}

// ---- Helper functions/classes ---------------------------------------------------------------------------------------

private interface WritableProperty<T> : Property<T> {

    val identifier: Int

    val writeValue: Sink.(T) -> Unit

    /**
     * The number of bytes which are used by this property when encoded in MQTT format.
     */
    fun byteCount(): Int
}

private val ByteWriter: Sink.(Byte) -> Unit = {
    writeByte(it)
}

private val ShortWriter: Sink.(Short) -> Unit = {
    writeShort(it)
}

private val UShortWriter: Sink.(UShort) -> Unit = {
    writeUShort(it)
}

private val UIntWriter: Sink.(UInt) -> Unit = {
    writeUInt(it)
}

private val StringWriter: Sink.(String) -> Unit = {
    writeMqttString(it)
}

private val ByteStringWriter: Sink.(ByteString) -> Unit = {
    writeMqttByteString(it)  // Do NOT(!) use ByteWriteChannel.writeFully(...) as this will not write the size of the byte array
}

private val BooleanWriter: Sink.(Boolean) -> Unit = {
    writeByte(if (it) 1 else 0)
}

private fun byteToBoolean(byte: Byte, constructor: (Boolean) -> Property<Boolean>): Property<Boolean> {
    return when (byte) {
        0.toByte() -> constructor(false)
        1.toByte() -> constructor(true)
        else -> throw MalformedPacketException("Value $byte not allowed, only 0 and 1 are allowed for boolean properties")
    }
}
