import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "de.kempmobil.ktor.mqtt.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
        compilerOptions {
            jvmTarget.set(
                JvmTarget.JVM_11
            )
        }
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
//        buildTypes {
//            getByName("release") {
//                isMinifyEnabled = false
//            }
//        }
//        compileOptions {
//            sourceCompatibility = JavaVersion.VERSION_11
//            targetCompatibility = JavaVersion.VERSION_11
//        }
    }
    dependencies {
        implementation(projects.mqttDemoShared)
        implementation(libs.androidx.activity.compose)
        implementation(libs.compose.uiToolingPreview)
    }
}