@file:OptIn(ExperimentalWasmDsl::class)

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()
    jvm()
    android {
        namespace = "de.kempmobil.ktor.mqtt"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(
                JvmTarget.JVM_1_8
            )
        }
        withHostTest { }  // Enables unit tests for Android
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        macosX64(),
        macosArm64(),
        tvosX64(),
        tvosArm64(),
        tvosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "base"
            isStatic = true
        }
    }
    wasmJs {
        browser() // Target the browser environment
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kermit)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.network)
                implementation(libs.ktor.network.tls)
                implementation(libs.kotlinx.io)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dokka {
    moduleName.set("Ktor-MQTT Core v${libs.versions.ktormqtt.get()}")
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(projectDir.resolve("src"))
            remoteUrl.set(URI("https://github.com/ukemp/ktor-mqtt/tree/main/mqtt-core/src"))
            remoteLineSuffix.set("#L")
        }
    }
}

// Do not delete nor move to the root script, otherwise iOS artifact will miss these values
group = "de.kempmobil.ktor.mqtt"
version = libs.versions.ktormqtt.get()

mavenPublishing {
    // It's not sufficient to call coordinates() here, group and version must also be defined as above
    coordinates(group.toString(), "mqtt-core", version.toString())
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGenerate"),
            sourcesJar = SourcesJar.Sources(),
            androidVariantsToPublish = listOf("debug", "release"),
        )
    )
}