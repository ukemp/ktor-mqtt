package de.kempmobil.ktor.mqtt.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform