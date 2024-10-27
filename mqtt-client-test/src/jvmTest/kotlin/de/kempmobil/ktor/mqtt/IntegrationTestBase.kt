package de.kempmobil.ktor.mqtt

import co.touchlab.kermit.Logger
import org.junit.AfterClass
import org.junit.BeforeClass

public abstract class IntegrationTestBase {

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
            Logger.i(mosquitto.logs)
            mosquitto.stop()
        }
    }
}