plugins {
    kotlin("jvm") version "2.3.0" // version required here; composite build can't use catalog for plugin versions
    `java-gradle-plugin`
    `maven-publish`
}

group = "coffee.adammakes.ksm"
version = "0.1.0"

gradlePlugin {
    plugins {
        create("ksmIrPlugin") {
            id = "coffee.adammakes.ksm.ir"
            implementationClass = "coffee.adammakes.ksm.ir.KsmGradlePlugin"
        }
    }
}

//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.kotlin.gradle.plugin.api)

    testImplementation("coffee.adammakes.ksm:ksm:0.0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
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
