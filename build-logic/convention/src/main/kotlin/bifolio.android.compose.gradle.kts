plugins {
    id("bifolio.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
    "implementation"("androidx.compose.runtime:runtime")
}
