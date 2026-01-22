plugins {
    kotlin("jvm")
}

group = "dev.adamwardvgp.ksm"
version = "0.1.0"


//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(project(":ksm"))
    testImplementation(libs.kotlin.test)
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}