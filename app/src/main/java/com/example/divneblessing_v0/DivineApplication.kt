package com.example.divneblessing_v0

import android.app.Application
import com.example.divneblessing_v0.data.DivineDatabase
import com.example.divneblessing_v0.data.DivineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DivineApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Database instance
    val database by lazy { DivineDatabase.getDatabase(this) }

    // Repository instance
    val repository by lazy { DivineRepository(database) }

    // Current language setting
    private var currentLanguage: String = "telugu"

    // Session-only: remember the lyrics language chosen inside the player
    private var currentLyricsLanguage: String? = null

    override fun onCreate() {
        super.onCreate()
        // Load saved language preference
        val sharedPrefs = getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        currentLanguage = sharedPrefs.getString("default_language", "telugu") ?: "telugu"

        // Initialize database with default settings and sample data
        applicationScope.launch {
            android.util.Log.d("DivineApplication", "Initializing database...")
            repository.initializeDefaultSettings()
            
            // Check if we need to insert sample data (only if gods table is empty)
            val gods = repository.getAllGods().first()
            android.util.Log.d("DivineApplication", "Found ${gods.size} gods in database")
            if (gods.isEmpty()) {
                android.util.Log.d("DivineApplication", "Inserting sample data...")
                repository.insertSampleData()
                val newGods = repository.getAllGods().first()
                android.util.Log.d("DivineApplication", "After insertion: ${newGods.size} gods")
            }

            // Insert Shiva + Lingashtakam if not present (safe, runs once)
            val newGodId = "god_shiva"
            val newSongId = "Lingashtakam"

            if (repository.getGodById(newGodId) == null) {
                repository.insertGod(
                    com.example.divneblessing_v0.data.God(
                        id = newGodId,
                        name = "Lord Shiva",
                        imageFileName = "shiva.png",
                        displayOrder = 2 // show after existing Vishnu (which is 1)
                    )
                )
            }

            if (repository.getSongById(newSongId) == null) {
                repository.insertSong(
                    com.example.divneblessing_v0.data.Song(
                        id = newSongId, // must match asset filenames
                        title = "Lingashtakam",
                        godId = newGodId,
                        languageDefault = "telugu", // default selection in UI; can be "english"
                        audioFileName = "Lingashtakam.mp3", // kept for consistency
                        lyricsTeluguFileName = "Lingashtakam_te.lrc",
                        lyricsEnglishFileName = "Lingashtakam_en.lrc",
                        duration = 0, // optional; player reads actual duration
                        displayOrder = 1
                    )
                )
            }
        }
    }

    fun updateLanguage(language: String) {
        if (currentLanguage != language) {
            currentLanguage = language
            android.util.Log.d("DivineApplication", "Language updated to: $language")
            applicationScope.launch {
                repository.updateDefaultLanguage(language)
            }
        }
    }

    fun getCurrentLanguage(): String {
        return currentLanguage
    }

    // Session player-language helpers
    fun setCurrentLyricsLanguage(language: String) {
        currentLyricsLanguage = language
    }

    fun getCurrentLyricsLanguageOrDefault(): String {
        return currentLyricsLanguage ?: currentLanguage
    }
}
