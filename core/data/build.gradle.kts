plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.data"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
