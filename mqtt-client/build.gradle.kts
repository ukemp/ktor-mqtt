plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mockery)
    alias(libs.plugins.kover)
    `maven-publish`
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
        commonMain {
            dependencies {
                implementation(project(":mqtt-core"))
                implementation(libs.kermit)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.network)
                implementation(libs.ktor.network.tls)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.coroutines.core)
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

group = "de.kempmobil.ktor.mqtt"
version = libs.versions.ktormqtt.get()

publishing {
    val repoDirectory: String by rootProject.extra
    repositories {
        maven {
            name = "ktor-mqtt"
            url = uri(repoDirectory)
        }
    }
}
