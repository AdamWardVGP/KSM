plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

group = "com.adamwardvgp"
version = findProperty("VERSION_NAME") ?: "0.0.1-SNAPSHOT"

subprojects {

    repositories {
        mavenCentral()
        google()
    }
}
