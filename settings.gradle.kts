rootProject.name = "AstroPlanner"
include(":composeApp")

pluginManagement {
    plugins {
        id("app.cash.sqldelight") version "2.0.2"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
