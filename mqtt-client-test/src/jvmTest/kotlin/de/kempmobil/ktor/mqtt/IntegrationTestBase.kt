package de.kempmobil.ktor.mqtt

import org.junit.AfterClass
import org.junit.BeforeClass

abstract class IntegrationTestBase {

    companion object {

        lateinit var mosquitto: MosquittoContainer

        @JvmStatic
        @BeforeClass
        fun startServer() {
            mosquitto = MosquittoContainer().also { it.start() }
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            println(mosquitto.logs)
            mosquitto.stop()
        }
    }
}