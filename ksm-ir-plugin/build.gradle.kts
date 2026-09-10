import com.vanniktech.maven.publish.SonatypeHost
import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.0" // version required here; composite build can't use catalog for plugin versions
    `java-gradle-plugin`
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) signAllPublications()
    coordinates(group.toString(), "ksm-ir-plugin", version.toString())
    pom {
        name = "KSM IR Plugin"
        description = "Kotlin IR compiler plugin for KSM — generates Mermaid and Glyphic state diagrams at compile time"
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

//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.gradle.plugin)
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

tasks.named<ProcessResources>("processResources") {
    filesMatching("version.properties") {
        filter(
            org.apache.tools.ant.filters.ReplaceTokens::class,
            "tokens" to mapOf("VERSION" to version.toString()),
        )
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}

// JDK 21 toolchain is used for compilation, but we target JVM 11 bytecode so the
// plugin JAR is loadable by any JVM 11+ Gradle daemon (Gradle 8 minimum is JDK 11).
// Must be set on both Kotlin and Java tasks.
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}
