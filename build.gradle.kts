// === FILE: build.gradle.kts ===
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.gradle.api.tasks.Copy

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") // Keep plugin, harmless
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "vision.salient"
version = "1.0-SNAPSHOT"

val composeVersion = project.property("compose.version") as String

kotlin {
    jvmToolchain(11)

    jvm {
        withJava() // Keep this in case you still need it later
    }

    @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl::class)
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
            @OptIn(org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalDistributionDsl::class)
            distribution {
                outputDirectory = file("$buildDir/dist/js/productionExecutable")
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Keep core Compose KMP parts
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                implementation("org.jetbrains.compose.material:material:$composeVersion")
                // Keep Kotlinx libs - they are KMP compatible and harmless
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                // Keep Desktop runtime dependency - it will be added implicitly by compose.desktop block
                // implementation("org.jetbrains.compose.desktop:desktop-jvm-current-os:$composeVersion") // Implicitly added

                // --- COMMENT OUT APP-SPECIFIC JVM DEPS ---
                // implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
                // implementation("org.web3j:core:5.0.0")
            }
            // --- COMMENT OUT JAVA SOURCE DIR IF NOT NEEDED FOR HELLO WORLD ---
            // kotlin.srcDirs("src/main/java")
        }

        val jsMain by getting {
            dependencies {
                // Keep Compose HTML dependency
                implementation("org.jetbrains.compose.html:html-core:$composeVersion")
            }
        }

        // --- Test Source Sets (can be left as is or simplified too) ---
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        val jvmTest by getting { dependencies { implementation(kotlin("test-junit")) } }
        val jsTest by getting { dependencies { implementation(kotlin("test-js")) } }
    }
}

// Keep compose.desktop block - it configures the JVM runner
compose.desktop {
    application {
        mainClass = "vision.salient.MainKt" // Points to our simplified Main.kt
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tic_tac_toe_compose"
            packageVersion = "1.0.0"
        }
    }
}

// Keep task configurations
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { /* ... */ }
}
tasks.named<Copy>("jsProcessResources") {
    destinationDir = file("$buildDir/processedResources/js/main")
}
tasks.named<Copy>("jsBrowserDistribution") {
    destinationDir = file("$buildDir/dist/js/productionExecutable")
}