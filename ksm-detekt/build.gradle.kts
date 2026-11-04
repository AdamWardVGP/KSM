import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.kotlinJvm)
  id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "coffee.adammakes.ksm"

version = rootProject.version

kotlin {
  jvmToolchain(17)
  compilerOptions {
    languageVersion.set(KotlinVersion.KOTLIN_2_2)
    apiVersion.set(KotlinVersion.KOTLIN_2_2)
  }
}

dependencies {
  compileOnly(kotlin("stdlib"))
  compileOnly(libs.detekt.api)
  testImplementation(libs.detekt.test)
  testImplementation(libs.junit)
}

tasks.test { maxHeapSize = "1g" }

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) signAllPublications()
  coordinates(group.toString(), "ksm-detekt")
  pom {
    name = "KSM Detekt"
    description = "Opt-in architecture rules for KSM, compatible with Detekt 2.0.0-alpha.6"
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
        email = "AdamWardVGP@gmail.com"
        organization = "KSM"
        organizationUrl = "https://github.com/AdamWardVGP/KSM"
      }
    }
    scm {
      url = "https://github.com/AdamWardVGP/KSM"
      connection = "scm:git:https://github.com/AdamWardVGP/KSM.git"
      developerConnection = "scm:git:ssh://git@github.com/AdamWardVGP/KSM.git"
    }
  }
}
