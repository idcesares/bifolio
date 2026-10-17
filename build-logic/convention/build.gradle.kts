plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.plugins.android.library.toDep())
    implementation(libs.plugins.android.application.toDep())
    implementation(libs.plugins.kotlin.jvm.toDep())
    implementation(libs.plugins.kotlin.compose.toDep())
    implementation(libs.plugins.roborazzi.toDep())
    implementation(libs.plugins.ktlint.toDep())
    implementation(libs.plugins.detekt.toDep())
}

fun Provider<PluginDependency>.toDep() = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
