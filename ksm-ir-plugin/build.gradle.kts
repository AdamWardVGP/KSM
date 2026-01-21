plugins {
    kotlin("jvm")
}

group = "dev.adamwardvgp.ksm"
version = "0.1.0"


//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    compileOnly(kotlin("compiler"))
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}