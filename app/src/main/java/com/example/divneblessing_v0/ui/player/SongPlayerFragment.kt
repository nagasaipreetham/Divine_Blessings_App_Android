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
    fun set(id: String, v: Int) { map[id] = max(0, v) }
    fun reset(id: String) { map[id] = 0 }
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

    private var currentLang: Lang = Lang.TELUGU
    private var lines: List<LrcLine> = emptyList()

    // Service related variables
    private var mediaPlayerService: MediaPlayerService? = null
    private var serviceBound = false
    private var hasAudio = false
    
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateProgressAndHighlight()
            ui.postDelayed(this, 250) // Reduced update frequency from 100ms to 250ms to reduce lag
        }
    }

    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            serviceBound = true
            
            // Load the song in the service
            setupAudio()
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

        // NEW: Back button
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        // Recycler setup
        lyricsList.layoutManager = LinearLayoutManager(requireContext())
        adapter = LyricsAdapter(emptyList())
        lyricsList.adapter = adapter

        // Removed: insets padding that created bottom gap
        // (previous ViewCompat.setOnApplyWindowInsetsListener(bottomBar) block removed)

        // Initialize lyrics language from session (falls back to app default)
        val app = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
        val sessionLang = app.getCurrentLyricsLanguageOrDefault()
        currentLang = if (sessionLang.equals("english", ignoreCase = true)) Lang.ENGLISH else Lang.TELUGU
        // Do not set btnLang.text here; loadLyrics() will set the correct label consistently
        loadLyrics(currentLang)

        // Counter init (per song, session-only)
        loadCounter()
        btnPlus.setOnClickListener {
            val newCount = SessionCounters.get(songId) + 1
            SessionCounters.set(songId, newCount)
            txtCounter.text = newCount.toString()
            saveCounterToDatabase(newCount)
        }
        btnMinus.setOnClickListener {
            val newCount = max(0, SessionCounters.get(songId) - 1)
            SessionCounters.set(songId, newCount)
            txtCounter.text = newCount.toString()
            saveCounterToDatabase(newCount)
        }
        btnResetCounter.setOnClickListener {
            SessionCounters.reset(songId)
            txtCounter.text = "0"
            saveCounterToDatabase(0)
        }

        // Language toggle
        btnLang.setOnClickListener {
            currentLang = if (currentLang == Lang.TELUGU) Lang.ENGLISH else Lang.TELUGU
            // Persist only for this app session (not global setting)
            val app2 = (requireActivity().application as com.example.divneblessing_v0.DivineApplication)
            app2.setCurrentLyricsLanguage(if (currentLang == Lang.ENGLISH) "english" else "telugu")
            val pos = mediaPlayerService?.getCurrentPosition() ?: 0
            loadLyrics(currentLang)
            // keep the highlight roughly in place based on current playback position
            highlightForTime(pos)
        }

        // Toggle "no lyrics" message
        txtNoLyrics.isVisible = lines.isEmpty()

        // Ensure lyrics visible
        if (lines.isNotEmpty()) {
            lyricsList.post { lyricsList.scrollToPosition(0) }
        }
    }

    private fun setupAudio() {
        // Try to load the song in the service
        hasAudio = false
        txtNoSong.isVisible = false
        btnPlay.isEnabled = true
        seek.isEnabled = true

        if (mediaPlayerService != null) {
            try {
                mediaPlayerService?.loadSong(songId, titleText)
                hasAudio = true
                
                // Add a small delay to ensure the player is prepared
                ui.postDelayed({
                    try {
                        val duration = mediaPlayerService?.getDuration() ?: 0
                        seek.max = duration
                        txtTotal.text = formatMs(duration)
                        txtElapsed.text = "00:00"
                        
                        val isPlaying = mediaPlayerService?.isPlaying() ?: false
                        btnPlay.setImageResource(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
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

        val langFolder = if (lang == Lang.TELUGU) "telugu" else "english"
        val code = if (lang == Lang.TELUGU) "te" else "en"

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

        // Try multiple paths: new format with folder, old format, then cross-language fallback
        val candidates = listOf(
            "lyrics/$langFolder/${songId}_${code}.lrc",
            "lyrics/${songId}_${code}.lrc"
        ) + run {
            val otherLang = if (lang == Lang.TELUGU) Lang.ENGLISH else Lang.TELUGU
            val otherFolder = if (otherLang == Lang.TELUGU) "telugu" else "english"
            val otherCode = if (otherLang == Lang.TELUGU) "te" else "en"
            listOf(
                "lyrics/$otherFolder/${songId}_${otherCode}.lrc",
                "lyrics/${songId}_${otherCode}.lrc"
            )
        }

        var parsed: List<LrcLine>? = null
        for (p in candidates) {
            android.util.Log.d("SongPlayer", "Trying lyrics path: $p")
            parsed = tryOpen(p)
            if (parsed != null) break
        }

        lines = parsed ?: emptyList()
        android.util.Log.d("SongPlayer", "Parsed lyrics lines: ${lines.size}")
        adapter.submit(lines)

        // Toggle "no lyrics" message
        txtNoLyrics.isVisible = lines.isEmpty()

        // Ensure lyrics visible
        if (lines.isNotEmpty()) {
            lyricsList.post { lyricsList.scrollToPosition(0) }
        }
    }

    private fun highlightForTime(ms: Int) {
        adapter.highlightFor(ms)?.let { idx ->
            // Keep highlighted item in view if it's far off-screen
            val lm = lyricsList.layoutManager as LinearLayoutManager
            val first = lm.findFirstVisibleItemPosition()
            val last = lm.findLastVisibleItemPosition()

            // Auto-scroll to keep highlighted line in center view
            if (idx < first || idx > last) {
                lm.scrollToPositionWithOffset(idx, (lyricsList.height * 0.4).toInt())
            } else if (idx == first || idx == last) {
                // Smooth scroll to keep highlighted line in better position
                lm.scrollToPositionWithOffset(idx, (lyricsList.height * 0.4).toInt())
            }
        }
    }

    private fun updateProgressAndHighlight() {
        if (hasAudio && mediaPlayerService != null) {
            try {
                val cur = mediaPlayerService?.getCurrentPosition() ?: 0
                txtElapsed.text = formatMs(cur)
                seek.progress = cur
                
                // Only update highlight every other time to reduce UI load
                if (System.currentTimeMillis() % 500 < 250) {
                    highlightForTime(cur)
                }
                
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
