import java.util.Properties

plugins {
    id("bifolio.android.application")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}

// Upload key: BIFOLIO_KEYSTORE, BIFOLIO_KEYSTORE_PASSWORD, BIFOLIO_KEY_ALIAS and BIFOLIO_KEY_PASSWORD, read from
// the environment or local.properties. Without them the release APK stays unsigned. Nothing is committed.
fun releaseSecret(name: String): String? =
    System.getenv(name) ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }.getProperty(name)
    }

android {
    signingConfigs {
        val store = releaseSecret("BIFOLIO_KEYSTORE")
        if (store != null) {
            create("release") {
                storeFile = file(store)
                storePassword = releaseSecret("BIFOLIO_KEYSTORE_PASSWORD")
                keyAlias = releaseSecret("BIFOLIO_KEY_ALIAS")
                keyPassword = releaseSecret("BIFOLIO_KEY_PASSWORD")
            }
        }
    }
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            // PRF-07: R8 full mode (the AGP default) with resource shrinking.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildTypes {
        // Release with R8, debug-signed, for :benchmark. Never published.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }
    // PRF-06 is measured per ABI.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    testOptions {
        managedDevices {
            localDevices {
                create("pixelFoldApi37") {
                    device = "Pixel Fold"
                    apiLevel = 37
                    systemImageSource = "google"
                }
            }
        }
    }
}

dependencies {
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.window.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
