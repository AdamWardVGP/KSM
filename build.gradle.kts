plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.composeHotReload) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.detekt)
  alias(libs.plugins.spotless)
}

group = "coffee.adammakes.ksm"

version = findProperty("VERSION_NAME") ?: "0.0.1-SNAPSHOT"

subprojects {
  repositories {
    mavenLocal()
    mavenCentral()
    google()
  }
}

detekt {
  config.setFrom(files("$rootDir/detekt.yml"))
  buildUponDefaultConfig = true
  source.setFrom(
    files(
      "ksm/src/commonMain/kotlin",
      "ksm/src/commonTest/kotlin",
      "ksm-effects/src/commonMain/kotlin",
      "ksm-effects/src/commonTest/kotlin",
    )
  )
}

spotless {
  kotlin {
    target("ksm/src/**/*.kt", "ksm-effects/src/**/*.kt", "sample/src/**/*.kt")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
  }
  kotlinGradle {
    target("*.gradle.kts", "ksm/*.gradle.kts", "ksm-effects/*.gradle.kts", "sample/*.gradle.kts")
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
  }
}
