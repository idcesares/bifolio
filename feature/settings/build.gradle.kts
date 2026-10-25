plugins {
    id("bifolio.android.compose")
}

android {
    namespace = "dev.dcesares.bifolio.feature.settings"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
}
