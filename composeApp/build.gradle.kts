import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation("com.github.jnr:jnr-ffi:2.2.19")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.datetime)
            implementation("com.github.jnr:jnr-ffi:2.2.19")
        }
    }
}

android {
    namespace = "com.bghorizon.proxytoolboxgui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bghorizon.proxytoolboxgui"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // jnr-ffi relies on standard jniLibs behavior
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.bghorizon.proxytoolboxgui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.bghorizon.proxytoolboxgui"
            packageVersion = "1.0.0"
        }
    }
}

val osName = System.getProperty("os.name").lowercase()
val isWindows = osName.contains("win")
val desktopTarget = if (isWindows) "windows_x64" else "linux_x64"

val runBuildGoWrapperDesktop = tasks.register<Exec>("runBuildGoWrapperDesktop") {
    group = "build"
    workingDir = file("../gowrapper")
    commandLine("make", desktopTarget)
}

tasks.register<Copy>("buildGoWrapperDesktop") {
    group = "build"
    dependsOn(runBuildGoWrapperDesktop)
    val srcDir = if (isWindows) "../gowrapper/bin/windows/x64" else "../gowrapper/bin/linux/x64"
    from(srcDir)
    into("src/jvmMain/resources")
}

val runBuildWorkersDesktop = tasks.register<Exec>("runBuildWorkersDesktop") {
    group = "build"
    workingDir = file("../workers")
    commandLine("make", desktopTarget)
}

tasks.register<Copy>("buildWorkersDesktop") {
    group = "build"
    dependsOn(runBuildWorkersDesktop)
    val srcDir = if (isWindows) "../workers/bin/windows/x64" else "../workers/bin/linux/x64"
    from(srcDir)
    into("src/jvmMain/resources")
}

val runBuildGoWrapperAndroid = tasks.register<Exec>("runBuildGoWrapperAndroid") {
    group = "build"
    workingDir = file("../gowrapper")
    commandLine("make", "android")
}

tasks.register<Copy>("buildGoWrapperAndroid") {
    group = "build"
    dependsOn(runBuildGoWrapperAndroid)
    from("../gowrapper/bin/android")
    into("src/androidMain/jniLibs")
}

val runBuildWorkersAndroid = tasks.register<Exec>("runBuildWorkersAndroid") {
    group = "build"
    workingDir = file("../workers")
    commandLine("make", "android")
}

tasks.register<Copy>("buildWorkersAndroid") {
    group = "build"
    dependsOn(runBuildWorkersAndroid)
    from("../workers/bin/android")
    into("src/androidMain/jniLibs")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("Jvm")) {
        dependsOn("buildGoWrapperDesktop", "buildWorkersDesktop")
    } else if (name.contains("Android")) {
        dependsOn("buildGoWrapperAndroid", "buildWorkersAndroid")
    }
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn("buildGoWrapperDesktop", "buildWorkersDesktop")
}

tasks.withType<com.android.build.gradle.tasks.MergeSourceSetFolders>().configureEach {
    dependsOn("buildGoWrapperAndroid", "buildWorkersAndroid")
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractRunDistributableTask>().configureEach {
    dependsOn("buildGoWrapperDesktop", "buildWorkersDesktop")
}
