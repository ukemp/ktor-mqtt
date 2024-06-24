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

public fun buildUserProperties(init: UserPropertiesBuilder.() -> Unit): UserProperties {
    val builder = UserPropertiesBuilder()
    builder.init()
    return builder.build()
}

public class UserPropertiesBuilder() {

    private val userProperties = mutableListOf<UserProperty>()

    public operator fun StringPair.unaryPlus() {
        userProperties.add(UserProperty(this))
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