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
        }
    }
    
    /**
     * Updates the application's language setting
     * @param language The language code ("english" or "telugu")
     */
    fun updateLanguage(language: String) {
        if (currentLanguage != language) {
            currentLanguage = language
            android.util.Log.d("DivineApplication", "Language updated to: $language")
            
            // Notify any listeners about language change
            applicationScope.launch {
                repository.updateDefaultLanguage(language)
            }
        }
    }
    
    /**
     * Gets the current language setting
     * @return The current language code ("english" or "telugu")
     */
    fun getCurrentLanguage(): String {
        return currentLanguage
    }
}
