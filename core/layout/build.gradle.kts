plugins {
    id("bifolio.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))

    testImplementation(project(":core:parse"))
}
