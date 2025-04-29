import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0" // Keep serialization for JSON parsing
}

group = "vision.salient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
    gradlePluginPortal()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    implementation("org.web3j:core:5.0.0") // Only need core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // For JSON parsing
    // Optional: Logging implementation
    // implementation("ch.qos.logback:logback-classic:1.3.11")
}

// NO sourceSets { main { java { ... } } } block needed

compose.desktop {
    application {
        mainClass = "vision.salient.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tic_tac_toe_compose"
            packageVersion = "1.0.0"
        }
    }
}