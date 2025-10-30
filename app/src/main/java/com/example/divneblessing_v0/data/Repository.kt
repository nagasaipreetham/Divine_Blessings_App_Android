package com.example.divneblessing_v0.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    // Public insert helpers (safe passthroughs)
    suspend fun insertGod(god: God) {
        database.godDao().insertGod(god)
    }

    suspend fun insertSong(song: Song) {
        database.songDao().insertSong(song)
    }

    /**
     * Reconcile DB with APK assets and mirror assets into filesDir for future updates.
     * - Scans audio, lyrics, and common image folders in assets
     * - Upserts "assets" source entries into the assets table
     * - Copies assets to filesDir/content/<type>/... and upserts "files" source entries with checksum/version
     */
    suspend fun reconcileAssets(context: android.content.Context) {
        val am = context.assets
        val now = System.currentTimeMillis()

        val scanTargets = listOf(
            "audio" to "audio",
            "lyrics" to "lyrics",
            "images" to "image",
            "images/gods" to "image"
        )

        for ((folder, type) in scanTargets) {
            // Recursively list only files (skip directories)
            val filePaths = listAssetFilesRecursively(am, folder)

            for (relPath in filePaths) {
                val size = safeSize(am, relPath) ?: 0L
                val checksum = safeChecksum(am, relPath)

                database.assetDao().upsert(
                    ContentAsset(
                        path = relPath,
                        type = type,
                        version = 1,
                        checksum = checksum,
                        sizeBytes = size,
                        lastUpdated = now,
                        source = "assets"
                    )
                )

                // Mirror into filesDir/content/<type>/<subpath>
                val subPath = relPath.removePrefix("$folder/")
                val destFile = java.io.File(context.filesDir, "content/$type/$subPath")
                destFile.parentFile?.mkdirs()

                val needsCopy = !destFile.exists() || checksum == null || checksum != safeFileChecksum(destFile)
                if (needsCopy) {
                    try {
                        copyAssetToFile(am, relPath, destFile)
                        val fileChecksum = safeFileChecksum(destFile)
                        val existingFilesEntry = database.assetDao()
                            .getByType(type)
                            .find { it.path == relPath && it.source == "files" }

                        val nextVersion = (existingFilesEntry?.version ?: 0) + 1
                        database.assetDao().upsert(
                            ContentAsset(
                                path = relPath,
                                type = type,
                                version = nextVersion,
                                checksum = fileChecksum,
                                sizeBytes = destFile.length(),
                                lastUpdated = now,
                                source = "files"
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("Repository", "Failed to mirror asset $relPath → ${destFile.absolutePath}", e)
                    }
                }
            }
        }

        // Preprocess lyrics into DB after reconciling files
        preprocessAllLyrics(context)
    }

    // Recursively list files under an assets directory path (returns full relative paths)
    private fun listAssetFilesRecursively(am: android.content.res.AssetManager, dir: String): List<String> {
        val result = mutableListOf<String>()

        fun recurse(path: String) {
            val children = try { am.list(path) } catch (_: Exception) { null }
            if (children == null || children.isEmpty()) {
                // If no children, try opening: if success, it's a file
                try {
                    am.open(path).close()
                    result.add(path)
                } catch (_: Exception) {
                    // Not a file (or not accessible), skip
                }
            } else {
                for (child in children) {
                    val childPath = if (path.isEmpty()) child else "$path/$child"
                    recurse(childPath)
                }
            }
        }

        recurse(dir)
        return result
    }

    // Get preprocessed lyrics for (songId, language) from DB, or null if missing
    suspend fun getLyricsLines(songId: String, language: String): List<com.example.divneblessing_v0.ui.player.LrcLine>? {
        val entry = database.lyricsDao().getEntry(songId, language)
        return entry?.let { jsonToLines(it.jsonLines) }
    }

    // Preprocess all assets lyrics into DB at startup (speeds up loading)
    suspend fun preprocessAllLyrics(context: Context) {
        val am = context.assets
        val folders = listOf("telugu" to "te", "english" to "en")
        for ((lang, code) in folders) {
            val dir = "lyrics/$lang"
            val files = try { am.list(dir) ?: emptyArray() } catch (_: Exception) { emptyArray() }
            for (name in files) {
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
            }
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

    private fun safeList(am: android.content.res.AssetManager, dir: String): Array<String> {
        return try { am.list(dir) ?: emptyArray() } catch (_: Exception) { emptyArray() }
    }
    private fun safeSize(am: android.content.res.AssetManager, relPath: String): Long? {
        return try { am.openFd(relPath).length } catch (_: Exception) {
            try { am.open(relPath).use { it.available().toLong() } } catch (_: Exception) { null }
        }
    }
    private fun safeChecksum(am: android.content.res.AssetManager, relPath: String): String? {
        return try {
            am.open(relPath).use { input ->
                val buf = ByteArray(8192)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var read: Int
                while (true) {
                    read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) { null }
    }
    private fun safeFileChecksum(file: java.io.File): String? {
        return try {
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var read: Int
                while (true) {
                    read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) { null }
    }
    private fun copyAssetToFile(am: android.content.res.AssetManager, relPath: String, dest: java.io.File) {
        am.open(relPath).use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
    }
}
