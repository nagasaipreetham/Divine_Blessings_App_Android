package com.example.divneblessing_v0

import android.app.Application
import com.example.divneblessing_v0.data.DivineDatabase
import com.example.divneblessing_v0.data.DivineRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DivineApplication : Application() {

    lateinit var repository: DivineRepository
    private var currentLyricsLanguage: String = "telugu"
    private var currentLanguage: String = "telugu"
    private val applicationScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val lyricsOverrides: MutableMap<String, String> = mutableMapOf() // songId -> "telugu"/"english"

    override fun onCreate() {
        super.onCreate()
        val db = DivineDatabase.getDatabase(this)
        repository = DivineRepository(db)

        val sharedPrefs = getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        currentLanguage = sharedPrefs.getString("default_language", "telugu") ?: "telugu"

        applicationScope.launch {
            try {
                // Populate database from JSON if needed
                repository.populateDatabaseFromJsonIfNeeded(this@DivineApplication)

                // Initialize default settings
                repository.initializeDefaultSettings()

                // Other background tasks
                repository.resetAllSongCounters()

                // Preprocess lyrics for fast loading (no asset tracking to save space)
                repository.reconcileAssets(this@DivineApplication)

                // Clean legacy audio asset rows (pre-cloud era)
                repository.cleanupLegacyAudioAssets()

                // Compact database to reclaim disk space
                repository.compactDatabase()
            } catch (e: Exception) {
                android.util.Log.e("DivineApplication", "Error during initialization", e)
            }
        }
    }

    fun updateLanguage(language: String) {
        if (currentLanguage != language) {
            currentLanguage = language
            applicationScope.launch { repository.updateDefaultLanguage(language) }
        }
    }

    fun getCurrentLanguage(): String {
        return currentLanguage
    }

    // Per-song override: if present, use it; otherwise use profile default language
    fun getLyricsLanguageForSong(songId: String): String {
        return lyricsOverrides[songId] ?: currentLanguage
    }

    fun setLyricsOverride(songId: String, language: String) {
        // language must be "telugu" or "english"
        lyricsOverrides[songId] = language.lowercase()
    }

    fun setCurrentLyricsLanguage(language: String) {
        currentLyricsLanguage = language
    }

    fun getCurrentLyricsLanguageOrDefault(): String {
        return currentLyricsLanguage
    }
}
