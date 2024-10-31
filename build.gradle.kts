plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.org.jetbrains.kotlin.jvm).apply(false)
    alias(libs.plugins.versions)
    alias(libs.plugins.kover)
}


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
            }
        }
    }
}
