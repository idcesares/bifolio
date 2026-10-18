package dev.dcesares.bifolio.feature.reader

/** Whole minutes left to read in the current section and in the whole document (UI-06). */
public data class TimeLeft(
    val sectionMinutes: Int,
    val documentMinutes: Int,
)
