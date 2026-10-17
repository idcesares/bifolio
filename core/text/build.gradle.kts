plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.text"
}

dependencies {
    api(project(":core:layout"))
}
