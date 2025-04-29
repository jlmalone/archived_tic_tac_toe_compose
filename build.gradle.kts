// build.gradle.kts (Kotlin Project)
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
// No need for SourceSetContainer import anymore

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0" // Add serialization plugin

    // NO org.web3j plugin
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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Or latest version
    implementation(compose.desktop.currentOs)
    // Dotenv for loading .env files
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    // Web3j core library ONLY
    implementation("org.web3j:core:5.0.0")
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // Optional: Logging implementation
    // implementation("ch.qos.logback:logback-classic:1.3.11")
}

// REMOVE or comment out the sourceSets block if you added it before
/*
sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
    }
}
*/

compose.desktop {
    application {
        mainClass = "vision.salient.MainKt" // Matches package
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tic_tac_toe_compose"
            packageVersion = "1.0.0"
        }
    }
}