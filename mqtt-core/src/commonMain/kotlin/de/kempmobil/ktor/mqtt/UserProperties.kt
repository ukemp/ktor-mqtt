package de.kempmobil.ktor.mqtt

import de.kempmobil.ktor.mqtt.util.MqttDslMarker
import kotlinx.io.Sink

public data class UserProperties(public val values: List<StringPair>) {

    // Note: not using a map for storing key/value pairs, as the key might appear more than once in a user property!

    /**
     * Returns the first occurrence of the user property with the specified name or `null` if this user property doesn't
     * contain the specified name
     *
     * @see getAll
     */
    public operator fun get(name: String): String? {
        return values.firstOrNull { it.name == name }?.value
    }

    /**
     * Returns all values of the properties with the specified name.
     */
    public fun getAll(name: String): List<String> {
        return values.filter { it.name == name }.map { it.value }
    }

    public fun containsKey(name: String): Boolean {
        return values.find { it.name == name } != null
    }

    public fun containsValue(value: String): Boolean {
        return values.find { it.value == value } != null
    }

    public fun isNotEmpty(): Boolean = values.isNotEmpty()

    public companion object {

        public val EMPTY: UserProperties = UserProperties(values = emptyList())

        internal fun from(properties: List<Property<*>>): UserProperties {
            val list = properties.filterIsInstance<UserProperty>()
            return if (list.isEmpty()) {
                EMPTY
            } else {
                return UserProperties(list.map { it.value })
            }
        }
    }
}

/**
 * DSL for building a [UserProperties] instance.
 *
 * @sample createUserPropertiesDsl
 */
public fun buildUserProperties(init: UserPropertiesBuilder.() -> Unit): UserProperties {
    val builder = UserPropertiesBuilder()
    builder.init()
    return builder.build()
}

/**
 * DSL for creating MQTT user properties. Note that the same name is allowed to appear more than once in user properties.
 *
 * @sample createUserPropertiesDsl
 */
@MqttDslMarker
public class UserPropertiesBuilder() {

    private val userProperties = mutableListOf<StringPair>()

    public infix fun String.to(value: String) {
        userProperties.add(StringPair(this, value))
    }

    public fun build(): UserProperties {
        return if (userProperties.isEmpty()) {
            UserProperties.EMPTY
        } else {
            UserProperties(userProperties)
        }
    }
}

internal val UserProperties.asArray: Array<UserProperty>
    get() = values.map { UserProperty(it) }.toTypedArray()

internal fun Sink.write(userProperties: UserProperties) {
    if (userProperties.values.isNotEmpty()) {
        userProperties.values.forEach { this.write(it) }
    }
}

internal fun createUserPropertiesDsl() {
    buildUserProperties {
        "filename" to "test.txt"
    }
}