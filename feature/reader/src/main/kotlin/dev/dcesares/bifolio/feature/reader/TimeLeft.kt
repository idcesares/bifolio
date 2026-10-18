package dev.dcesares.bifolio.feature.reader

/** Percent of the document read and whole minutes left to read in it (UI-06). */
public data class TimeLeft(
    val percent: Int,
    val documentMinutes: Int,
)
