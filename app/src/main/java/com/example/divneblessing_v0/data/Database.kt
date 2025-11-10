package com.example.divneblessing_v0.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// DAOs (Data Access Objects)
@Dao
interface GodDao {
    @Query("SELECT * FROM gods ORDER BY displayOrder ASC")
    fun getAllGods(): Flow<List<God>>

    @Query("SELECT * FROM gods WHERE id = :godId")
    suspend fun getGodById(godId: String): God?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(god: God)

    @Query("DELETE FROM gods")
    suspend fun deleteAllGods()
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE godId = :godId ORDER BY displayOrder ASC")
    fun getSongsByGod(godId: String): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Query("SELECT s.*, g.name as godName FROM songs s INNER JOIN gods g ON s.godId = g.id WHERE s.title LIKE '%' || :query || '%' OR g.name LIKE '%' || :query || '%' ORDER BY s.displayOrder ASC")
    fun searchSongs(query: String): Flow<List<SongWithGod>>

    @Query("SELECT s.*, g.name as godName FROM songs s INNER JOIN gods g ON s.godId = g.id ORDER BY s.displayOrder ASC")
    fun getAllSongsWithGods(): Flow<List<SongWithGod>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: Favorite)

    @Delete
    suspend fun removeFavorite(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavoriteById(songId: String)
}

@Dao
interface SongCounterDao {
    @Query("SELECT * FROM song_counters WHERE songId = :songId")
    suspend fun getCounter(songId: String): SongCounter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCounter(counter: SongCounter)

    @Query("UPDATE song_counters SET count = :count, lastUpdated = :timestamp WHERE songId = :songId")
    suspend fun updateCounter(songId: String, count: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM song_counters WHERE songId = :songId")
    suspend fun resetCounter(songId: String)

    // Reset all counters in DB on cold app start
    @Query("DELETE FROM song_counters")
    suspend fun resetAllCounters()
}

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettings(): Flow<UserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: UserSettings)

    @Query("UPDATE user_settings SET userName = :userName WHERE id = 1")
    suspend fun updateUserName(userName: String)

    @Query("UPDATE user_settings SET themeMode = :themeMode WHERE id = 1")
    suspend fun updateThemeMode(themeMode: String)

    @Query("UPDATE user_settings SET accentColor = :accentColor WHERE id = 1")
    suspend fun updateAccentColor(accentColor: String)

    @Query("UPDATE user_settings SET defaultLanguage = :language WHERE id = 1")
    suspend fun updateDefaultLanguage(language: String)

    @Query("UPDATE user_settings SET profileImagePath = :imagePath WHERE id = 1")
    suspend fun updateProfileImage(imagePath: String?)
}

// Data class for songs with god information
data class SongWithGod(
    val id: String,
    val title: String,
    val godId: String,
    val languageDefault: String,
    val audioFileName: String,
    val lyricsTeluguFileName: String?,
    val lyricsEnglishFileName: String?,
    val duration: Int,
    val displayOrder: Int,
    val godName: String
)

// Add LyricsDao
@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId AND language = :language")
    suspend fun getEntry(songId: String, language: String): LyricsEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricsEntry)
}

// Main Database
// Class: DivineDatabase
@Database(
    entities = [
        God::class,
        Song::class,
        Favorite::class,
        SongCounter::class,
        UserSettings::class,
        ContentAsset::class,
        LyricsEntry::class
    ],
    version = 6, // Incremented version to 6 to fix schema mismatch
    exportSchema = false
)
abstract class DivineDatabase : RoomDatabase() {
    abstract fun godDao(): GodDao
    abstract fun songDao(): SongDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun songCounterDao(): SongCounterDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun assetDao(): AssetDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        @Volatile
        private var INSTANCE: DivineDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assets (
                        path TEXT NOT NULL,
                        type TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        checksum TEXT,
                        sizeBytes INTEGER NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        PRIMARY KEY (path, source)
                    )
                    """.trimIndent()
                )
            }
        }

        // New migration to create lyrics table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lyrics (
                        songId TEXT NOT NULL,
                        language TEXT NOT NULL,
                        jsonLines TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        PRIMARY KEY (songId, language)
                    )
                    """.trimIndent()
                )
            }
        }

        // Migration from 4 to 5 - no schema changes needed
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 4 already has the correct schema from MIGRATION_3_4
                // No changes needed, just version bump
            }
        }

        // Migration from 3 to 4 - ignore duplicate columns gracefully
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE songs ADD COLUMN languageDefault TEXT NOT NULL DEFAULT 'telugu'") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE songs ADD COLUMN lyricsTeluguFileName TEXT") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE songs ADD COLUMN lyricsEnglishFileName TEXT") } catch (_: Exception) {}
            }
        }

        // Migration from 5 to 6 - no schema changes, just version bump
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes needed, just version bump
            }
        }

        fun getDatabase(context: android.content.Context): DivineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    DivineDatabase::class.java,
                    "divine_database"
                )
                // Use destructive migration to handle all migration issues
                // This will recreate the database if any migration fails
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Assets table DAO (already present)
@Dao
interface AssetDao {
    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<ContentAsset>

    @Query("SELECT * FROM assets WHERE type = :type")
    suspend fun getByType(type: String): List<ContentAsset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: ContentAsset)

    @Query("DELETE FROM assets WHERE path = :path AND source = :source")
    suspend fun deleteByPathAndSource(path: String, source: String)
}
