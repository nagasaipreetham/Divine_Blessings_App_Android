package com.example.divneblessing_v0.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.service.MediaPlayerService
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.max
import androidx.navigation.fragment.findNavController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.updateLayoutParams
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.appbar.MaterialToolbar

// Session-persistent counters (reset when app process ends)
object SessionCounters {
    private val map = mutableMapOf<String, Int>()
    fun get(id: String) = map[id] ?: 0
    fun set(id: String, v: Int) { map[id] = kotlin.math.max(0, v) }
    fun reset(id: String) { map[id] = 0 }
    // Reset all in-memory counters when app UI is freshly opened
    fun resetAll() { map.clear() }
}

class SongPlayerFragment : Fragment() {

    private var songId: String = "unknown_song"
    private var titleText: String = "Song"
    private var godId: String = "unknown_god"

    private lateinit var lyricsList: RecyclerView
    private lateinit var adapter: LyricsAdapter
    private lateinit var btnPlay: ImageButton
    private lateinit var seek: SeekBar
    private lateinit var txtElapsed: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtNoSong: TextView
    private lateinit var btnLocate: ImageButton
    private lateinit var txtNoLyrics: TextView

    private lateinit var btnLang: Button
    private lateinit var btnPlus: ImageButton
    private lateinit var btnMinus: ImageButton
    private lateinit var txtCounter: TextView
    private lateinit var btnResetCounter: ImageButton
    private lateinit var btnSpeed: Button

    private var currentLang: Lang = Lang.TELUGU
    private var lines: List<LrcLine> = emptyList()

    private var songDetails: com.example.divneblessing_v0.data.Song? = null

    private var mediaPlayerService: MediaPlayerService? = null
    private var serviceBound = false
    private var hasAudio = false
    private var userSeeking = false
    private var userScrollingLyrics = false
    private var autoCenterEnabled = true
    private var lastCenteredIndex = -1
    private var lastPlaybackMs = 0

    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateProgressAndHighlight()
            // Near real-time updates (~60fps). Adjust if needed for performance.
            ui.postDelayed(this, 16)
        }
    }

    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            serviceBound = true

            // If launched from mini player or args were missing, pull id/title from service
            if (songId == "unknown_song" || songId.isBlank()) {
                mediaPlayerService?.getCurrentSongId()?.let { sid ->
                    songId = sid
                    titleText = mediaPlayerService?.getCurrentSongTitle() ?: titleText
                    (activity as? AppCompatActivity)?.supportActionBar?.title = titleText
                }
            }

            // Fetch details, then load audio and lyrics
            viewLifecycleOwner.lifecycleScope.launch {
                val app = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
                val repo = app.repository
                songDetails = repo.getSongById(songId)

                // After details are available, load audio and lyrics
                setupAudio()
                val langStr = app.getLyricsLanguageForSong(songId)
                currentLang = if (langStr.equals("english", ignoreCase = true)) Lang.ENGLISH else Lang.TELUGU
                loadLyrics(currentLang)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            mediaPlayerService = null
        }
    }

    enum class Lang { TELUGU, ENGLISH }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { b ->
            songId = b.getString("songId") ?: songId
            titleText = b.getString("title") ?: titleText
            godId = b.getString("godId") ?: godId
        }

        // Start the service in foreground so it persists across navigation/background
        try {
            val startIntent = Intent(requireContext(), MediaPlayerService::class.java).apply {
                action = "ACTION_START_FOREGROUND"
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                requireContext().startForegroundService(startIntent)
            } else {
                requireContext().startService(startIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("SongPlayer", "Error starting foreground service: ${e.message}")
        }
        
        // Bind to the MediaPlayerService
        val intent = Intent(requireContext(), MediaPlayerService::class.java)
        try {
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("SongPlayer", "Error binding to service: ${e.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_song_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Hide bottom nav while full player is active (MainActivity also handles this on destination change)
        activity?.findViewById<View>(R.id.bottomNav)?.visibility = View.GONE
        // Remove explicit mini-player hide to avoid conflicts; MainActivity controls its visibility
        // activity?.findViewById<View>(R.id.mini_player_container)?.visibility = View.GONE

        // Top app bar title and back arrow
        (activity as? AppCompatActivity)?.supportActionBar?.title = titleText
        val toolbar = activity?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar?.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(requireContext(), R.drawable.ic_back_24)
        toolbar?.setNavigationOnClickListener { findNavController().navigateUp() }

        // Ensure the NavHost has no bottom margin on the full player (removes extra gap)
        activity?.findViewById<androidx.fragment.app.FragmentContainerView>(R.id.nav_host_fragment)
            ?.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = 0 }

        // Hide the overlay back button in this layout to avoid covering lyrics
        view.findViewById<View>(R.id.btnBack)?.visibility = View.GONE

        lyricsList = view.findViewById(R.id.recycler_lyrics)
        btnPlay = view.findViewById(R.id.btnPlayPause)
        seek = view.findViewById(R.id.seekbar)
        txtElapsed = view.findViewById(R.id.txtElapsed)
        txtTotal = view.findViewById(R.id.txtTotal)
        txtNoSong = view.findViewById(R.id.txtNoSong)
        btnLocate = view.findViewById(R.id.btnLocate)
        txtNoLyrics = view.findViewById(R.id.txtNoLyrics)

        btnLang = view.findViewById(R.id.btnLangToggle)
        btnPlus = view.findViewById(R.id.btnPlusSmall)
        btnMinus = view.findViewById(R.id.btnMinusSmall)
        txtCounter = view.findViewById(R.id.txtSmallCount)
        btnResetCounter = view.findViewById(R.id.btnResetSmall)
        btnSpeed = view.findViewById(R.id.btnSpeed)

        // Initialize speed button
        updateSpeedButton()

        // NEW: Back button
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        // Recycler setup
        lyricsList.layoutManager = LinearLayoutManager(requireContext())
        adapter = LyricsAdapter(emptyList())
        lyricsList.adapter = adapter

        // Pause auto-centering when user scrolls. Do NOT auto-resume later.
        lyricsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> {
                        userScrollingLyrics = true
                        autoCenterEnabled = false // disable until user explicitly re-enables
                    }
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        userScrollingLyrics = false
                        // Do not auto-resume centering here
                    }
                }
            }
        })

        // Initialize lyrics language: per-song override if present, else Profile default language
        val app = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
        val repo = app.repository

        viewLifecycleOwner.lifecycleScope.launch {
            // Fetch DB row containing audio and lyric file names
            songDetails = repo.getSongById(songId)

            // Initialize lyrics language and load after details are available
            val langStr = app.getLyricsLanguageForSong(songId)
            currentLang = if (langStr.equals("english", ignoreCase = true)) Lang.ENGLISH else Lang.TELUGU
            loadLyrics(currentLang)
        }

        // Counter init (per song, session-only)
        loadCounter()
        btnPlus.setOnClickListener {
            val newCount = SessionCounters.get(songId) + 1
            SessionCounters.set(songId, newCount)
            txtCounter.text = newCount.toString()
            saveCounterToDatabase(newCount)
        }
        btnMinus.setOnClickListener {
            val newCount = kotlin.math.max(0, SessionCounters.get(songId) - 1)
            SessionCounters.set(songId, newCount)
            txtCounter.text = newCount.toString()
            saveCounterToDatabase(newCount)
        }
        btnResetCounter.setOnClickListener {
            SessionCounters.reset(songId)
            txtCounter.text = "0"
            saveCounterToDatabase(0)
        }

        // Language toggle (session-only)
        btnLang.setOnClickListener {
            currentLang = if (currentLang == Lang.TELUGU) Lang.ENGLISH else Lang.TELUGU
            val app2 = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
            val chosen = if (currentLang == Lang.ENGLISH) "english" else "telugu"
            app2.setLyricsOverride(songId, chosen)

            val pos = mediaPlayerService?.getCurrentPosition() ?: 0
            autoCenterEnabled = true
            loadLyrics(currentLang)
            highlightForTime(pos)
        }

        // NEW: Play/Pause click handler
        btnPlay.setOnClickListener {
            if (!hasAudio && serviceBound) {
                setupAudio()
            }
            val playing = mediaPlayerService?.togglePlayPause() ?: false
            btnPlay.setImageResource(if (playing) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
        }

        // SeekBar change listener
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) {
                userSeeking = true
            }
            override fun onStopTrackingTouch(sb: SeekBar) {
                val pos = sb.progress
                mediaPlayerService?.seekTo(pos)
                userSeeking = false
                // Re-enable and force-center after any seek (forward/backward)
                autoCenterEnabled = true
                forceCenterForTime(pos)
            }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtElapsed.text = formatMs(progress)
                }
            }
        })

        // Locate current lyric line: force-center now and re-enable auto-centering
        btnLocate.setOnClickListener {
            val pos = mediaPlayerService?.getCurrentPosition() ?: 0
            autoCenterEnabled = true
            forceCenterForTime(pos)
        }

        // Speed control button
        btnSpeed.setOnClickListener {
            showSpeedDialog()
        }

        // Toggle "no lyrics" message
        txtNoLyrics.isVisible = lines.isEmpty()

        // Ensure lyrics visible
        if (lines.isNotEmpty()) {
            lyricsList.post { lyricsList.scrollToPosition(0) }
        }
    }

    private fun setupAudio() {
        hasAudio = false
        txtNoSong.isVisible = false
        btnPlay.isEnabled = true
        seek.isEnabled = true

        if (mediaPlayerService != null) {
            try {
                // Check if the same song is already loaded in the service
                val currentServiceSongId = mediaPlayerService?.getCurrentSongId()
                val isSameSong = currentServiceSongId == songId
                
                // Only load the song if it's different from what's currently playing
                if (!isSameSong) {
                    // Use explicit audio file name from DB; fallback to {songId}.mp3
                    val fileName = songDetails?.audioFileName ?: "${songId}.mp3"
                    mediaPlayerService?.loadSongByFile(fileName, titleText, songId)
                }
                
                hasAudio = true

                ui.postDelayed({
                    try {
                        val duration = mediaPlayerService?.getDuration() ?: 0
                        seek.max = duration
                        txtTotal.text = formatMs(duration)
                        
                        // Get current position instead of resetting to 00:00
                        val currentPos = mediaPlayerService?.getCurrentPosition() ?: 0
                        txtElapsed.text = formatMs(currentPos)
                        
                        val isPlaying = mediaPlayerService?.isPlaying() ?: false
                        btnPlay.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
                        
                        // Apply saved speed for this song (only if not already set)
                        if (!isSameSong) {
                            val savedSpeed = SpeedManager.getSpeed(songId)
                            mediaPlayerService?.setPlaybackSpeed(savedSpeed)
                        }
                        updateSpeedButton()
                        
                        // Set god ID and lyrics for notification
                        mediaPlayerService?.setGodId(godId)
                        mediaPlayerService?.setLyrics(lines)
                    } catch (e: Exception) {
                        android.util.Log.e("SongPlayer", "Error setting up UI after delay: ${e.message}")
                    }
                }, 500)
            } catch (e: Exception) {
                android.util.Log.e("SongPlayer", "Error loading song: ${e.message}")
                hasAudio = false
                btnPlay.isEnabled = false
                seek.isEnabled = false
                txtNoSong.isVisible = true
            }
        } else {
            hasAudio = false
            btnPlay.isEnabled = false
            seek.isEnabled = false
            txtNoSong.isVisible = true
        }
    }

    // Keep the mapping consistent:
    // - If currentLang == TELUGU (showing Telugu) => toggle shows "A"
    // - If currentLang == ENGLISH (showing English) => toggle shows "అ"
    private fun loadLyrics(lang: Lang) {
        currentLang = lang
        // Set button to show the target language (opposite of current)
        btnLang.text = if (lang == Lang.TELUGU) "A" else "అ"

        val langStr = if (lang == Lang.TELUGU) "telugu" else "english"
        val app = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
        val repo = app.repository

        viewLifecycleOwner.lifecycleScope.launch {
            var parsedLines: List<LrcLine>? = null

            // 1) Try DB first using multiple keys: songId, explicit lyrics basename, audio basename
            try {
                val candidates = mutableListOf<String>()
                candidates.add(songId)

                val explicitFile = if (lang == Lang.TELUGU) songDetails?.lyricsTeluguFileName else songDetails?.lyricsEnglishFileName
                val explicitBase = explicitFile
                    ?.substringBeforeLast(".")
                    ?.substringBeforeLast("_te")
                    ?.substringBeforeLast("_en")
                if (!explicitBase.isNullOrBlank()) candidates.add(explicitBase)

                val audioBase = songDetails?.audioFileName?.substringBeforeLast(".")
                if (!audioBase.isNullOrBlank()) candidates.add(audioBase)

                for (id in candidates) {
                    parsedLines = repo.getLyricsLines(id, langStr)
                    if (!parsedLines.isNullOrEmpty()) break
                }
            } catch (_: Exception) {}

            // 2) Fallback to assets using explicit file name first, then conventional patterns
            if (parsedLines.isNullOrEmpty()) {
                fun tryOpen(path: String): List<LrcLine>? {
                    return runCatching {
                        requireContext().assets.open(path).use { input ->
                            java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8)).use { br ->
                                val raw = br.readLines()
                                LrcParser.parse(raw)
                            }
                        }
                    }.getOrNull()
                }

                val langFolder = if (lang == Lang.TELUGU) "telugu" else "english"
                val explicitFile = if (lang == Lang.TELUGU) songDetails?.lyricsTeluguFileName else songDetails?.lyricsEnglishFileName
                val code = if (lang == Lang.TELUGU) "te" else "en"

                val paths = mutableListOf<String>()
                if (!explicitFile.isNullOrBlank()) paths.add("lyrics/$langFolder/$explicitFile")
                paths.add("lyrics/$langFolder/${songId}_${code}.lrc")
                paths.add("lyrics/${songId}_${code}.lrc")
                val otherFolder = if (lang == Lang.TELUGU) "english" else "telugu"
                val otherCode = if (lang == Lang.TELUGU) "en" else "te"
                paths.add("lyrics/$otherFolder/${songId}_${otherCode}.lrc")
                paths.add("lyrics/${songId}_${otherCode}.lrc")

                for (p in paths) {
                    android.util.Log.d("SongPlayer", "Trying lyrics path: $p")
                    parsedLines = tryOpen(p)
                    if (parsedLines != null) break
                }
            }

            // Update UI on the main thread
            lines = (parsedLines ?: emptyList()).dropWhile { it.text.isBlank() }
            android.util.Log.d("SongPlayer", "Parsed lyrics lines: ${lines.size}")
            adapter.submit(lines)

            // Update lyrics in notification
            mediaPlayerService?.setLyrics(lines)

            // Re-enable and immediately sync highlight + center
            autoCenterEnabled = true
            val curPos = mediaPlayerService?.getCurrentPosition() ?: 0
            highlightForTime(curPos)

            txtNoLyrics.isVisible = lines.isEmpty()
            if (lines.isNotEmpty()) {
                lyricsList.post { lyricsList.scrollToPosition(0) }
            }
        }
    }

    private fun highlightForTime(ms: Int) {
        val idx = adapter.highlightFor(ms) ?: return

        // Only auto-center when explicitly enabled and not during user scroll
        val canAutoCenter = autoCenterEnabled && !userScrollingLyrics
        if (!canAutoCenter) return

        val lm = lyricsList.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val visibleCount = if (first >= 0 && last >= first) last - first + 1 else 0
        val middleIdx = if (visibleCount > 0) first + visibleCount / 2 else -1

        // Target a slightly above-middle position (adaptive to screen size)
        val targetRatio = 0.42f

        // Center when highlighted line reaches/passes the middle area
        if (middleIdx == -1 || idx >= middleIdx) {
            val view = lm.findViewByPosition(idx)
            val offset = if (view != null) {
                ((lyricsList.height * targetRatio).toInt()) - (view.height / 2)
            } else {
                ((lyricsList.height * targetRatio).toInt())
            }
            lm.scrollToPositionWithOffset(idx, offset)
            lastCenteredIndex = idx
        }
    }

    // Force-center regardless of user-scrolling state (used by Seek and Locate)
    private fun forceCenterForTime(ms: Int) {
        val idx = adapter.indexForTime(ms)
        if (idx < 0) return
        val lm = lyricsList.layoutManager as LinearLayoutManager
        val view = lm.findViewByPosition(idx)
        val targetRatio = 0.42f
        val offset = if (view != null) {
            ((lyricsList.height * targetRatio).toInt()) - (view.height / 2)
        } else {
            ((lyricsList.height * targetRatio).toInt())
        }
        lm.scrollToPositionWithOffset(idx, offset)
        lastCenteredIndex = idx
    }

    private fun updateProgressAndHighlight() {
        if (hasAudio && mediaPlayerService != null) {
            try {
                val cur = mediaPlayerService?.getCurrentPosition() ?: 0
                txtElapsed.text = formatMs(cur)
                if (!userSeeking) {
                    seek.progress = cur
                }

                // Highlight on every tick (16ms interval)
                highlightForTime(cur)

                // If there is a large jump (forward or backward), force-center when enabled
                val jump = kotlin.math.abs(cur - lastPlaybackMs)
                if (jump > 800 && autoCenterEnabled && !userScrollingLyrics) {
                    forceCenterForTime(cur)
                }
                lastPlaybackMs = cur

                // Update play/pause button state
                val isPlaying = mediaPlayerService?.isPlaying() ?: false
                btnPlay.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
            } catch (e: Exception) {
                android.util.Log.e("SongPlayer", "Error updating UI: ${e.message}")
            }
        }
    }

    private fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun loadCounter() {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            val count = repository.getSongCounter(songId)
            SessionCounters.set(songId, count)
            txtCounter.text = count.toString()
        }
    }

    private fun saveCounterToDatabase(count: Int) {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateSongCounter(songId, count)
        }
    }

    private fun showSpeedDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_speed_control, null)
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val txtSpeedValue = dialogView.findViewById<TextView>(R.id.txtSpeedValue)
        val seekBarSpeed = dialogView.findViewById<SeekBar>(R.id.seekBarSpeed)
        val btnSpeedMinus = dialogView.findViewById<ImageButton>(R.id.btnSpeedMinus)
        val btnSpeedPlus = dialogView.findViewById<ImageButton>(R.id.btnSpeedPlus)
        val btnResetSpeed = dialogView.findViewById<Button>(R.id.btnResetSpeed)

        // Get current speed for this song
        val currentSpeed = SpeedManager.getSpeed(songId)
        val currentIndex = SpeedManager.speedToIndex(currentSpeed)
        
        seekBarSpeed.max = SpeedManager.SPEED_OPTIONS.size - 1
        seekBarSpeed.progress = currentIndex
        txtSpeedValue.text = SpeedManager.formatSpeed(currentSpeed)

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = SpeedManager.indexToSpeed(progress)
                txtSpeedValue.text = SpeedManager.formatSpeed(speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = SpeedManager.indexToSpeed(seekBar?.progress ?: 3)
                applySpeed(speed)
            }
        })

        btnSpeedMinus.setOnClickListener {
            val newProgress = (seekBarSpeed.progress - 1).coerceAtLeast(0)
            seekBarSpeed.progress = newProgress
            val speed = SpeedManager.indexToSpeed(newProgress)
            applySpeed(speed)
        }

        btnSpeedPlus.setOnClickListener {
            val newProgress = (seekBarSpeed.progress + 1).coerceAtMost(seekBarSpeed.max)
            seekBarSpeed.progress = newProgress
            val speed = SpeedManager.indexToSpeed(newProgress)
            applySpeed(speed)
        }

        btnResetSpeed.setOnClickListener {
            seekBarSpeed.progress = SpeedManager.speedToIndex(1.0f)
            applySpeed(1.0f)
        }

        // Set width before showing to avoid flash
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.show()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun applySpeed(speed: Float) {
        SpeedManager.setSpeed(songId, speed)
        mediaPlayerService?.setPlaybackSpeed(speed)
        updateSpeedButton()
    }

    private fun updateSpeedButton() {
        val speed = SpeedManager.getSpeed(songId)
        btnSpeed.text = SpeedManager.formatSpeed(speed)
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(ticker)
        // Don't pause the player when navigating away - it will continue in the service
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.findViewById<View>(R.id.bottomNav)?.visibility = View.VISIBLE

        val toolbar = activity?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        toolbar?.navigationIcon = null
        toolbar?.setNavigationOnClickListener(null)

        ui.removeCallbacks(ticker)
        // Don't release the player here, it's managed by the service
        // MainActivity will decide to show mini player based on service state
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unbind from the service, but don't stop it
        if (serviceBound) {
            requireActivity().unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
