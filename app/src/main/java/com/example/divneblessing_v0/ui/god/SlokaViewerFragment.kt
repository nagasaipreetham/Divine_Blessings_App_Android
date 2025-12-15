package com.example.divneblessing_v0.ui.god

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.Sloka
import com.example.divneblessing_v0.service.MediaPlayerService
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class SlokaViewerFragment : Fragment() {

    private var slokaId: String = "unknown"
    private var titleText: String = "Sloka"
    private var godId: String = "unknown"
    private var slokaEntity: Sloka? = null
    private var currentLang = "telugu" // "telugu" or "english"

    private lateinit var txtContent: TextView
    private lateinit var btnLang: Button
    private lateinit var txtCounter: TextView
    
    private var currentCount = 0

    private var mediaPlayerService: MediaPlayerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            serviceBound = true
            
            // Auto-pause audio when entering Sloka Viewer
            if (mediaPlayerService?.isPlaying() == true) {
                mediaPlayerService?.pause()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            mediaPlayerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            slokaId = it.getString("slokaId") ?: slokaId
            titleText = it.getString("title") ?: titleText
            godId = it.getString("godId") ?: godId
        }

        // Bind to service to control playback
        try {
            val intent = Intent(requireContext(), MediaPlayerService::class.java)
            requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            android.util.Log.e("SlokaViewer", "Error binding service", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sloka_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set Toolbar Title and Back Button
        (activity as? AppCompatActivity)?.supportActionBar?.let { actionBar ->
            actionBar.title = titleText
            actionBar.setDisplayHomeAsUpEnabled(true)
            
            // If strict control needed over toolbar icon (e.g. ensure white arrow)
            val toolbar = activity?.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
            toolbar?.setNavigationOnClickListener { findNavController().navigateUp() }
        }

        txtContent = view.findViewById(R.id.sloka_text)
        btnLang = view.findViewById(R.id.btnLangToggle)
        txtCounter = view.findViewById(R.id.txtSmallCount)
        
        val btnPlus = view.findViewById<ImageButton>(R.id.btnPlusSmall)
        val btnMinus = view.findViewById<ImageButton>(R.id.btnMinusSmall)
        val btnReset = view.findViewById<ImageButton>(R.id.btnResetSmall)

        val app = (requireActivity().application as DivineApplication)
        val repository = app.repository

        // Load Sloka Details
        viewLifecycleOwner.lifecycleScope.launch {
            slokaEntity = repository.getSlokaById(slokaId)
            
            // Initialize language from App Session (User pref or App default)
            val sessionLang = app.getSlokaSessionLanguage()
            currentLang = sessionLang
            
            updateLangButton()
            loadContent()
            
            // Load Counter
            val count = repository.getSlokaCounter(slokaId)
            currentCount = count
            updateCounterUI()
        }

        // Language Toggle
        btnLang.setOnClickListener {
            toggleLanguage()
            // Persist choice to session
            app.setSlokaSessionLanguage(currentLang)
        }

        // Counter Logic
        btnPlus.setOnClickListener {
            updateCount(currentCount + 1)
        }

        btnMinus.setOnClickListener {
            if (currentCount > 0) {
                updateCount(currentCount - 1)
            }
        }

        btnReset.setOnClickListener {
            updateCount(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            requireActivity().unbindService(serviceConnection)
            serviceBound = false
        }
        // Reset toolbar state if needed by other fragments
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    private fun toggleLanguage() {
        currentLang = if (currentLang == "telugu") "english" else "telugu"
        updateLangButton()
        loadContent()
    }

    private fun updateLangButton() {
        // Toggle text: Show 'A' (English) when in Telugu mode, 'అ' (Telugu) when in English mode
        // mirroring the player logic style if desired.
        // Or simply text representation. The layout uses "అ" as default.
        btnLang.text = if (currentLang == "telugu") "A" else "అ"
    }

    private fun loadContent() {
        val entity = slokaEntity ?: return
        val filename = if (currentLang == "telugu") entity.scriptTeluguFileName else entity.scriptEnglishFileName
        
        if (filename.isNullOrEmpty()) {
            txtContent.text = "Content not available in $currentLang"
            return
        }

        // Try standard paths:
        // 1. Exact path if it contains /
        // 2. slokas/telugu/filename
        // 3. slokas/english/filename
        // 4. slokas/filename
        
        val folder = if (currentLang == "telugu") "telugu" else "english"
        val pathsToTry = listOf(
            if (filename.contains("/")) filename else null,
            "slokas/$folder/$filename",
            "slokas/$filename"
        ).filterNotNull()

        var loadedText: String? = null
        for (path in pathsToTry) {
            try {
                requireContext().assets.open(path).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        loadedText = reader.readText()
                    }
                }
                if (loadedText != null) break
            } catch (e: Exception) {
                // Ignore and try next
            }
        }

        if (loadedText != null) {
            txtContent.text = loadedText
        } else {
            txtContent.text = "Error loading content: $filename"
            android.util.Log.e("SlokaViewer", "Could not find file: $filename in paths: $pathsToTry")
        }
    }

    private fun updateCount(newCount: Int) {
        currentCount = newCount
        updateCounterUI()
        
        // Save to DB
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = (requireActivity().application as DivineApplication).repository
            repository.updateSlokaCounter(slokaId, newCount)
        }
    }

    private fun updateCounterUI() {
        txtCounter.text = currentCount.toString()
    }
}
