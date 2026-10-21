plugins {
    id("bifolio.android.compose")
}

android {
    namespace = "dev.dcesares.bifolio.math"
}

dependencies {
    api(project(":core:model"))
    // ADR-0006: the renderer and its fonts live only here. No com.hrm type crosses the public API of this module.
    implementation(libs.latex.renderer)
    implementation(libs.latex.fonts.katex)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
