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

    @Query("UPDATE songs SET isDownloaded = :isDownloaded, localFilePath = :localFilePath, fileSizeBytes = :fileSize WHERE id = :songId")
    suspend fun updateDownloadStatus(songId: String, isDownloaded: Boolean, localFilePath: String?, fileSize: Long)

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY displayOrder ASC")
    fun getDownloadedSongs(): Flow<List<Song>>
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
    val audioFileURL: String,
    val lyricsTeluguFileName: String?,
    val lyricsEnglishFileName: String?,
    val duration: Int,
    val displayOrder: Int,
    val isDownloaded: Boolean,
    val localFilePath: String?,
    val fileSizeBytes: Long,
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

@Dao
interface SlokaDao {
    @Query("SELECT * FROM slokas WHERE godId = :godId ORDER BY displayOrder ASC")
    fun getSlokasByGod(godId: String): Flow<List<Sloka>>

    @Query("SELECT * FROM slokas WHERE id = :id")
    suspend fun getSlokaById(id: String): Sloka?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sloka: Sloka)

    @Query("DELETE FROM slokas")
    suspend fun deleteAllSlokas()
}

@Dao
interface SlokaCounterDao {
    @Query("SELECT * FROM sloka_counters WHERE slokaId = :slokaId")
    suspend fun getCounter(slokaId: String): SlokaCounter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCounter(counter: SlokaCounter)

    @Query("DELETE FROM sloka_counters WHERE slokaId = :slokaId")
    suspend fun resetCounter(slokaId: String)
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
        LyricsEntry::class,
        Sloka::class,
        SlokaCounter::class
    ],
    version = 8, // Incremented to 8 for Sloka support
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
    abstract fun slokaDao(): SlokaDao
    abstract fun slokaCounterDao(): SlokaCounterDao

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

        // Migration from 6 to 7 - add download fields to songs table
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room doesn't like DEFAULT values in migrations - use a workaround
                // 1. Add columns as nullable first
                try { 
                    db.execSQL("ALTER TABLE songs ADD COLUMN isDownloaded INTEGER") 
                } catch (_: Exception) {}
                try { 
                    db.execSQL("ALTER TABLE songs ADD COLUMN localFilePath TEXT") 
                } catch (_: Exception) {}
                try { 
                    db.execSQL("ALTER TABLE songs ADD COLUMN fileSizeBytes INTEGER") 
                } catch (_: Exception) {}
                
                // 2. Update existing rows with default values
                try {
                    db.execSQL("UPDATE songs SET isDownloaded = 0 WHERE isDownloaded IS NULL")
                    db.execSQL("UPDATE songs SET fileSizeBytes = 0 WHERE fileSizeBytes IS NULL")
                } catch (_: Exception) {}
            }
        }

        // Migration from 7 to 8 - add Sloka support
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS slokas (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        godId TEXT NOT NULL,
                        languageDefault TEXT NOT NULL DEFAULT 'telugu',
                        scriptTeluguFileName TEXT,
                        scriptEnglishFileName TEXT,
                        displayOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sloka_counters (
                        slokaId TEXT NOT NULL PRIMARY KEY,
                        count INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: android.content.Context): DivineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    DivineDatabase::class.java,
                    "divine_database"
                )
                // Use destructive migration - simpler and safer for this app
                // When schema changes, database is wiped and repopulated from JSON
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                // Clear SharedPreferences version when database is destroyed
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        // Clear the version in SharedPreferences so data gets repopulated
                        context.getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .remove("version")
                            .apply()
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Assets table DAO (already present)
// AssetDao (interface)
@Dao
interface AssetDao {
    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<ContentAsset>

    // Remove any assets by type, e.g., legacy 'audio' entries
    @Query("DELETE FROM assets WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: ContentAsset)

    @Query("DELETE FROM assets WHERE path = :path AND source = :source")
    suspend fun deleteByPathAndSource(path: String, source: String)
}
