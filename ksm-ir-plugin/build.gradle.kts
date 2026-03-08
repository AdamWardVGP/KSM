import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.0" // version required here; composite build can't use catalog for plugin versions
    `java-gradle-plugin`
    `maven-publish`
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "coffee.adammakes.ksm"
version = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME", "0.0.1-SNAPSHOT")

gradlePlugin {
    plugins {
        create("ksmIrPlugin") {
            id = "coffee.adammakes.ksm.ir"
            implementationClass = "coffee.adammakes.ksm.ir.KsmGradlePlugin"
        }
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    if (System.getenv("SIGNING_KEY") != null) {
        signAllPublications()
    }
    coordinates("coffee.adammakes.ksm", "ksm-ir-plugin")
    pom {
        name = "KSM IR Plugin"
        description = "Kotlin IR compiler plugin for KSM — generates Mermaid state diagrams at compile time"
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

//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.gradle.plugin.api)

    val ksmVersion = Properties().apply {
        file("../gradle.properties").inputStream().use { load(it) }
    }.getProperty("VERSION_NAME", "0.0.1-SNAPSHOT")
    testImplementation("coffee.adammakes.ksm:ksm:$ksmVersion")
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

// JDK 21 toolchain is used for compilation, but we target JVM 17 bytecode so the
// plugin JAR is loadable by a JVM 17 Gradle daemon. Must be set on both Kotlin and Java tasks.
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3) // kept in sync with libs.versions.toml kotlin = "2.3.0"
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}
