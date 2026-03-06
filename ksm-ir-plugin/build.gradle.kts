plugins {
    kotlin("jvm") version "2.3.0"
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
    compileOnly(kotlin("compiler-embeddable"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.3.0")

    testImplementation("coffee.adammakes.ksm:ksm:0.0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("dev.zacsweers.kctfork:core:0.12.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}
