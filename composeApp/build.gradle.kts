import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Read API keys from local.properties (gitignored — never committed)
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())
val anthropicApiKey: String = localProps.getProperty("anthropic.api.key", "")

// Configuration-cache-compatible task that generates ApiKeys.kt in build/
abstract class GenerateApiKeysTask : DefaultTask() {
    @get:Input abstract val anthropicKey: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "ApiKeys.kt").writeText(
            "package com.islandskiesastro.astroplanner\n\n" +
            "internal const val ANTHROPIC_API_KEY_BUILD = \"${anthropicKey.get()}\"\n"
        )
    }
}

val generateApiKeys by tasks.registering(GenerateApiKeysTask::class) {
    anthropicKey.set(anthropicApiKey)
    outputDir.set(layout.buildDirectory.dir("generated/source/apikeys/kotlin"))
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateApiKeys.map { layout.buildDirectory.dir("generated/source/apikeys/kotlin") })
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.services.location)
            implementation(libs.sqldelight.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.astronomy)
            implementation(libs.camera.core)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.0")
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose)
            implementation(libs.markdown.renderer)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest.dependencies {
            implementation(libs.astronomy)
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.islandskiesastro.astroplanner"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.islandskiesastro.astroplanner"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("AstroDatabase") {
            packageName.set("com.islandskiesastro.astroplanner.database")
            version = 7
        }
    }
}
