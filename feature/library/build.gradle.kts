plugins {
    id("bifolio.android.compose")
}

android {
    namespace = "dev.dcesares.bifolio.feature.library"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
}
