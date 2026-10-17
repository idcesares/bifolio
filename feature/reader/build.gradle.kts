plugins {
    id("bifolio.android.compose")
}

android {
    namespace = "dev.dcesares.bifolio.feature.reader"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:parse"))
    implementation(project(":core:layout"))
    implementation(project(":core:text"))
    implementation(project(":core:highlight"))
    implementation(project(":core:math"))
    implementation(libs.androidx.window)
}
