package com.example.divneblessing_v0.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class DivineRepository(private val database: DivineDatabase) {

    // God operations
    fun getAllGods(): Flow<List<God>> = database.godDao().getAllGods()
    suspend fun getGodById(godId: String): God? = database.godDao().getGodById(godId)

    // Song operations
    fun getSongsByGod(godId: String): Flow<List<Song>> = database.songDao().getSongsByGod(godId)
    suspend fun getSongById(songId: String): Song? = database.songDao().getSongById(songId)
    
    fun searchSongs(query: String): Flow<List<SongWithGod>> = database.songDao().searchSongs(query)
    
    fun getAllSongsWithGods(): Flow<List<SongWithGod>> = database.songDao().getAllSongsWithGods()

    // Sloka operations
    fun getSlokasByGod(godId: String): Flow<List<Sloka>> = database.slokaDao().getSlokasByGod(godId)
    suspend fun getSlokaById(slokaId: String): Sloka? = database.slokaDao().getSlokaById(slokaId)

    // Sloka Favorites
    fun getFavoriteSlokas(): Flow<List<SlokaFavorite>> = database.slokaFavoriteDao().getAllFavorites()
    
    fun isSlokaFavorite(slokaId: String): Flow<Boolean> = database.slokaFavoriteDao().isFavorite(slokaId)
    
    suspend fun toggleSlokaFavorite(slokaId: String) {
        val dao = database.slokaFavoriteDao()
        val isFav = dao.isFavorite(slokaId).first()
        if (isFav) {
            dao.removeFavoriteById(slokaId)
        } else {
            dao.addFavorite(SlokaFavorite(slokaId))
        }
    }
    
    // Helper to get Sloka items with favorite status efficiently
    fun getSlokasByGodWithFavorites(godId: String): Flow<List<SlokaItem>> {
        return combine(
            database.slokaDao().getSlokasByGod(godId),
            database.slokaFavoriteDao().getAllFavorites()
        ) { slokas, favorites ->
            val favIds = favorites.map { it.slokaId }.toSet()
            slokas.map { sloka ->
                SlokaItem(
                    id = sloka.id,
                    title = sloka.title,
                    godId = sloka.godId,
                    isFavorite = favIds.contains(sloka.id)
                )
            }
        }
    }

    // Helper to get favorite slokas with details for the Favorites screen
    fun getFavoriteSlokasWithDetails(): Flow<List<SlokaItem>> {
        return database.slokaFavoriteDao().getAllFavorites().flatMapLatest { favorites ->
             // Manual join: iterate and fetch
             flow {
                 val items = favorites.mapNotNull { fav ->
                     database.slokaDao().getSlokaById(fav.slokaId)?.let { sloka ->
                         SlokaItem(
                             id = sloka.id,
                             title = sloka.title,
                             godId = sloka.godId,
                             isFavorite = true
                         )
                     }
                 }
                 emit(items)
             }
        }
    }

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
    // Reset all counters (called on cold app start)
    suspend fun resetAllSongCounters() {
        database.songCounterDao().resetAllCounters()
    }

    // Sloka counter operations
    suspend fun getSlokaCounter(slokaId: String): Int {
        return database.slokaCounterDao().getCounter(slokaId)?.count ?: 0
    }
    suspend fun updateSlokaCounter(slokaId: String, count: Int) {
        database.slokaCounterDao().insertOrUpdateCounter(SlokaCounter(slokaId = slokaId, count = count))
    }
    suspend fun resetSlokaCounter(slokaId: String) {
        database.slokaCounterDao().resetCounter(slokaId)
    }

    // User settings operations
    fun getUserSettings(): Flow<UserSettings?> = database.userSettingsDao().getUserSettings()

    private suspend fun ensureSettingsExists() {
        val dao = database.userSettingsDao()
        val existing = dao.getUserSettings().first()
        if (existing == null) {
            dao.insertOrUpdateSettings(UserSettings())
        }
    }

    suspend fun updateUserName(userName: String) {
        ensureSettingsExists()
        database.userSettingsDao().updateUserName(userName)
    }

    suspend fun updateThemeMode(themeMode: String) {
        ensureSettingsExists()
        database.userSettingsDao().updateThemeMode(themeMode)
    }

    suspend fun updateAccentColor(accentColor: String) {
        ensureSettingsExists()
        database.userSettingsDao().updateAccentColor(accentColor)
    }

    suspend fun updateDefaultLanguage(language: String) {
        ensureSettingsExists()
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
                    isFavorite = favoriteIds.contains(song.id),
                    isDownloaded = song.isDownloaded
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

    // Method: populateDatabaseFromJsonIfNeeded
    suspend fun populateDatabaseFromJsonIfNeeded(context: Context) {
        try {
            val jsonString = try {
                context.assets.open("gods_songs.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) { 
                android.util.Log.e("Repository", "Failed to read gods_songs.json", e)
                return 
            }
        
            val obj = try { JSONObject(jsonString) } catch (e: Exception) { 
                android.util.Log.e("Repository", "Failed to parse gods_songs.json", e)
                return 
            }
        
            val jsonVersionStr = obj.optString("version", "0")
            val jsonVersion = jsonVersionStr.toLongOrNull() ?: 0L
        
            val prefs = context.getSharedPreferences("divine_settings", Context.MODE_PRIVATE)
            val storedVersionStr = prefs.getString("version", null)
            val storedVersion = storedVersionStr?.toLongOrNull() ?: 0L
        
            // Also populate when DB is empty (fresh install or cleared storage)
            val isDbEmpty = try { 
                database.godDao().getAllGods().first().isEmpty() 
            } catch (e: Exception) { 
                android.util.Log.w("Repository", "Could not check if DB is empty, assuming true", e)
                true 
            }
    
        if (jsonVersion > storedVersion || isDbEmpty) {
            database.godDao().deleteAllGods()
            database.songDao().deleteAllSongs()
            database.slokaDao().deleteAllSlokas()
    
            val godsArray = obj.optJSONArray("gods") ?: org.json.JSONArray()
            for (i in 0 until godsArray.length()) {
                val g = godsArray.optJSONObject(i) ?: continue
    
                val god = God(
                    id = g.optString("id"),
                    name = g.optString("name"),
                    imageFileName = g.optString("imageFileName"),
                    displayOrder = g.optInt("displayOrder", 0)
                )
                database.godDao().insert(god)
    
                val songsArray = g.optJSONArray("songs") ?: org.json.JSONArray()
                for (j in 0 until songsArray.length()) {
                    val s = songsArray.optJSONObject(j) ?: continue
    
                    val song = Song(
                        id = s.optString("id"),
                        title = s.optString("title"),
                        godId = s.optString("godId", god.id),
                        languageDefault = s.optString("languageDefault", "telugu"),
                        audioFileName = s.optString("audioFileName", ""),
                        audioFileURL = s.optString("audioFileURL", ""),
                        lyricsTeluguFileName = s.optString("lyricsTeluguFileName", "").takeIf { it.isNotEmpty() },
                        lyricsEnglishFileName = s.optString("lyricsEnglishFileName", "").takeIf { it.isNotEmpty() },
                        duration = s.optInt("duration", 0),
                        displayOrder = s.optInt("displayOrder", 0),
                        isDownloaded = false,
                        localFilePath = null,
                        fileSizeBytes = s.optLong("fileSizeBytes", 0)
                    )
                    database.songDao().insert(song)
                }

                val slokasArray = g.optJSONArray("slokas") ?: org.json.JSONArray()
                for (k in 0 until slokasArray.length()) {
                    val s = slokasArray.optJSONObject(k) ?: continue

                    val sloka = Sloka(
                        id = s.optString("id"),
                        title = s.optString("title"),
                        godId = s.optString("godId", god.id),
                        languageDefault = s.optString("languageDefault", "telugu"),
                        scriptTeluguFileName = s.optString("scriptTeluguFileName", ""),
                        scriptEnglishFileName = s.optString("scriptEnglishFileName", ""),
                        displayOrder = s.optInt("displayOrder", 0)
                    )
                    database.slokaDao().insert(sloka)
                }
            }
    
            prefs.edit().putString("version", jsonVersionStr).apply()
        }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Error in populateDatabaseFromJsonIfNeeded", e)
        }
    }

    suspend fun initializeDefaultSettings() {
        val settings = database.userSettingsDao().getUserSettings().first()
        if (settings == null) {
            // No settings exist, insert all defaults
            database.userSettingsDao().insertOrUpdateSettings(UserSettings())
        } else {
            // Settings exist, check individual fields and update missing ones
            if (settings.themeMode.isNullOrEmpty()) {
                database.userSettingsDao().updateThemeMode("system")
            }
            if (settings.accentColor.isNullOrEmpty()) {
                database.userSettingsDao().updateAccentColor("blue")
            }
            if (settings.defaultLanguage.isNullOrEmpty()) {
                database.userSettingsDao().updateDefaultLanguage("telugu")
            }
            if (settings.userName.isNullOrEmpty()) {
                database.userSettingsDao().updateUserName("Bhakta")
            }
        }
    }

    // Public insert helpers (safe passthroughs)
    suspend fun insertGod(god: God) {
        database.godDao().insert(god)
    }

    suspend fun insertSong(song: Song) {
        database.songDao().insert(song)
    }

    /**
     * Compact the database by checkpointing the WAL and vacuuming.
     * Vacuum reclaims free pages after checkpoint.
     */
    suspend fun compactDatabase() {
        try {
            // Use support database directly
            val db = database.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").close()
            db.query("VACUUM").close()
            android.util.Log.d("Repository", "Database compacted successfully")
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to compact database", e)
        }
    }

    /**
     * Remove legacy audio asset rows to reduce storage.
     * Audio is streamed, so no local tracking is needed.
     */
    suspend fun cleanupLegacyAudioAssets() {
        try {
            database.assetDao().deleteByType("audio")
            android.util.Log.d("Repository", "Legacy audio assets removed")
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to cleanup legacy audio assets", e)
        }
    }

    /**
     * Mark song as downloaded and save local file path
     */
    suspend fun markSongAsDownloaded(songId: String, localFilePath: String, fileSize: Long) {
        database.songDao().updateDownloadStatus(songId, true, localFilePath, fileSize)
    }

    /**
     * Mark song as not downloaded and clear local file path
     */
    suspend fun markSongAsNotDownloaded(songId: String) {
        database.songDao().updateDownloadStatus(songId, false, null, 0)
    }

    /**
     * Get all downloaded songs
     */
    fun getDownloadedSongs(): Flow<List<Song>> {
        return database.songDao().getDownloadedSongs()
    }

    /**
     * Get total size of downloaded songs in bytes
     */
    suspend fun getDownloadedSongsSize(): Long {
        return try {
            database.songDao().getDownloadedSongs().first().sumOf { it.fileSizeBytes }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Track assets in database and preprocess lyrics.
     * - Scans lyrics and images folders (NOT audio - streamed from cloud)
     * - Tracks metadata in database (no file copying)
     * - Preprocesses lyrics into Room DB for fast access
     */
    suspend fun reconcileAssets(context: android.content.Context) {
        try {
            val am = context.assets
            val now = System.currentTimeMillis()

            // REMOVED: Asset tracking with checksums (was causing 15-18MB database bloat)
            // Assets are bundled in APK and don't need tracking
            // Lyrics are preprocessed below for fast loading

            // Preprocess lyrics into DB for fast access
            preprocessAllLyrics(context)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Error in reconcileAssets", e)
        }
    }

    // Removed: listAssetFilesRecursively - no longer needed without asset tracking

    // Get preprocessed lyrics for (songId, language) from DB, or null if missing
    suspend fun getLyricsLines(songId: String, language: String): List<com.example.divneblessing_v0.ui.player.LrcLine>? {
        val entry = database.lyricsDao().getEntry(songId, language)
        return entry?.let { jsonToLines(it.jsonLines) }
    }

    // Preprocess all assets lyrics into DB at startup (speeds up loading)
    suspend fun preprocessAllLyrics(context: Context) {
        try {
            val am = context.assets
            val folders = listOf("telugu" to "te", "english" to "en")
            for ((lang, code) in folders) {
                val dir = "lyrics/$lang"
                val files = try { am.list(dir) ?: emptyArray() } catch (_: Exception) { 
                    android.util.Log.w("Repository", "Could not list lyrics folder: $dir")
                    emptyArray() 
                }
                for (name in files) {
                    try {
                        if (!name.endsWith(".lrc", ignoreCase = true)) continue
                        // Expect "{songId}_{code}.lrc"
                        val base = name.removeSuffix(".lrc")
                        val expectedSuffix = "_$code"
                        if (!base.endsWith(expectedSuffix)) continue
                        val songId = base.removeSuffix(expectedSuffix)
                        val relPath = "$dir/$name"

                        val lines = runCatching {
                            am.open(relPath).use { input ->
                                java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { br ->
                                    val raw = br.readLines()
                                    com.example.divneblessing_v0.ui.player.LrcParser.parse(raw)
                                }
                            }
                        }.getOrNull() ?: continue

                        val json = linesToJson(lines)
                        database.lyricsDao().upsert(
                            LyricsEntry(
                                songId = songId,
                                language = if (lang == "english") "english" else "telugu",
                                jsonLines = json,
                                updatedAt = System.currentTimeMillis(),
                                source = "assets"
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("Repository", "Error preprocessing lyrics file: $name", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Error in preprocessAllLyrics", e)
        }
    }

    // Convert lines → JSON array string
    private fun linesToJson(lines: List<com.example.divneblessing_v0.ui.player.LrcLine>): String {
        val arr = JSONArray()
        for (line in lines) {
            val obj = JSONObject()
            obj.put("x", line.text)
            obj.put("t", line.timeMs ?: -1) // use -1 for untimed
            arr.put(obj)
        }
        return arr.toString()
    }

    // Convert JSON array string → lines
    private fun jsonToLines(json: String): List<com.example.divneblessing_v0.ui.player.LrcLine> {
        val arr = JSONArray(json)
        val out = mutableListOf<com.example.divneblessing_v0.ui.player.LrcLine>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val text = obj.optString("x", "")
            val t = obj.optInt("t", -1)
            out.add(com.example.divneblessing_v0.ui.player.LrcLine(timeMs = if (t >= 0) t else null, text = text))
        }
        return out
    }

    // Removed: safeSize and safeChecksum - no longer needed without asset tracking
}
