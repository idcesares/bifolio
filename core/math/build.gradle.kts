plugins {
    id("bifolio.android.library")
}

android {
    namespace = "dev.dcesares.bifolio.math"
}

dependencies {
    api(project(":core:model"))
}
