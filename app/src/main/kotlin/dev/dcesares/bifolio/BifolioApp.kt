package dev.dcesares.bifolio

import android.app.Application
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.data.createRecentsRepository

class BifolioApp : Application() {
    val recents: RecentsRepository by lazy { createRecentsRepository(this) }
}
