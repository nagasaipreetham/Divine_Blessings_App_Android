package com.example.divneblessing_v0.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var userNameEditText: EditText
    private lateinit var themeSpinner: Spinner
    private lateinit var accentColorSpinner: Spinner
    private lateinit var languageSpinner: Spinner
    private lateinit var saveButton: Button

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveProfileImage(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "User"

        // Ensure views and spinners are set up and data is loaded
        initializeViews(view)
        setupSpinners()
        loadUserSettings()
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
        userNameEditText = view.findViewById(R.id.userNameEditText)
        themeSpinner = view.findViewById(R.id.themeSpinner)
        accentColorSpinner = view.findViewById(R.id.accentColorSpinner)
        languageSpinner = view.findViewById(R.id.languageSpinner)
        saveButton = view.findViewById(R.id.saveButton)
    }

    private fun setupSpinners() {
        // Theme spinner
        val themes = arrayOf("System", "Light", "Dark")
        val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter

        // Accent color spinner
        val colors = arrayOf("Blue", "Green", "Purple", "Orange", "Red")
        val colorAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, colors)
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accentColorSpinner.adapter = colorAdapter

        // Language spinner
        val languages = arrayOf("Telugu", "English")
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = languageAdapter
    }

    private fun loadUserSettings() {
        val repository = (requireActivity().application as DivineApplication).repository

        // Load from SharedPreferences first for immediate access
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        val savedTheme = sharedPrefs.getString("theme_mode", "system")
        val savedColor = sharedPrefs.getString("accent_color", "blue")
        val savedLanguage = sharedPrefs.getString("default_language", "telugu")
        val savedUserName = sharedPrefs.getString("user_name", "User")

        android.util.Log.d("ProfileFragment", "Loading settings: theme=$savedTheme, color=$savedColor, lang=$savedLanguage")

        // Apply saved theme immediately (safe: applyTheme() now skips if unchanged)
        applyTheme(savedTheme ?: "system")

        // Set UI values
        userNameEditText.setText(savedUserName)

        // Set theme spinner
        val themeIndex = when (savedTheme) {
            "light" -> 1
            "dark" -> 2
            else -> 0 // system
        }
        themeSpinner.setSelection(themeIndex)

        // Set accent color spinner
        val colorIndex = when (savedColor) {
            "green" -> 1
            "purple" -> 2
            "orange" -> 3
            "red" -> 4
            else -> 0 // blue
        }
        accentColorSpinner.setSelection(colorIndex)

        // Set language spinner
        val languageIndex = if (savedLanguage == "english") 1 else 0
        languageSpinner.setSelection(languageIndex)

        // Also load from database for other settings
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getUserSettings().collectLatest { settings ->
                settings?.let { setupSettings(it) }
            }
        }
    }

    private fun setupSettings(settings: com.example.divneblessing_v0.data.UserSettings) {
        userNameEditText.setText(settings.userName)

        // Set theme spinner
        val themeIndex = when (settings.themeMode) {
            "light" -> 1
            "dark" -> 2
            else -> 0 // system
        }
        themeSpinner.setSelection(themeIndex)

        // Set accent color spinner
        val colorIndex = when (settings.accentColor) {
            "green" -> 1
            "purple" -> 2
            "orange" -> 3
            "red" -> 4
            else -> 0 // blue
        }
        accentColorSpinner.setSelection(colorIndex)

        // Set language spinner
        val languageIndex = if (settings.defaultLanguage == "english") 1 else 0
        languageSpinner.setSelection(languageIndex)

        // Load profile image
        settings.profileImagePath?.let { path ->
            Glide.with(this)
                .load(path)
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(profileImageView)
        }
    }

    private fun setupClickListeners() {
        profileImageView.setOnClickListener {
            openImagePicker()
        }

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun saveProfileImage(uri: Uri) {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateProfileImage(uri.toString())

            Glide.with(this@ProfileFragment)
                .load(uri)
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(profileImageView)
        }
    }

    private fun saveSettings() {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            val userName = userNameEditText.text.toString()
            if (userName.isNotEmpty()) {
                repository.updateUserName(userName)
            }

            val themeMode = when (themeSpinner.selectedItemPosition) {
                1 -> "light"
                2 -> "dark"
                else -> "system"
            }
            repository.updateThemeMode(themeMode)

            val accentColor = when (accentColorSpinner.selectedItemPosition) {
                1 -> "green"
                2 -> "purple"
                3 -> "orange"
                4 -> "red"
                else -> "blue"
            }
            repository.updateAccentColor(accentColor)

            val language = if (languageSpinner.selectedItemPosition == 1) "english" else "telugu"
            repository.updateDefaultLanguage(language)

            android.util.Log.d("ProfileFragment", "Saving settings: theme=$themeMode, color=$accentColor, lang=$language")

            // Apply changes immediately (each method now recreates ONLY if changed)
            applyTheme(themeMode)
            applyAccentColor(accentColor)
            applyDefaultLanguage(language)

            // Persist for immediate access
            val sharedPrefs = requireContext().getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
            with(sharedPrefs.edit()) {
                putString("theme_mode", themeMode)
                putString("accent_color", accentColor)
                putString("default_language", language)
                putString("user_name", userName)
                commit() // immediate persistence
            }

            Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme(themeMode: String) {
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        val currentTheme = sharedPrefs.getString("theme_mode", "system")

        if (currentTheme == themeMode) {
            android.util.Log.d("ProfileFragment", "Theme already $themeMode, skipping recreate")
            return
        }

        android.util.Log.d("ProfileFragment", "Applying theme: $themeMode")
        when (themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        // Save & recreate only if changed
        sharedPrefs.edit().putString("theme_mode", themeMode).commit()
        activity?.recreate()
    }

    private fun applyAccentColor(accentColor: String) {
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        val currentColor = sharedPrefs.getString("accent_color", "blue")

        if (currentColor == accentColor) {
            android.util.Log.d("ProfileFragment", "Accent already $accentColor, skipping recreate")
            return
        }

        android.util.Log.d("ProfileFragment", "Applying accent color: $accentColor")
        sharedPrefs.edit().putString("accent_color", accentColor).commit()

        // Map to your style names defined in themes.xml / themes-night.xml
        val colorResId = when (accentColor) {
            "green" -> R.style.Theme_divneblessing_v0_Green
            "purple" -> R.style.Theme_divneblessing_v0_Purple
            "orange" -> R.style.Theme_divneblessing_v0_Orange
            "red" -> R.style.Theme_divneblessing_v0_Red
            else -> R.style.Theme_divneblessing_v0_Blue
        }

        // Apply & recreate only if changed
        requireActivity().setTheme(colorResId)
        activity?.recreate()
    }

    private fun applyDefaultLanguage(language: String) {
        android.util.Log.d("ProfileFragment", "Applying default language: $language")

        // Save to preferences
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", android.content.Context.MODE_PRIVATE)
        val currentLang = sharedPrefs.getString("default_language", "telugu")
        if (currentLang == language) return

        sharedPrefs.edit().putString("default_language", language).commit()

        // Apply language setting immediately
        val config = resources.configuration
        val locale = if (language == "english") java.util.Locale.ENGLISH else java.util.Locale("te")
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Notify the application about language change
        (requireActivity().application as DivineApplication).updateLanguage(language)

        // Optional: recreate if you want strings to refresh everywhere
        activity?.recreate()
    }
}
