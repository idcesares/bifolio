plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.highlight"
}

dependencies {
    api(project(":core:model"))
}
