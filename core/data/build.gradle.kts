plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.data"
}

dependencies {
    api(project(":core:model"))
}
