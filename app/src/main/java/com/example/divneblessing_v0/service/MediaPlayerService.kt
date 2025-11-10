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
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()
    private var currentSongId: String? = null
    private var currentSongTitle: String? = null
    private var isPlaying = false
    private val TAG = "MediaPlayerService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentAudioFileName: String? = null
    private var currentSpeed: Float = 1.0f
    private var currentGodId: String? = null
    private var currentLyrics: List<com.example.divneblessing_v0.ui.player.LrcLine> = emptyList()
    private var isFavorite: Boolean = false
    private var currentGodBitmap: android.graphics.Bitmap? = null
    private var currentGodImageFileName: String? = null

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private val notificationId: Int = 1
    private val channelId: String = "media_playback_channel"

    private val notificationHandler = Handler(Looper.getMainLooper())
    private var lastLyricLine: String = ""
    private val notificationUpdateRunnable =
            object : Runnable {
                override fun run() {
                    updateNotificationIfNeeded()
                    notificationHandler.postDelayed(this, 100) // Update every 100ms for instant lyrics sync
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

        // ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNew: Boolean) {
                    this@MediaPlayerService.isPlaying = isPlayingNew
                    playerNotificationManager?.invalidate()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        this@MediaPlayerService.isPlaying = false
                        playerNotificationManager?.invalidate()
                    }
                }
            })
        }

        // MediaSession
        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()

        // Notification: description adapter
        val mediaDescriptionAdapter =
            object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence {
                    return currentSongTitle ?: "Divine Blessing"
                }
                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    val intent = Intent(this@MediaPlayerService, com.example.divneblessing_v0.MainActivity::class.java)
                    intent.putExtra("title", currentSongTitle)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    return PendingIntent.getActivity(
                        this@MediaPlayerService, 0, intent,
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
                override fun getCurrentContentText(player: Player): CharSequence? {
                    val (_, currentLine, _) = getCurrentLyricLines()
                    return if (currentLine.isNotEmpty()) currentLine else null
                }
                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): android.graphics.Bitmap? {
                    loadGodImageIfNeeded()
                    return currentGodBitmap
                }
            }

        // Notification: custom "Like" action
        val customActionReceiver =
            object : PlayerNotificationManager.CustomActionReceiver {
                override fun createCustomActions(
                    context: android.content.Context,
                    instanceId: Int
                ): Map<String, NotificationCompat.Action> {
                    val likeIntent = Intent(context, MediaPlayerService::class.java).setAction(ACTION_LIKE)
                    val likePendingIntent = PendingIntent.getService(
                        context,
                        101,
                        likeIntent,
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    
                    // Create colored icon for notification action
                    val likeIcon = if (isFavorite) {
                        // Create a red heart icon using IconCompat with tint
                        val redColor = androidx.core.content.ContextCompat.getColor(context, R.color.red)
                        androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.ic_heart_filled_24)
                            .setTint(redColor)
                    } else {
                        // White outline heart for not favorited
                        androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.ic_heart_24)
                    }
                    
                    // Create the action with colored icon
                    val likeAction = NotificationCompat.Action.Builder(
                        likeIcon,
                        if (isFavorite) "Unlike" else "Like",
                        likePendingIntent
                    ).build()
                    
                    return mapOf(ACTION_LIKE to likeAction)
                }

                override fun getCustomActions(player: Player): List<String> {
                    return listOf(ACTION_LIKE)
                }

                override fun onCustomAction(
                    player: Player,
                    action: String,
                    intent: Intent
                ) {
                    if (action == ACTION_LIKE) {
                        handleLikeAction()
                    }
                }
            }

        // Notification: listener controls startForeground/stopForeground
        val notificationListener =
            object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: android.app.Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_DETACH)
                            } else {
                                @Suppress("DEPRECATION") stopForeground(false)
                            }
                        } catch (_: Exception) {}
                        androidx.core.app.NotificationManagerCompat
                            .from(this@MediaPlayerService)
                            .notify(notificationId, notification)
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION") stopForeground(true)
                        }
                    } catch (_: Exception) {}
                    stopSelf()
                }
            }

        // PlayerNotificationManager setup
        playerNotificationManager =
            PlayerNotificationManager.Builder(this, notificationId, channelId)
                .setMediaDescriptionAdapter(mediaDescriptionAdapter)
                .setNotificationListener(notificationListener)
                .setCustomActionReceiver(customActionReceiver)
                .build().apply {
                    setUseNextAction(false)
                    setUsePreviousAction(false)
                    setUseFastForwardAction(true)
                    setUseRewindAction(true)
                    setSmallIcon(R.mipmap.ic_launcher)
                    setPlayer(exoPlayer)
                }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                // Only start foreground if we have a song
                if (currentSongTitle != null || currentSongId != null) {
                    playerNotificationManager?.invalidate()
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
                    playerNotificationManager?.invalidate()
                    notificationHandler.post(notificationUpdateRunnable)
                }
            }
        }
        // Keep service running across navigation/background until explicitly stopped
        return START_STICKY
    }

    fun loadSong(songId: String, title: String) {
        if (songId == currentSongId) {
            return
        }
        currentSongId = songId
        currentSongTitle = title

        // Seed favorite state once per song
        serviceScope.launch(Dispatchers.IO) {
            try {
                val app = application as? com.example.divneblessing_v0.DivineApplication
                isFavorite = app?.repository?.isFavorite(songId)?.first() ?: false
                withContext(Dispatchers.Main) {
                    playerNotificationManager?.invalidate()
                }
            } catch (_: Exception) {}
        }

        // ExoPlayer: play asset:///audio/<file>
        val uri = android.net.Uri.parse("asset:///audio/$songId")
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(uri)
            .setMediaId(songId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Divine Blessing")
                    .build()
            )
            .build()

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        playerNotificationManager?.invalidate()
        notificationHandler.removeCallbacks(notificationUpdateRunnable)
        notificationHandler.post(notificationUpdateRunnable)
    }
    fun loadSongByFile(audioFileName: String, title: String, songId: String? = null) {
        currentAudioFileName = audioFileName
        if (songId != null) currentSongId = songId
        currentSongTitle = title

        val idForItem = songId ?: audioFileName
        val uri = android.net.Uri.parse("asset:///audio/$audioFileName")
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(uri)
            .setMediaId(idForItem)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Divine Blessing")
                    .build()
            )
            .build()

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
        playerNotificationManager?.invalidate()
        notificationHandler.removeCallbacks(notificationUpdateRunnable)
        notificationHandler.post(notificationUpdateRunnable)
    }

        fun togglePlayPause(): Boolean {
            exoPlayer?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    this.isPlaying = false
                } else {
                    p.play()
                    this.isPlaying = true
                }
                playerNotificationManager?.invalidate()
                return this.isPlaying
            }
            return false
        }

        fun seekTo(position: Int) {
            exoPlayer?.seekTo(position.toLong())
            playerNotificationManager?.invalidate()
        }

        fun getCurrentPosition(): Int {
            return (exoPlayer?.currentPosition ?: 0L).toInt()
        }

        fun getDuration(): Int {
            return (exoPlayer?.duration ?: 0L).toInt()
        }

        fun isPlaying(): Boolean {
            return exoPlayer?.isPlaying ?: false
        }

        fun getCurrentSongId(): String? {
            return currentSongId
        }

        fun getCurrentSongTitle(): String? {
            return currentSongTitle
        }

        fun hasLoadedSong(): Boolean {
            return currentSongId != null
        }

        fun setPlaybackSpeed(speed: Float) {
            currentSpeed = speed
            exoPlayer?.setPlaybackParameters(PlaybackParameters(speed))
        }

        fun setGodId(godId: String) {
            currentGodId = godId
            // Clear cached bitmap so it reloads for the new god
            currentGodBitmap = null
            currentGodImageFileName = null
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
            val (_, currentLine, _) = getCurrentLyricLines()
            if (currentLine != lastLyricLine) {
                lastLyricLine = currentLine
                playerNotificationManager?.invalidate()
            }
        }

        private fun updateNotification() {
            playerNotificationManager?.invalidate()
        }

        fun setLyrics(lyrics: List<com.example.divneblessing_v0.ui.player.LrcLine>) {
            currentLyrics = lyrics
            lastLyricLine = ""
            playerNotificationManager?.invalidate()
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

        private fun handleLikeAction() {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val app = application as? com.example.divneblessing_v0.DivineApplication
                    val songId = currentSongId ?: return@launch
                    
                    app?.repository?.toggleFavorite(songId)
                    isFavorite = app?.repository?.isFavorite(songId)?.first() ?: false
                    
                    withContext(Dispatchers.Main) {
                        playerNotificationManager?.invalidate()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error toggling favorite", e)
                }
            }
        }

        private fun loadGodImageIfNeeded() {
            if (currentGodBitmap != null) return
            
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val app = application as? com.example.divneblessing_v0.DivineApplication
                    val songId = currentSongId ?: return@launch
                    val song = app?.repository?.getSongById(songId) ?: return@launch
                    val god = app.repository.getGodById(song.godId) ?: return@launch
                    
                    val imageFileName = god.imageFileName
                    if (imageFileName.isNotEmpty()) {
                        val bitmap = try {
                            val inputStream = assets.open("images/gods/$imageFileName")
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading god image", e)
                            null
                        }
                        
                        currentGodBitmap = bitmap
                        currentGodImageFileName = imageFileName
                        
                        withContext(Dispatchers.Main) {
                            playerNotificationManager?.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in loadGodImageIfNeeded", e)
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            notificationHandler.removeCallbacks(notificationUpdateRunnable)
            serviceScope.cancel()
            exoPlayer?.release()
            mediaSession?.release()
            playerNotificationManager?.setPlayer(null)
        }
}
