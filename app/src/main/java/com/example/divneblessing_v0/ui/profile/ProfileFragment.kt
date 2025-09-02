package com.example.divneblessing_v0.ui.profile

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var userNameEditText: EditText
    private lateinit var themeSpinner: Spinner
    private lateinit var accentColorSpinner: Spinner
    private lateinit var languageSpinner: Spinner

    private var isInitialSetup = true
    private var saveJob: Job? = null
    private var lastSavedUserName: String = ""

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
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "User Settings"

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

        // Debounce username save: wait 1 second after user stops typing to save
        userNameEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { /* no-op */ }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isInitialSetup) return
                saveJob?.cancel() // cancel previous pending save
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isInitialSetup) return

                val newName = s?.toString() ?: ""
                saveJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(1000L) // 1 second debounce
                    if (newName.isNotEmpty() && newName != lastSavedUserName) {
                        saveUserName(newName)
                    }
                }
            }
        })
    }

    private fun setupSpinners() {
        val themes = arrayOf("System", "Light", "Dark")
        val themeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, themes)
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        themeSpinner.adapter = themeAdapter

        val colors = arrayOf("Blue", "Green", "Purple", "Orange", "Red")
        val colorAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, colors)
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accentColorSpinner.adapter = colorAdapter

        val languages = arrayOf("Telugu", "English")
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, languages)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = languageAdapter

        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isInitialSetup) {
                    val themeMode = when (position) {
                        1 -> "light"
                        2 -> "dark"
                        else -> "system"
                    }
                    applyTheme(themeMode)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        accentColorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isInitialSetup) {
                    val accentColor = when (position) {
                        1 -> "green"
                        2 -> "purple"
                        3 -> "orange"
                        4 -> "red"
                        else -> "blue"
                    }
                    applyAccentColor(accentColor)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isInitialSetup) {
                    val language = if (position == 1) "english" else "telugu"
                    applyDefaultLanguage(language)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadUserSettings() {
        val repository = (requireActivity().application as DivineApplication).repository
        isInitialSetup = true

        viewLifecycleOwner.lifecycleScope.launch {
            repository.initializeDefaultSettings()

            val sharedPrefs = requireContext().getSharedPreferences("divine_settings", Context.MODE_PRIVATE)
            val savedTheme = sharedPrefs.getString("theme_mode", "system")
            val savedColor = sharedPrefs.getString("accent_color", "blue")
            val savedLanguage = sharedPrefs.getString("default_language", "telugu")
            val savedUserName = sharedPrefs.getString("user_name", "")

            withContext(Dispatchers.Main) {
                userNameEditText.setText(savedUserName)
                lastSavedUserName = savedUserName ?: ""

                val themeIndex = when (savedTheme) {
                    "light" -> 1
                    "dark" -> 2
                    else -> 0
                }
                themeSpinner.setSelection(themeIndex)

                val colorIndex = when (savedColor) {
                    "green" -> 1
                    "purple" -> 2
                    "orange" -> 3
                    "red" -> 4
                    else -> 0
                }
                accentColorSpinner.setSelection(colorIndex)

                val languageIndex = if (savedLanguage == "english") 1 else 0
                languageSpinner.setSelection(languageIndex)

                isInitialSetup = false
            }

            repository.getUserSettings().collectLatest { settings ->
                settings?.let {
                    val editor = sharedPrefs.edit()
                    editor.putString("theme_mode", it.themeMode)
                    editor.putString("accent_color", it.accentColor)
                    editor.putString("default_language", it.defaultLanguage)
                    editor.putString("user_name", it.userName)
                    editor.apply()

                    withContext(Dispatchers.Main) {
                        setupSettings(it)
                        lastSavedUserName = it.userName ?: ""
                    }
                }
            }
        }
    }

    private fun saveUserName(userName: String) {
        if (userName.isEmpty()) return
        if (userName == lastSavedUserName) return

        val repository = (requireActivity().application as DivineApplication).repository
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", Context.MODE_PRIVATE)

        viewLifecycleOwner.lifecycleScope.launch {
            sharedPrefs.edit().putString("user_name", userName).apply()
            repository.updateUserName(userName)
            lastSavedUserName = userName

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Name saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSettings(settings: com.example.divneblessing_v0.data.UserSettings) {
        isInitialSetup = true

        val currentCursorPosition = userNameEditText.selectionStart.takeIf { it >= 0 } ?: 0
        userNameEditText.setText(settings.userName)

        // Restore cursor position within the new text length bounds
        val newCursorPosition = currentCursorPosition.coerceIn(0, userNameEditText.text.length)
        userNameEditText.setSelection(newCursorPosition)

        lastSavedUserName = settings.userName ?: ""

        val themeIndex = when (settings.themeMode) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }
        themeSpinner.setSelection(themeIndex)

        val colorIndex = when (settings.accentColor) {
            "green" -> 1
            "purple" -> 2
            "orange" -> 3
            "red" -> 4
            else -> 0
        }
        accentColorSpinner.setSelection(colorIndex)

        val languageIndex = if (settings.defaultLanguage == "english") 1 else 0
        languageSpinner.setSelection(languageIndex)

        settings.profileImagePath?.let { path ->
            Glide.with(this)
                .load(path)
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(profileImageView)
        }

        isInitialSetup = false
    }


    private fun setupClickListeners() {
        profileImageView.setOnClickListener {
            openImagePicker()
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

    private fun applyTheme(themeMode: String) {
        val repository = (requireActivity().application as DivineApplication).repository
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", Context.MODE_PRIVATE)
        val currentTheme = sharedPrefs.getString("theme_mode", "system")
        if (currentTheme == themeMode) return
        sharedPrefs.edit().putString("theme_mode", themeMode).apply()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateThemeMode(themeMode)
        }
        when (themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        activity?.recreate()
        Toast.makeText(requireContext(), "Theme updated", Toast.LENGTH_SHORT).show()
    }

    private fun applyAccentColor(accentColor: String) {
        val repository = (requireActivity().application as DivineApplication).repository
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", Context.MODE_PRIVATE)
        val currentColor = sharedPrefs.getString("accent_color", "blue")
        if (currentColor == accentColor) return
        sharedPrefs.edit().putString("accent_color", accentColor).apply()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateAccentColor(accentColor)
        }
        val colorResId = when (accentColor) {
            "green" -> R.style.Theme_divneblessing_v0_Green
            "purple" -> R.style.Theme_divneblessing_v0_Purple
            "orange" -> R.style.Theme_divneblessing_v0_Orange
            "red" -> R.style.Theme_divneblessing_v0_Red
            else -> R.style.Theme_divneblessing_v0_Blue
        }
        requireActivity().setTheme(colorResId)
        activity?.recreate()
        Toast.makeText(requireContext(), "Accent color updated", Toast.LENGTH_SHORT).show()
    }

    private fun applyDefaultLanguage(language: String) {
        val repository = (requireActivity().application as DivineApplication).repository
        val sharedPrefs = requireContext().getSharedPreferences("divine_settings", Context.MODE_PRIVATE)
        val currentLang = sharedPrefs.getString("default_language", "telugu")
        if (currentLang == language) return
        sharedPrefs.edit().putString("default_language", language).apply()
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateDefaultLanguage(language)
        }
        val config = resources.configuration
        val locale = if (language == "english") java.util.Locale.ENGLISH else java.util.Locale("te")
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        (requireActivity().application as DivineApplication).updateLanguage(language)
        activity?.recreate()
        Toast.makeText(requireContext(), "Language updated", Toast.LENGTH_SHORT).show()
    }
}
