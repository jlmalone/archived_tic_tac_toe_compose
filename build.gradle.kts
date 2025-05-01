plugins {
    kotlin("multiplatform") version "2.1.20"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "vision.salient"
version = "1.0-SNAPSHOT"

kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(compose.web.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "vision.salient.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "tic_tac_toe_compose"
            packageVersion = "1.0.0"
        }
    }
}
