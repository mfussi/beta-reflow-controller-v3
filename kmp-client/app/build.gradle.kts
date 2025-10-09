@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.0.20"
}

val securePropertiesFile = rootProject.file("secure.properties")
val secureProperties = Properties()
secureProperties.load(FileInputStream(securePropertiesFile))


ext {

    this.set("name", "Reflow")
    this.set("exportName", "Reflow")
    this.set("namespace", "com.tangentlines.reflow")

    this.set("version", "1.0.0")
    this.set("versionCode", 1)
    this.set("settingsVersion", "v1")
    this.set("date", SimpleDateFormat("yyyy-MM-dd").format(Calendar.getInstance().time) )

    this.set("changelog", """
        V1.0.0:
            - initial release
    """.trimIndent())

}

val exportName = (project.ext.get("exportName") as String)
val version = project.ext.get("version")
val outputFolder = layout.projectDirectory.dir("../outputs/v${version}").asFile.absolutePath!!

val distributeLinux by tasks.register("distributeLinux", Copy::class) {

    this.group = "distribute"
    this.dependsOn("packageDeb")

    from(layout.buildDirectory.dir("compose/binaries/main/deb")) {
        include("*.deb")
        rename { it.replace("${exportName}-${version}", "${exportName}-v${version}") }
    }

    into("${outputFolder}/linux/")

}

val distributeWindows by tasks.register("distributeWindows", Copy::class) {

    this.group = "distribute"
    this.dependsOn("packageExe", "packageMsi")

    from(layout.buildDirectory.dir("compose/binaries/main/msi")) {
        include("*.msi")
        rename { it.replace("${exportName}-${version}", "${exportName}-v${version}") }
    }

    from(layout.buildDirectory.dir("compose/binaries/main/exe")) {
        include("*.exe")
        rename { it.replace("${exportName}-${version}", "${exportName}-v${version}") }
    }

    into("${outputFolder}/windows/")

}

val distributeMacDmg by tasks.register("distributeMacDmg", Copy::class) {

    this.group = "distribute"
    this.dependsOn("packageDmg")

    from(layout.buildDirectory.dir("compose/binaries/main/dmg")) {
        include("*.dmg")
        rename { it.replace("${exportName}-${version}", "${exportName}-v${version}") }
    }

    into("${outputFolder}/mac/")

}

val distributeMacApp by tasks.register("distributeMacApp", Copy::class) {

    this.group = "distribute"
    this.dependsOn("packageDmg")

    from(layout.buildDirectory.dir("compose/binaries/main/app/${exportName}.app")) {
        include("**")
    }

    into("${outputFolder}/mac/${exportName}-v${version}.app")

}

val distributeMac by tasks.register("distributeMac") {

    this.group = "distribute"
    this.dependsOn(distributeMacApp, distributeMacDmg)

}

val distributeAndroid by tasks.register("distributeAndroid", Copy::class) {

    this.group = "distribute"
    this.dependsOn("distributeRelease")

    from(layout.projectDirectory.dir("../outputs")) { include("*.apk", "*.meta", "*.aab") }
    into("${outputFolder}/android/")

}

val buildConfigGenerator by tasks.registering(Sync::class) {

    from(
        resources.text.fromString(
            """
        |package com.tangentlines.reflow
        |
        |object BuildConfig {
        |
        |  const val NAME = "${(project.ext.get("name") as String)}"
        |  const val VERSION = "${project.ext.get("version")}"
        |  const val VERSION_DATE = "${project.ext.get("date")}"
        |  const val SETTINGS_VERSION = "${project.ext.get("settingsVersion")}"
        |  const val CHANGELOG = "${(project.ext.get("changelog") as String).replace("\n", "\\n")}"
        |  const val SECRET_API_KEY = "${(secureProperties["SECRET_API_KEY"] as String)}"
        |  
        |}
        |
      """.trimMargin()
        )
    ) {
        rename { "BuildConfig.kt" }
        into("com/tangentlines/reflow")
    }

    // the target directory
    into(layout.buildDirectory.dir("generated-src-build/kotlin"))
}

kotlin {

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->

        iosTarget.binaries.framework {
            baseName = "ReflowShared"
            isStatic = true

            binaryOption("bundleId", project.ext.get("namespace") as String)
            binaryOption("CFBundleShortVersionString", project.ext.get("version") as String)
            binaryOption("CFBundleVersion", (project.ext.get("versionCode") as Int).toString())

            binaryOption("bundleShortVersionString", project.ext.get("version") as String)
            binaryOption("bundleVersion", (project.ext.get("versionCode") as Int).toString())

        }

    }

    wasmJs {

        outputModuleName.set("app")
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "app.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {

            kotlin.srcDir(buildConfigGenerator.map { it.destinationDir })

            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.jetbrains.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.koalaplot.core)
                implementation(libs.ui.backhandler)
            }
        }
        val androidMain by getting {
            dependencies {

                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation("androidx.compose.material3:material3:1.4.0")

                implementation(libs.ui.tooling)
                implementation(libs.ui.tooling.preview)
                implementation(libs.ui.tooling.preview.android)
            }

        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(compose.desktop.currentOs)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.ktor.client.js)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies { implementation(libs.ktor.client.darwin) }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

android {

    val keystorePropertiesFile = rootProject.file("signing.properties")
    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

    namespace = (project.ext.get("namespace") as String)
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = (project.ext.get("namespace") as String)
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = project.ext.get("versionCode") as Int
        versionName = "${project.ext.get("version")}"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {

        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"]  as String?
            storeFile = (keystoreProperties["storeFile"] as String?)?.let { layout.projectDirectory.dir(it).asFile }
            storePassword = keystoreProperties["storePassword"]  as String?
        }

    }

    buildTypes {

        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

}

compose.desktop {
    application {

        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }

        mainClass = "com.tangentlines.reflowclient.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = exportName
            packageVersion = "${project.ext.get("version")}"
            includeAllModules = true
            vendor = "TangentLines"
            description = "Reflow Controller"

            macOS {

                iconFile.set(project.file("AppIcon.icns"))
                dockName = (project.ext.get("name") as String)
                infoPlist {}

            }

            windows {

                menu = true
                menuGroup = (project.ext.get("name") as String)
                shortcut = true
                perUserInstall = true
                iconFile.set(project.file("app_icon.png"))

            }

            linux {

                iconFile.set(project.file("app_icon.png"))
                shortcut = true
                packageName = exportName
                menuGroup = (project.ext.get("name") as String)
                debMaintainer = "TangentLines"

            }

        }

    }
}