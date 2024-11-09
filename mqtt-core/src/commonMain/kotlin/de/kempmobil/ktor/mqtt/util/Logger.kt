package de.kempmobil.ktor.mqtt.util

import co.touchlab.kermit.*
import co.touchlab.kermit.Logger

public object Logger {

    private val log: Logger = Logger(mutableLoggerConfigInit(platformLogWriter()), "mqtt")

    init {
        log.mutableConfig.minSeverity = Severity.Assert // Highest severity and not used, hence no logging by default
    }

    public fun configureLogging(init: MutableLoggerConfig.() -> Unit) {
        log.mutableConfig.init()
    }

    public fun v(throwable: Throwable? = null, message: () -> String) {
        log.v(throwable = throwable, tag = "mqtt", message = message)
    }

    public fun d(throwable: Throwable? = null, message: () -> String) {
        log.d(throwable = throwable, tag = "mqtt", message = message)
    }

    public fun i(throwable: Throwable? = null, message: () -> String) {
        log.i(throwable = throwable, tag = "mqtt", message = message)
    }

    public fun w(throwable: Throwable? = null, message: () -> String) {
        log.w(throwable = throwable, tag = "mqtt", message = message)
    }

    public fun e(throwable: Throwable? = null, message: () -> String) {
        log.e(throwable = throwable, tag = "mqtt", message = message)
    }
}