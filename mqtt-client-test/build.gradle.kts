plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mockery)
}

kotlin {
    explicitApi()
    jvm()
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "base"
            isStatic = true
        }
    }

    sourceSets {
        jvmTest {
            dependencies {
                implementation(project(":mqtt-core"))
                implementation(project(":mqtt-client"))
                implementation(project(":mqtt-client-ws"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.network)
                implementation(libs.ktor.network.tls)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.io)
                implementation(libs.kermit)
                implementation(libs.kotlin.test)
                implementation(libs.junit.api)
                implementation(libs.junit.engine)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.slf4j.api)
                implementation(libs.slf4j.simple)
            }
        }
    }
}

android {
    namespace = "de.kempmobil.ktor.mqtt"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}
