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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
