import com.android.build.api.dsl.TestExtension

plugins {
    id("com.android.test")
}

extensions.configure<TestExtension> {
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 37
    }
    targetProjectPath = ":app"
}
