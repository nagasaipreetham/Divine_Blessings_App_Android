package com.example.divneblessing_v0.service

import android.app.*
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.divneblessing_v0.MainActivity
import com.example.divneblessing_v0.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()
    private var currentSongId: String? = null
    private var currentSongTitle: String? = null
    private var isPlaying = false
    private val TAG = "MediaPlayerService"

    private var currentAudioFileName: String? = null
    private var currentSpeed: Float = 1.0f
    private var currentGodId: String? = null
    private var currentLyrics: List<com.example.divneblessing_v0.ui.player.LrcLine> = emptyList()
    private var isFavorite: Boolean = false
    private var currentGodBitmap: android.graphics.Bitmap? = null
    private var currentGodImageFileName: String? = null

    private val notificationHandler = Handler(Looper.getMainLooper())
    private var lastLyricLine: String = ""
    private val notificationUpdateRunnable =
            object : Runnable {
                override fun run() {
                    updateNotificationIfNeeded()
                    notificationHandler.postDelayed(
                            this,
                            2000
                    ) // Update every 2 seconds (reduced from 500ms)
                }
            }

    companion object {
        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
        const val ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE"
        const val ACTION_LIKE = "ACTION_LIKE"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                // Only start foreground if we have a song
                if (currentSongTitle != null || currentSongId != null) {
                    updateNotification()
                    notificationHandler.post(notificationUpdateRunnable)
                }
            }
            ACTION_PLAY_PAUSE, "PLAY" -> {
                togglePlayPause()
            }
            ACTION_LIKE -> {
                handleLikeAction()
            }
            "STOP" -> {
                notificationHandler.removeCallbacks(notificationUpdateRunnable)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                } catch (_: Exception) {}
                stopSelf()
            }
            else -> {
                // Only update notification if we have a song
                if (currentSongTitle != null || currentSongId != null) {
                    updateNotification()
                    notificationHandler.post(notificationUpdateRunnable)
                }
            }
        }
        // Keep service running across navigation/background until explicitly stopped
        return START_STICKY
    }

    fun loadSong(songId: String, title: String) {
        if (songId == currentSongId && mediaPlayer != null) {
            return
        }
        currentSongId = songId
        currentSongTitle = title
        // Seed favorite state once per song
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val app = application as? com.example.divneblessing_v0.DivineApplication
                isFavorite = app?.repository?.isFavorite(songId)?.first() ?: false
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateNotification(silent = true)
                }
            } catch (_: Exception) {}
        }

        // Start foreground service immediately with initial notification
        updateNotification(silent = false)
        notificationHandler.removeCallbacks(notificationUpdateRunnable)
        notificationHandler.post(notificationUpdateRunnable)

        try {
            // Release previous player if exists
            mediaPlayer?.release()
            mediaPlayer = null

            // Create new player
            mediaPlayer = MediaPlayer()

            // Use try-with-resources to ensure AssetFileDescriptor is closed properly
            val afd: AssetFileDescriptor? =
                    try {
                        // Interpret the argument as the exact file name (e.g., "Lingashtakam.mp3")
                        assets.openFd("audio/$songId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open audio file: audio/$songId", e)
                        null
                    }

            if (afd != null) {
                try {
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close() // Close the descriptor after setting data source

                    mediaPlayer?.setOnPreparedListener {
                        Log.d(TAG, "MediaPlayer prepared successfully")
                        // Auto-start playback when prepared
                        mediaPlayer?.start()
                        isPlaying = true
                        updateNotification(silent = true)
                    }

                    mediaPlayer?.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        false
                    }

                    mediaPlayer?.setOnCompletionListener {
                        isPlaying = false
                        updateNotification(silent = true)
                    }

                    // Use prepareAsync to avoid blocking the main thread
                    mediaPlayer?.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up MediaPlayer", e)
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadSong", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun loadSongByFile(audioFileName: String, title: String, songId: String? = null) {
        if (audioFileName == currentAudioFileName && mediaPlayer != null) {
            return
        }
        currentAudioFileName = audioFileName
        if (songId != null) currentSongId = songId
        currentSongTitle = title

        // Start foreground service immediately with initial notification
        updateNotification(silent = false)
        notificationHandler.removeCallbacks(notificationUpdateRunnable)
        notificationHandler.post(notificationUpdateRunnable)

        try {
            mediaPlayer?.release()
            mediaPlayer = null
            mediaPlayer = MediaPlayer()

            val afd: AssetFileDescriptor? =
                    try {
                        assets.openFd("audio/$audioFileName")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open audio file: audio/$audioFileName", e)
                        null
                    }

            if (afd != null) {
                try {
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()

                    mediaPlayer?.setOnPreparedListener {
                        mediaPlayer?.start()
                        isPlaying = true
                        updateNotification(silent = true)
                    }
                    mediaPlayer?.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        false
                    }
                    mediaPlayer?.setOnCompletionListener {
                        isPlaying = false
                        updateNotification(silent = true)
                    }
                    mediaPlayer?.prepareAsync()
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up MediaPlayer", e)
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadSongByFile", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun togglePlayPause(): Boolean {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.pause()
                    isPlaying = false
                } else {
                    it.start()
                    isPlaying = true
                }
                updateNotification(silent = false)
                return isPlaying
            } catch (e: Exception) {
                Log.e(TAG, "Error in togglePlayPause", e)
            }
        }
        return false
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
        } catch (e: Exception) {
            Log.e(TAG, "Error in seekTo", e)
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCurrentPosition", e)
            0
        }
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error in getDuration", e)
            0
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error in isPlaying", e)
            false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    "media_playback_channel",
                                    "Media Playback",
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Shows currently playing song"
                                setShowBadge(false)
                                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                                setSound(null, null) // Disable sound
                                enableVibration(false) // Disable vibration
                            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun updateNotificationIfNeeded() {
        // Only update if lyrics changed or play state changed
        val (_, currentLine, _) = getCurrentLyricLines()
        if (currentLine != lastLyricLine) {
            lastLyricLine = currentLine
            updateNotification(silent = true)
        }
    }

    private fun updateNotification(silent: Boolean = false) {
        // Only show notification if we have a song loaded
        if (currentSongTitle == null && currentSongId == null) {
            Log.d(TAG, "No song loaded, skipping notification update")
            return
        }

        try {
            val title = currentSongTitle ?: "Now Playing"

            // Get current lyrics for display
            val (_, currentLine, _) = getCurrentLyricLines()
            val subtitle = if (currentLine.isNotEmpty()) currentLine else "Divine Blessing"

            // Create pending intents with proper flags for Android 9+
            val pendingIntentFlags =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }

            val playPauseIntent =
                    Intent(this, MediaPlayerService::class.java).apply {
                        action = ACTION_PLAY_PAUSE
                    }
            val playPausePendingIntent =
                    PendingIntent.getService(this, 1, playPauseIntent, pendingIntentFlags)

            val likeIntent =
                    Intent(this, MediaPlayerService::class.java).apply { action = ACTION_LIKE }
            val likePendingIntent =
                    PendingIntent.getService(this, 2, likeIntent, pendingIntentFlags)

            // Open player page when notification is clicked
            val contentIntent =
                    Intent(this, MainActivity::class.java).apply {
                        putExtra("openPlayer", true)
                        putExtra("songId", currentSongId)
                        putExtra("title", currentSongTitle)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
            val contentPendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            contentIntent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    PendingIntent.FLAG_IMMUTABLE or
                                            PendingIntent.FLAG_UPDATE_CURRENT
                            else PendingIntent.FLAG_UPDATE_CURRENT
                    )

            // Ensure god image is loaded
            loadGodImageIfNeeded()

            // Use standard MediaStyle notification instead of custom RemoteViews
            val notification =
                    NotificationCompat.Builder(this, "media_playback_channel")
                            .setSmallIcon(R.drawable.ic_play_24)
                            .setContentTitle(title)
                            .setContentText(subtitle)
                            .setSubText("Divine Blessing")
                            .setContentIntent(contentPendingIntent)
                            .setLargeIcon(currentGodBitmap)
                            .addAction(
                                    if (isFavorite) R.drawable.ic_heart_filled_24
                                    else R.drawable.ic_heart_24,
                                    "Like",
                                    likePendingIntent
                            )
                            .addAction(
                                    if (isPlaying) R.drawable.ic_pause_24
                                    else R.drawable.ic_play_24,
                                    if (isPlaying) "Pause" else "Play",
                                    playPausePendingIntent
                            )
                            .setStyle(
                                    androidx.media.app.NotificationCompat.MediaStyle()
                                            .setShowActionsInCompactView(0, 1)
                            )
                            .setOngoing(true)
                            .setShowWhen(false)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setCategory(NotificationCompat.CATEGORY_SERVICE)
                            .setOnlyAlertOnce(true) // Only alert once, not on every update
                            .setSilent(silent) // Make updates silent
                            .build()

            startForeground(1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    private fun loadGodImageIfNeeded() {
        val file = currentGodImageFileName ?: return
        if (currentGodBitmap != null) return
        try {
            assets.open("images/$file").use { input ->
                currentGodBitmap = android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load god image: $file", e)
            currentGodBitmap = null
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stop playback when app is removed from recents (as requested)
        try {
            notificationHandler.removeCallbacks(notificationUpdateRunnable)
            mediaPlayer?.release()
            mediaPlayer = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onTaskRemoved", e)
        }
    }

    override fun onDestroy() {
        try {
            notificationHandler.removeCallbacks(notificationUpdateRunnable)
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    fun getCurrentSongTitle(): String? = currentSongTitle
    fun hasLoadedSong(): Boolean =
            (mediaPlayer != null && (currentAudioFileName != null || currentSongId != null))
    fun getCurrentSongId(): String? = currentSongId

    fun setPlaybackSpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                currentSpeed = speed.coerceIn(0.25f, 2.0f)
                val wasPlaying = mediaPlayer?.isPlaying ?: false
                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(currentSpeed)!!
                // Preserve play/pause state - if it was paused, pause it again
                if (!wasPlaying && mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                    isPlaying = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting playback speed", e)
        }
    }

    fun getPlaybackSpeed(): Float = currentSpeed

    fun setGodId(godId: String?) {
        currentGodId = godId
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val app = application as? com.example.divneblessing_v0.DivineApplication
                val god = godId?.let { app?.repository?.getGodById(it) }
                currentGodImageFileName = god?.imageFileName
                loadGodImageIfNeeded()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateNotification(silent = true)
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleLikeAction() {
        val songId = currentSongId ?: return
        Log.d(TAG, "handleLikeAction: songId=$songId, currentFavorite=$isFavorite")
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val app = application as? com.example.divneblessing_v0.DivineApplication
                // Toggle favorite in database
                app?.repository?.toggleFavorite(songId)
                // Read back the new state
                isFavorite = app?.repository?.isFavorite(songId)?.first() ?: false
                Log.d(TAG, "handleLikeAction: newFavorite=$isFavorite")
                // Update notification on main thread
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateNotification(silent = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    fun setLyrics(lyrics: List<com.example.divneblessing_v0.ui.player.LrcLine>) {
        currentLyrics = lyrics
        lastLyricLine = "" // Reset to force update
        updateNotification(silent = true)
    }

    private fun getCurrentLyricLines(): Triple<String, String, String> {
        if (currentLyrics.isEmpty()) return Triple("", "", "")

        val currentPos = getCurrentPosition()
        var currentIndex = -1

        // Find current lyric line
        for (i in currentLyrics.indices) {
            val lineTime = currentLyrics[i].timeMs
            if (lineTime != null && lineTime <= currentPos) {
                currentIndex = i
            } else if (lineTime != null) {
                break
            }
        }

        val prevLine = if (currentIndex > 0) currentLyrics[currentIndex - 1].text else ""
        val currentLine = if (currentIndex >= 0) currentLyrics[currentIndex].text else ""
        val nextLine =
                if (currentIndex >= 0 && currentIndex < currentLyrics.size - 1)
                        currentLyrics[currentIndex + 1].text
                else ""

        return Triple(prevLine, currentLine, nextLine)
    }
}
