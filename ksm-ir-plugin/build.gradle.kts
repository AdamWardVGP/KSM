plugins {
    kotlin("jvm")
}
//https://github.com/JetBrains/kotlin/blob/master/docs/fir/fir-basics.md
dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    compileOnly(kotlin("compiler"))
}

kotlin {
    jvmToolchain(17)
}
