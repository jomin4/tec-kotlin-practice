plugins {
    kotlin("jvm") version "2.4.20"
    application
}

group = "com.travel"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.travel.MainKt")
    applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}
