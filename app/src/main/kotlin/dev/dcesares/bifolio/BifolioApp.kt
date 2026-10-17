package dev.dcesares.bifolio

import android.app.Application
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.data.createReadingPreferences
import dev.dcesares.bifolio.data.createRecentsRepository

class BifolioApp : Application() {
    val recents: RecentsRepository by lazy { createRecentsRepository(this) }
    val readingPreferences: ReadingPreferences by lazy { createReadingPreferences(this) }
}
