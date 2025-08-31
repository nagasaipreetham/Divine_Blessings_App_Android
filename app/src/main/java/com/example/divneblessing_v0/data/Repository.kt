package com.example.divneblessing_v0.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class DivineRepository(private val database: DivineDatabase) {

    // God operations
    fun getAllGods(): Flow<List<God>> = database.godDao().getAllGods()
    
    suspend fun getGodById(godId: String): God? = database.godDao().getGodById(godId)

    // Song operations
    fun getSongsByGod(godId: String): Flow<List<Song>> = database.songDao().getSongsByGod(godId)
    
    suspend fun getSongById(songId: String): Song? = database.songDao().getSongById(songId)
    
    fun searchSongs(query: String): Flow<List<SongWithGod>> = database.songDao().searchSongs(query)
    
    fun getAllSongsWithGods(): Flow<List<SongWithGod>> = database.songDao().getAllSongsWithGods()

    // Favorite operations
    fun getAllFavorites(): Flow<List<Favorite>> = database.favoriteDao().getAllFavorites()
    
    fun isFavorite(songId: String): Flow<Boolean> = database.favoriteDao().isFavorite(songId)
    
    suspend fun addFavorite(songId: String) {
        database.favoriteDao().addFavorite(Favorite(songId = songId))
    }
    
    suspend fun removeFavorite(songId: String) {
        database.favoriteDao().removeFavoriteById(songId)
    }
    
    suspend fun toggleFavorite(songId: String) {
        val isCurrentlyFavorite = database.favoriteDao().isFavorite(songId).first()
        if (isCurrentlyFavorite) {
            removeFavorite(songId)
        } else {
            addFavorite(songId)
        }
    }

    // Song counter operations
    suspend fun getSongCounter(songId: String): Int {
        return database.songCounterDao().getCounter(songId)?.count ?: 0
    }
    
    suspend fun updateSongCounter(songId: String, count: Int) {
        database.songCounterDao().insertOrUpdateCounter(SongCounter(songId = songId, count = count))
    }
    
    suspend fun resetSongCounter(songId: String) {
        database.songCounterDao().resetCounter(songId)
    }

    // User settings operations
    fun getUserSettings(): Flow<UserSettings?> = database.userSettingsDao().getUserSettings()
    
    suspend fun updateUserName(userName: String) {
        database.userSettingsDao().updateUserName(userName)
    }
    
    suspend fun updateThemeMode(themeMode: String) {
        database.userSettingsDao().updateThemeMode(themeMode)
    }
    
    suspend fun updateAccentColor(accentColor: String) {
        database.userSettingsDao().updateAccentColor(accentColor)
    }
    
    suspend fun updateDefaultLanguage(language: String) {
        database.userSettingsDao().updateDefaultLanguage(language)
    }
    
    suspend fun updateProfileImage(imagePath: String?) {
        database.userSettingsDao().updateProfileImage(imagePath)
    }

    // Combined data operations
    fun getSongsByGodWithFavorites(godId: String): Flow<List<SongItem>> {
        return combine(
            database.songDao().getSongsByGod(godId),
            database.favoriteDao().getAllFavorites()
        ) { songs, favorites ->
            val favoriteIds = favorites.map { it.songId }.toSet()
            songs.map { song ->
                SongItem(
                    id = song.id,
                    title = song.title,
                    godId = song.godId,
                    godName = "", // Will be filled by UI layer
                    isFavorite = favoriteIds.contains(song.id)
                )
            }
        }
    }

    fun getSearchResultsWithFavorites(query: String): Flow<List<SearchResult>> {
        return combine(
            database.songDao().searchSongs(query),
            database.favoriteDao().getAllFavorites()
        ) { songs, favorites ->
            val favoriteIds = favorites.map { it.songId }.toSet()
            songs.map { song ->
                SearchResult(
                    songId = song.id,
                    title = song.title,
                    godName = song.godName,
                    isFavorite = favoriteIds.contains(song.id)
                )
            }
        }
    }

    fun getFavoritesWithDetails(): Flow<List<SongItem>> {
        return combine(
            database.favoriteDao().getAllFavorites(),
            database.songDao().getAllSongsWithGods()
        ) { favorites, songs ->
            val songMap = songs.associateBy { it.id }
            favorites.mapNotNull { favorite ->
                val song = songMap[favorite.songId] ?: return@mapNotNull null
                SongItem(
                    id = song.id,
                    title = song.title,
                    godId = song.godId,
                    godName = song.godName,
                    isFavorite = true
                )
            }
        }
    }

    // Initialize default settings
    suspend fun initializeDefaultSettings() {
        val existingSettings = database.userSettingsDao().getUserSettings().first()
        if (existingSettings == null) {
            database.userSettingsDao().insertOrUpdateSettings(UserSettings())
        }
    }

    // Sample data insertion (for development)
    suspend fun insertSampleData() {
        android.util.Log.d("Repository", "Inserting sample data...")
        
        // Insert sample gods - only Vishnu for now
        val gods = listOf(
            God("god_vishnu", "Lord Vishnu", "vishnu.png", 1)
        )
        
        gods.forEach { god ->
            android.util.Log.d("Repository", "Inserting god: ${god.name}")
            database.godDao().insertGod(god)
        }

        // Insert sample songs - using song_1 data for now
        val songs = listOf(
            Song("song_1", "Vishnu Sahasranama Stotram", "god_vishnu", "telugu", "song_1.mp3", "song_1_te.lrc", "song_1_en.lrc", 1800000, 1)
        )
        
        songs.forEach { song ->
            android.util.Log.d("Repository", "Inserting song: ${song.title}")
            database.songDao().insertSong(song)
        }
        
        android.util.Log.d("Repository", "Sample data insertion completed")
    }
}
