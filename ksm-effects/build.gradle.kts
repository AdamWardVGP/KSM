import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "coffee.adammakes.ksm"

version = rootProject.version

repositories { mavenCentral() }

kotlin {
  jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib"))
        implementation(libs.kotlinx.coroutines.core)
        api(project(":ksm"))
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

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) signAllPublications()
  coordinates(group.toString(), "ksm-effects")
  pom {
    name = "KSM Effects"
    description =
      "Side effect DSL for KSM — dispatches state-entry work back into the machine as events"
    inceptionYear = "2026"
    url = "https://github.com/AdamWardVGP/KSM"
    licenses {
      license {
        name = "Mozilla Public License 2.0"
        url = "https://www.mozilla.org/en-US/MPL/2.0/"
      }
    }
    developers {
      developer {
        id = "AdamWardVGP"
        name = "Adam Ward"
      }
    }
    scm { url = "https://github.com/AdamWardVGP/KSM" }
  }
}
