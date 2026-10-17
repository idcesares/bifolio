import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("bifolio.quality")
}

val libs = versionCatalogs.named("libs")

extensions.configure<ApplicationExtension> {
    namespace = "dev.dcesares.bifolio"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.dcesares.bifolio"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "implementation"("androidx.compose.runtime:runtime")
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
