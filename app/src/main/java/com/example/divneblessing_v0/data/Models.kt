package com.example.divneblessing_v0.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Data models for the Divine app

@Entity(tableName = "gods")
data class God(
    @PrimaryKey val id: String,
    val name: String,
    val imageFileName: String,
    val displayOrder: Int = 0
)

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val godId: String,
    val languageDefault: String = "telugu", // "telugu" or "english"
    val audioFileName: String,
    val lyricsTeluguFileName: String? = null,
    val lyricsEnglishFileName: String? = null,
    val duration: Int = 0, // in milliseconds
    val displayOrder: Int = 0
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "song_counters")
data class SongCounter(
    @PrimaryKey val songId: String,
    val count: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1, // Only one settings record
    val userName: String = "User",
    val themeMode: String = "system", // "light", "dark", "system"
    val accentColor: String = "blue", // "blue", "green", "purple", "orange", "red"
    val defaultLanguage: String = "telugu", // "telugu" or "english"
    val profileImagePath: String? = null
)

// UI Models for RecyclerViews
data class GodItem(
    val id: String,
    val name: String,
    val imageFileName: String
)

data class SongItem(
    val id: String,
    val title: String,
    val godId: String,
    val godName: String,
    var isFavorite: Boolean = false
)

// Search result model
data class SearchResult(
    val songId: String,
    val title: String,
    val godName: String,
    var isFavorite: Boolean = false
)


@Entity(
    tableName = "assets",
    primaryKeys = ["path", "source"]
)
data class ContentAsset(
    val path: String,            // e.g., "audio/song_1.mp3", "lyrics/song_1_en.lrc", "images/gods/vishnu.png"
    val type: String,            // "audio", "lyrics", "image"
    val version: Int = 1,        // versioning for updates
    val checksum: String? = null,// SHA-256 checksum
    val sizeBytes: Long = 0L,    // file size in bytes
    val lastUpdated: Long = System.currentTimeMillis(),
    val source: String = "assets" // "assets" (APK) or "files" (filesDir)
)
