import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
}

group = "coffee.adammakes.ksm"

version = rootProject.version

kotlin {
  jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib"))
        implementation(libs.kotlinx.coroutines.core)
        implementation(project(":ksm"))
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
      }
    }
  }
}
