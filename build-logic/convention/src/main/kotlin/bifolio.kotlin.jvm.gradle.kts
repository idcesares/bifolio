import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("bifolio.quality")
}

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(21)
    explicitApi = ExplicitApiMode.Strict
}

dependencies {
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("kotest-property").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
