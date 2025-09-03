package com.example.divneblessing_v0

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.divneblessing_v0.databinding.ActivityMainBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.view.WindowManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Mini player views
    private var miniContainer: View? = null
    private var miniTitle: android.widget.TextView? = null
    private var miniSeek: android.widget.SeekBar? = null
    private var miniLike: android.widget.ImageButton? = null
    private var miniPlay: android.widget.ImageButton? = null
    private var miniElapsed: android.widget.TextView? = null
    private var miniTotal: android.widget.TextView? = null

    // Service binding for mini player
    private var mediaPlayerService: com.example.divneblessing_v0.service.MediaPlayerService? = null
    private var serviceBound = false
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            val b = binder as com.example.divneblessing_v0.service.MediaPlayerService.LocalBinder
            mediaPlayerService = b.getService()
            serviceBound = true
            updateMiniVisibility()
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            serviceBound = false
            mediaPlayerService = null
            updateMiniVisibility()
        }
    }

    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var userSeeking = false
    private val ticker = object : Runnable {
        override fun run() {
            try {
                updateMiniUI()
            } finally {
                ui.postDelayed(this, 500)
            }
        }
    }

    private fun applySavedTheme() {
        // Read the same SharedPreferences and keys ProfileFragment uses
        val prefs = getSharedPreferences("divine_settings", MODE_PRIVATE)
        val themeMode = prefs.getString("theme_mode", "system") ?: "system"
        val accent = prefs.getString("accent_color", "blue") ?: "blue"

        // Apply accent theme style first
        val styleRes = when (accent) {
            "green" -> R.style.Theme_divneblessing_v0_Green
            "purple" -> R.style.Theme_divneblessing_v0_Purple
            "orange" -> R.style.Theme_divneblessing_v0_Orange
            "red" -> R.style.Theme_divneblessing_v0_Red
            else -> R.style.Theme_divneblessing_v0_Blue
        }
        setTheme(styleRes)

        // Then apply dark/light/system mode
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply saved theme before setting content view
        applySavedTheme()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep bottom navigation fixed; let IME overlay instead of shifting layout
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        // Ensure content isn't hidden by the keyboard: apply bottom padding to content area when IME is visible
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = imeInsets.bottom // > 0 only when keyboard is visible
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottomPadding)
            insets
        }

        // Add window insets padding to the root 'main' view
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // Resolve mini player views
        miniContainer = findViewById(R.id.mini_player_container)
        miniTitle = findViewById(R.id.mini_player_title)
        miniSeek = findViewById(R.id.mini_player_seekbar)
        miniLike = findViewById(R.id.mini_player_like)
        miniPlay = findViewById(R.id.mini_player_play)
        miniElapsed = findViewById(R.id.mini_player_elapsed)
        miniTotal = findViewById(R.id.mini_player_total)

        // Ensure mini player draws above BottomNavigationView
        miniContainer?.elevation = (binding.bottomNav.elevation.takeIf { it != 0f } ?: 8f) + 1f

        // Bind to service (lifetime of activity)
        try {
            val intent = android.content.Intent(this, com.example.divneblessing_v0.service.MediaPlayerService::class.java)
            bindService(intent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error binding MediaPlayerService: ${e.message}")
        }

        // Wire mini player controls
        miniPlay?.setOnClickListener {
            try {
                val playing = mediaPlayerService?.togglePlayPause() ?: false
                miniPlay?.setImageResource(if (playing) R.drawable.ic_pause_24 else R.drawable.ic_play_24)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Play/Pause error: ${e.message}")
            }
        }
        miniLike?.setOnClickListener {
            val svc = mediaPlayerService ?: return@setOnClickListener
            val sid = svc.getCurrentSongId() ?: return@setOnClickListener
            val repo = (application as DivineApplication).repository
            lifecycleScope.launch {
                try {
                    // Make it atomic and consistent to avoid double-tap/race feel
                    repo.toggleFavorite(sid)
                    // Re-read latest state and apply tint once
                    val nowFav = repo.isFavorite(sid).first()
                    val red = androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.red)
                    val white = androidx.core.content.ContextCompat.getColor(this@MainActivity, android.R.color.white)
                    miniLike?.imageTintList = android.content.res.ColorStateList.valueOf(if (nowFav) red else white)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Favorite toggle error: ${e.message}")
                }
            }
        }
        miniSeek?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                userSeeking = false
                val pos = seekBar?.progress ?: 0
                try { mediaPlayerService?.seekTo(pos) } catch (_: Exception) {}
            }
        })

        // NEW: Open full player when tapping the collapsed player
        miniContainer?.setOnClickListener {
            val svc = mediaPlayerService ?: return@setOnClickListener
            if (!svc.hasLoadedSong()) return@setOnClickListener
            // Avoid re-navigating if we're already on the full player
            if (isOnFullPlayer()) return@setOnClickListener
            val bundle = android.os.Bundle().apply {
                putString("songId", svc.getCurrentSongId())
                putString("title", svc.getCurrentSongTitle())
            }
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHostFragment.navController.navigate(R.id.songPlayerFragment, bundle)
        }

        // Use a single NavHostFragment/NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Top bar title follows destination label
        setSupportActionBar(binding.topAppBar)

        // Keep and use the already-declared navController above
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val title = destination.label?.toString().orEmpty()
            binding.topAppBar.title = title
            supportActionBar?.title = title

            // Hide bottom nav on full player, show otherwise
            val onPlayer = destination.id == R.id.songPlayerFragment
            binding.bottomNav.visibility = if (onPlayer) View.GONE else View.VISIBLE

            // Refresh mini player visibility whenever destination changes
            // Shows when: service has a song loaded or is playing, and we are NOT on the full player
            updateMiniVisibility()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == navController.currentDestination?.id) return@setOnItemSelectedListener true

            // Make nav between tabs instantaneous (no animation = snappy feel)
            val options = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.startDestinationId, false)
                .setEnterAnim(0)
                .setExitAnim(0)
                .setPopEnterAnim(0)
                .setPopExitAnim(0)
                .build()

            return@setOnItemSelectedListener try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (_: IllegalArgumentException) {
                navController.navigate(item.itemId, null)
                true
            }
        }

        binding.bottomNav.setOnItemReselectedListener { item ->
            // Pop back to the root of the reselected destination
            val root = when (item.itemId) {
                R.id.homeFragment -> R.id.homeFragment
                R.id.favoritesFragment -> R.id.favoritesFragment
                R.id.searchFragment -> R.id.searchFragment
                R.id.counterFragment -> R.id.counterFragment
                R.id.profileFragment -> R.id.profileFragment
                else -> navController.graph.startDestinationId
            }
            navController.popBackStack(root, false)
        }

        // Hide bottom nav on full player to allow the timeline to hug the system navigator
        val onPlayer = isOnFullPlayer()
        binding.bottomNav.visibility = if (onPlayer) View.GONE else View.VISIBLE

        updateMiniVisibility() // also re-applies margins based on visibility

        // Start periodic updates for mini player UI & visibility
        ui.post(ticker)
    }

    private fun updateMiniUI() {
        val svc = mediaPlayerService ?: return
        if (!svc.hasLoadedSong() && !svc.isPlaying()) {
            setMiniVisible(false)
            return
        }
        if (miniContainer?.visibility == View.VISIBLE) {
            miniTitle?.text = svc.getCurrentSongTitle() ?: "Now Playing"
            val playing = svc.isPlaying()
            miniPlay?.setImageResource(if (playing) R.drawable.ic_pause_24 else R.drawable.ic_play_24)

            // Also set like tint based on favorite state of current song
            val sid = svc.getCurrentSongId()
            if (sid != null) {
                lifecycleScope.launch {
                    try {
                        val isFav = (application as DivineApplication).repository.isFavorite(sid).first()
                        val red = androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.red)
                        val white = androidx.core.content.ContextCompat.getColor(this@MainActivity, android.R.color.white)
                        miniLike?.imageTintList = android.content.res.ColorStateList.valueOf(if (isFav) red else white)
                    } catch (_: Exception) { }
                }
            }

            if (!userSeeking) {
                val dur = svc.getDuration()
                val pos = svc.getCurrentPosition()
                if (dur > 0) {
                    miniSeek?.max = dur
                    miniSeek?.progress = pos.coerceIn(0, dur)
                    miniTotal?.text = formatMs(dur)
                    miniElapsed?.text = formatMs(pos)
                }
            }
        }
        updateMiniVisibility()
    }

    private fun formatMs(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun updateMiniVisibility() {
        val svc = mediaPlayerService
        val shouldShow = svc != null && (svc.hasLoadedSong() || svc.isPlaying()) && !isOnFullPlayer()
        setMiniVisible(shouldShow)
        adjustNavHostBottomMargin(shouldShow)
    }

    private fun setMiniVisible(visible: Boolean) {
        miniContainer?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun adjustNavHostBottomMargin(miniVisible: Boolean) {
        val params = binding.navHostFragment.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams

        // Base = bottom nav height (if visible) else 0 (for full player)
        val bottomNavVisible = binding.bottomNav.visibility == View.VISIBLE
        val basePx = if (bottomNavVisible) {
            val h = binding.bottomNav.height
            if (h > 0) h else android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 80f, resources.displayMetrics
            ).toInt()
        } else 0

        val extra = if (miniVisible) (miniContainer?.height ?: 0) else 0
        params.bottomMargin = basePx + extra
        binding.navHostFragment.layoutParams = params

        // Position the mini player ABOVE the bottom nav when both are visible
        val miniParams = (miniContainer?.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)
        if (miniParams != null) {
            miniParams.bottomMargin = if (bottomNavVisible) basePx else 0
            miniContainer?.layoutParams = miniParams
        }
    }

    private fun isOnFullPlayer(): Boolean {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val current = navHost?.childFragmentManager?.primaryNavigationFragment
        return current is com.example.divneblessing_v0.ui.player.SongPlayerFragment
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(ticker)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
