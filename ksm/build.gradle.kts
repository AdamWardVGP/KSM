import com.vanniktech.maven.publish.SonatypeHost

plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "coffee.adammakes.ksm"

version = rootProject.version

repositories { mavenCentral() }

kotlin {
  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(kotlin("stdlib"))
        implementation(libs.kotlinx.coroutines.core)
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
  if (System.getenv("SIGNING_KEY") != null) signAllPublications()
  coordinates(group.toString(), "ksm")
  pom {
    name = "KSM"
    description = "A finite state machine for Kotlin Multiplatform"
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
