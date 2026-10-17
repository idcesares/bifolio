plugins {
    id("bifolio.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.task.list.items)
    implementation(libs.commonmark.ext.footnotes)
}
