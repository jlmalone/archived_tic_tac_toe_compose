import org.gradle.api.initialization.dsl.RepositoriesMode

pluginManagement {
    repositories {
        // for the Compose Kotlin plugin
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        kotlin("multiplatform") version "2.1.20"
        id("org.jetbrains.compose") version "1.7.3"
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    }
}

dependencyResolutionManagement {
    // prefer these settings over any repos in build.gradle.kts
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "tic_tac_toe_compose"
