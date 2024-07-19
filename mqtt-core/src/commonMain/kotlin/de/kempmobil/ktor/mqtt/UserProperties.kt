package de.kempmobil.ktor.mqtt

import io.ktor.utils.io.core.*

public data class UserProperties(public val values: List<UserProperty>) {

    public companion object {

        public val EMPTY: UserProperties = UserProperties(emptyList())

        public fun from(properties: List<Property<*>>): UserProperties {
            return UserProperties(properties.filterIsInstance<UserProperty>())
        }
    }
}

/**
 * DSL for building a [UserProperties] instance. Example:
 * ```
 * buildUserProperties {
 *     "key-1" to "value-1"
 *     "key-2" to "value-2"
 * }
 * ```
 */
public fun buildUserProperties(init: UserPropertiesBuilder.() -> Unit): UserProperties {
    val builder = UserPropertiesBuilder()
    builder.init()
    return builder.build()
}

public class UserPropertiesBuilder() {

    private val userProperties = mutableListOf<UserProperty>()

    public infix fun String.to(value: String) {
        userProperties.add(UserProperty(StringPair(this, value)))
    }

    public fun build(): UserProperties {
        return if (userProperties.isNotEmpty()) {
            UserProperties(userProperties)
        } else {
            UserProperties.EMPTY
        }
    }
}

internal val UserProperties.asArray: Array<UserProperty>
    get() = values.toTypedArray()

internal fun BytePacketBuilder.write(userProperties: UserProperties) {
    if (userProperties.values.isNotEmpty()) {
        userProperties.values.forEach { this.write(it) }
    }
}