plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.beryx.jlink") version "3.0.1"
    application
}

group = "io.github.colerar"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("team.unnamed:mocha:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.colerar.backportbb.MainKt")
}

jlink {
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "blockbench-backport"
    }
    addExtraDependencies("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    mainClass.set("io.github.colerar.backportbb.MainKt")
    forceMerge("kotlin")
}
