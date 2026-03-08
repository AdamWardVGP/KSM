rootProject.name = "ksm-ir-plugin"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
