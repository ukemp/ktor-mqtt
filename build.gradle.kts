import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.org.jetbrains.kotlin.jvm).apply(false)
    alias(libs.plugins.versions)
    alias(libs.plugins.kover)
    alias(libs.plugins.vanniktech)
    alias(libs.plugins.dokka)
}

// Local maven repository to publish artifacts to
val repoDirectory by extra { "${project.rootDir}/repo" }

dependencies {
    kover(project(":mqtt-core"))
    kover(project(":mqtt-client"))
    kover(project(":mqtt-client-ws"))
    kover(project(":mqtt-client-test"))
}

// Run for example with ./gradlew koverHtmlReport
kover {
    reports {
        filters {
            excludes {
                classes(
                    "de.kempmobil.ktor.mqtt.ClientSample",
                    "de.kempmobil.ktor.mqtt.ws.WsSample"
                )
            }
        }
    }
}

allprojects {
    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()
            pom {
                name = "Ktor MQTT"
                description = "A modern multiplatform Kotlin MQTT 5 client library."
                inceptionYear = "2024"
                url = "https://github.com/ukemp/ktor-mqtt/"
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("ukemp")
                        name.set("Ulrich Kemp")
                        url.set("https://github.com/ukemp/")
                    }
                }
                scm {
                    url.set("https://github.com/ukemp/ktor-mqtt/")
                    connection.set("scm:git:git://github.com/ukemp/ktor-mqtt.git")
                    developerConnection.set("scm:git:ssh://git@github.com/ukemp/ktor-mqtt.git")
                }
            }
        }
    }
}