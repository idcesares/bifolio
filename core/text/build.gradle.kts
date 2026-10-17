plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.text"
}

dependencies {
    api(project(":core:layout"))

    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
