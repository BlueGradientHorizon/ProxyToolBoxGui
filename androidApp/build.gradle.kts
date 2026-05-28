import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appVersion: String by project

android {
    namespace = "com.bghorizon.proxytoolboxgui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
            keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            storeFile = keystoreProperties.getProperty("STORE_FILE")?.let { rootProject.file(it) }
            storePassword = keystoreProperties.getProperty("STORE_PASSWORD")
        }
    }

    defaultConfig {
        applicationId = "com.bghorizon.proxytoolboxgui"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = appVersion
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            // Check if the variant has a signing configuration
            val signedStatus =
                if (variant.name == "debug" || variant.buildType == "release") "signed" else "unsigned"
            output.outputFileName.set("${rootProject.name}_${output.versionName.get()}_${abi}_${variant.name}_$signedStatus.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.room.runtime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.compose.uiTooling)
}
