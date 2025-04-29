import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {


    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
//    id("org.web3j") version "4.14.0"

}

//web3j {
//    generatedPackageName.set("io.tictactoe.contracts")
//    generatedFilesBaseDir.set(file("$buildDir/generated/sources/web3j"))
//    solcVersion.set("0.8.28")
//}

group = "vision.salient"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()

    gradlePluginPortal()
}



dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1") // Or check for the latest version

    implementation("org.web3j:core:5.0.0") // Web3j for Ethereum interaction
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

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
