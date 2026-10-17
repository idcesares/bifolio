import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("bifolio.quality")
}

val libs = versionCatalogs.named("libs")

extensions.configure<LibraryExtension> {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
