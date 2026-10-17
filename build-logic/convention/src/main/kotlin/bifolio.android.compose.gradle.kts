plugins {
    id("bifolio.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "implementation"("androidx.compose.runtime:runtime")
    "implementation"("androidx.compose.ui:ui")
    "implementation"("androidx.compose.ui:ui-graphics")
    "implementation"("androidx.compose.foundation:foundation")
    "implementation"("androidx.compose.ui:ui-tooling-preview")
    "debugImplementation"("androidx.compose.ui:ui-tooling")

    // Screenshot tests (ADR-0011): Robolectric and Compose test rules run on JUnit 4 through the vintage engine.
    "testImplementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "testImplementation"("androidx.compose.ui:ui-test-junit4")
    "testImplementation"(libs.findLibrary("junit4").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("roborazzi").get())
    "testImplementation"(libs.findLibrary("roborazzi-compose").get())
    "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
    "testRuntimeOnly"(libs.findLibrary("junit-vintage-engine").get())
    "debugImplementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "debugImplementation"("androidx.compose.ui:ui-test-manifest")
}
