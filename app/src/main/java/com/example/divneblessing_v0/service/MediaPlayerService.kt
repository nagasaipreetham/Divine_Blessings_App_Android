package com.example.divneblessing_v0.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.divneblessing_v0.MainActivity
import com.example.divneblessing_v0.R

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()
    private var currentSongId: String? = null
    private var currentSongTitle: String? = null
    private var isPlaying = false
    private val TAG = "MediaPlayerService"

    companion object {
        const val ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND"
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
                updateNotification()
            }
            "PLAY" -> togglePlayPause()
            "STOP" -> {
                try { stopForeground(true) } catch (_: Exception) {}
                stopSelf()
            }
            else -> {
                updateNotification()
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
        
        try {
            // Release previous player if exists
            mediaPlayer?.release()
            mediaPlayer = null
            
            // Create new player
            mediaPlayer = MediaPlayer()
            
            // Use try-with-resources to ensure AssetFileDescriptor is closed properly
            val afd: AssetFileDescriptor? = try {
                assets.openFd("audio/$songId.mp3")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open audio file: audio/$songId.mp3", e)
                null
            }
            
            if (afd != null) {
                try {
                    mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close() // Close the descriptor after setting data source
                    
                    mediaPlayer?.setOnPreparedListener {
                        Log.d(TAG, "MediaPlayer prepared successfully")
                        // Start the service in foreground immediately when prepared
                        updateNotification()
                        
                        // Auto-start playback when prepared
                        mediaPlayer?.start()
                        isPlaying = true
                        updateNotification()
                    }
                    
                    mediaPlayer?.setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        false
                    }
                    
                    mediaPlayer?.setOnCompletionListener {
                        isPlaying = false
                        updateNotification()
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
                updateNotification()
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
            val channel = NotificationChannel(
                "media_playback_channel",
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        try {
            val playPauseIcon = if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
            
            val playPauseIntent = Intent(this, MediaPlayerService::class.java).apply {
                action = "PLAY"
            }
            val playPausePendingIntent = PendingIntent.getService(
                this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val contentIntent = Intent(this, MainActivity::class.java)
            val contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            val title = currentSongTitle ?: "Now Playing"
            val notification = NotificationCompat.Builder(this, "media_playback_channel")
                .setContentTitle(title)
                .setContentText(if (isPlaying) "Playing" else "Paused")
                .setSmallIcon(R.drawable.ic_play_24)
                .addAction(playPauseIcon, "Play/Pause", playPausePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .build()
                
            startForeground(1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    // Stop playback when app is removed from recents (as requested)
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onTaskRemoved", e)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    fun getCurrentSongTitle(): String? = currentSongTitle
    fun hasLoadedSong(): Boolean = (mediaPlayer != null && currentSongId != null)
    fun getCurrentSongId(): String? = currentSongId
}
