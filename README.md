# Āśraya - Spiritual Devotional Music App

A feature-rich Android application for streaming and managing devotional music (stotrams, bhajans, chalisa) with synchronized lyrics in Telugu and English.

## 🎯 Overview

Āśraya is a modern Android app that combines cloud streaming with offline capabilities, providing users with a seamless experience for devotional music. The app features real-time lyrics synchronization, background playback, and a beautiful Material Design 3 interface.

## ✨ Key Features

### 🎵 Audio & Playback
- **Cloud Streaming**: Stream songs from Cloudflare R2 CDN
- **Offline Downloads**: Download songs for offline playback
- **Background Playback**: Persistent foreground service with media controls
- **Variable Speed**: Playback speed control (0.25x - 2.0x) with per-song memory
- **Media Notifications**: Rich notifications with play/pause, like, and lyrics display

### 📝 Lyrics & Language
- **Real-time Synchronization**: LRC format with auto-scrolling and highlighting
- **Bilingual Support**: Telugu and English lyrics with instant toggle
- **Smart Scrolling**: Auto-center current line with manual override
- **Locate Feature**: Jump to current playing line instantly
- **Lyrics Preprocessing**: Cached in database for instant loading

### 🎨 User Interface
- **Material Design 3**: Modern, beautiful UI following latest guidelines
- **Theme Options**: System, Light, and Dark modes
- **Accent Colors**: 5 color schemes (Blue, Green, Purple, Orange, Red)
- **Responsive Design**: Optimized for all screen sizes
- **Mini Player**: Persistent mini player across all screens

### 🔍 Discovery & Organization
- **Real-time Search**: Debounced search across songs and gods
- **Favorites System**: Like/unlike songs with instant sync
- **God Categories**: Browse songs by deity
- **Download Manager**: Track and manage downloaded songs

### 📊 Chanting Tools
- **Per-song Counter**: Track chanting count for each song
- **Global Counter**: Dedicated counter screen for general use
- **Persistent Tracking**: Counters saved to database
- **Reset Options**: Individual and global reset functionality

### ⚙️ Customization
- **User Profile**: Customizable name and profile picture
- **Language Preference**: Default language selection
- **Theme Persistence**: Settings saved across sessions
- **Per-song Overrides**: Individual lyrics language preference

## 🏗️ Architecture & Technologies

### Architecture Pattern
- **MVVM (Model-View-ViewModel)**: Clean separation of concerns
- **Repository Pattern**: Single source of truth for data
- **Clean Architecture**: Layered approach with clear dependencies

### Tech Stack

**Core Technologies:**
- **Language**: Kotlin 2.0.21
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Build System**: Gradle with Kotlin DSL

**UI Layer:**
- Material Design 3 (Material Components 1.11.0)
- ViewBinding & DataBinding
- Navigation Component
- RecyclerView with custom adapters
- ConstraintLayout & CoordinatorLayout

**Data Layer:**
- Room Database 2.6.1 (SQLite ORM)
- Kotlin Coroutines 1.7.3
- Flow for reactive data streams
- SharedPreferences for settings

**Media & Playback:**
- ExoPlayer (Media3 1.4.1)
- MediaSession for background playback
- PlayerNotificationManager for rich notifications
- Foreground Service for persistent playback

**Networking & Storage:**
- Cloudflare R2 CDN for audio streaming
- Android DownloadManager for file downloads
- App-specific external storage for offline files

**Image Loading:**
- Glide 4.16.0 for efficient image loading and caching

**Other Libraries:**
- Lifecycle Components (ViewModel, LiveData)
- AndroidX Core KTX
- Legacy Support for compatibility

## 📁 Project Structure

```
app/src/main/
├── java/com/example/divneblessing_v0/
│   ├── data/
│   │   ├── Models.kt              # Entity classes (God, Song, Favorite, etc.)
│   │   ├── Database.kt            # Room database, DAOs, migrations
│   │   └── Repository.kt          # Data access layer with business logic
│   ├── service/
│   │   ├── MediaPlayerService.kt  # Foreground service for audio playback
│   │   └── DownloadManager.kt     # Song download management
│   ├── ui/
│   │   ├── home/                  # Home screen with god grid
│   │   ├── god/                   # God category with songs list
│   │   ├── player/                # Song player with lyrics sync
│   │   │   ├── SongPlayerFragment.kt
│   │   │   ├── LrcParser.kt       # LRC file parser
│   │   │   ├── LyricsAdapter.kt   # RecyclerView adapter
│   │   │   └── SpeedManager.kt    # Playback speed control
│   │   ├── search/                # Search functionality
│   │   ├── favorites/             # Favorites management
│   │   ├── counter/               # Global counter screen
│   │   └── profile/               # Settings and profile
│   ├── MainActivity.kt            # Main activity with bottom navigation
│   └── DivineApplication.kt       # Application class with initialization
└── assets/
    ├── images/                    # God images
    ├── lyrics/                    # LRC files (Telugu & English)
    │   ├── telugu/
    │   └── english/
    └── gods_songs.json            # Content metadata
```

## 🗄️ Database Schema

### Room Database (Version 7)

**Tables:**
- **gods**: God information (id, name, imageFileName, displayOrder)
- **songs**: Song metadata (id, title, godId, audioFileName, audioFileURL, lyricsTeluguFileName, lyricsEnglishFileName, duration, displayOrder, isDownloaded, localFilePath, fileSizeBytes, languageDefault)
- **favorites**: User's favorite songs (songId, addedAt)
- **song_counters**: Persistent counters for each song (songId, count, lastUpdated)
- **user_settings**: User preferences (userName, themeMode, accentColor, defaultLanguage, profileImagePath)
- **lyrics**: Preprocessed lyrics cache (songId, language, jsonLines, updatedAt, source)

**Migration Strategy:**
- Destructive migration with automatic data repopulation from JSON
- SharedPreferences cleared on database wipe to ensure fresh data load

### Content Data
The app comes pre-configured with:
- **3 Gods**: Vishnu, Shiva, Hanuman
- **3 Songs**: Vishnu Sahasranama, Lingashtakam, Hanuman Chalisa
- All songs available for streaming from Cloudflare R2

## 📦 Asset Structure

```
app/src/main/assets/
├── images/                    # God images (PNG/JPEG format)
│   ├── vishnu.png
│   ├── shiva.png
│   └── Hanuman.jpeg
├── lyrics/                    # Lyrics files (LRC format)
│   ├── telugu/               # Telugu lyrics
│   │   ├── song_1_te.lrc
│   │   ├── Lingashtakam_te.lrc
│   │   └── Hanuman_Chalisa_te.lrc
│   └── english/              # English lyrics
│       ├── song_1_en.lrc
│       ├── Lingashtakam_en.lrc
│       └── Hanuman_Chalisa_en.lrc
└── gods_songs.json           # Content metadata and CDN URLs
```

**Note**: Audio files are streamed from Cloudflare R2 CDN, not bundled in the APK.

## 🔧 Technical Implementation Details

### 1. Cloud Streaming & Downloads
- **CDN**: Cloudflare R2 for global content delivery
- **Streaming**: ExoPlayer with adaptive streaming support
- **Downloads**: Android DownloadManager with progress tracking
- **Storage**: App-specific external storage (`/Android/data/.../files/Music/`)
- **Fallback**: Graceful handling of network failures

### 2. Lyrics Synchronization Engine
- **Format**: LRC files with millisecond precision `[mm:ss.xx]`
- **Parser**: Custom LRC parser supporting timed and untimed lines
- **Preprocessing**: Lyrics cached in Room database as JSON for instant loading
- **Auto-scroll**: Smart scrolling with 42% viewport positioning
- **Manual Override**: User can disable auto-scroll by manual scrolling
- **Locate Feature**: Re-enable auto-scroll and jump to current line

### 3. Background Playback Service
- **Service Type**: Foreground service with `mediaPlayback` type
- **Lifecycle**: Survives navigation and app backgrounding
- **Notifications**: Rich media notifications with:
  - Play/pause control
  - Like/unlike button with colored icons
  - Current lyrics display (updates every 10ms)
  - Album art from god images
- **MediaSession**: Integration with system media controls and Bluetooth devices

### 4. Performance Optimizations
- **APK Size**: Reduced from 32MB to 14MB by removing bundled audio
- **Database Size**: Optimized from 20MB to 2-3MB by removing asset checksums
- **Lyrics Loading**: Preprocessed and cached for instant display
- **Image Loading**: Glide with memory and disk caching
- **Search**: Debounced input (300ms) to reduce database queries
- **UI Updates**: Efficient RecyclerView updates with DiffUtil patterns

### 5. State Management
- **Session State**: Counters and playback speed (reset on app restart)
- **Persistent State**: Favorites, settings, downloaded songs, database counters
- **Per-song Preferences**: Lyrics language override per song
- **Theme Persistence**: Settings applied before `setContentView()` for flicker-free startup

## 📚 Dependencies

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity:1.8.0")
    
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Lifecycle & Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media:media:1.6.0")
    
    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK with API 35
- Gradle 8.0+

### Building the Project

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Divine_Blessing
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the project directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - Wait for dependencies to download

4. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

5. **Run on Device/Emulator**
   - Connect Android device or start emulator
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

### For Users

1. **Browse Content**: Tap on any god from the home screen grid
2. **Play Songs**: Select a song to open the full player
3. **Download for Offline**: Tap download icon in song list
4. **Toggle Lyrics Language**: Use "అ" / "A" button in player
5. **Use Counters**: Use +/- buttons to track chanting
6. **Search**: Use search tab to find specific songs or gods
7. **Manage Favorites**: Tap heart icon to save/remove favorites
8. **Customize**: Go to Profile tab for theme, language, and settings

### For Developers

#### Adding New Content

1. **Update JSON Metadata** (`assets/gods_songs.json`):
   ```json
   {
     "version": "20251122154434",
     "cloudflareBaseUrl": "https://your-cdn-url.com/audio",
     "gods": [
       {
         "id": "god_newgod",
         "name": "New God Name",
         "imageFileName": "newgod.png",
         "displayOrder": 4,
         "songs": [...]
       }
     ]
   }
   ```

2. **Add Assets**:
   - Place god image in `assets/images/`
   - Upload audio to Cloudflare R2
   - Create LRC files in `assets/lyrics/telugu/` and `assets/lyrics/english/`

3. **Increment Version**: Update version in JSON to trigger database refresh

4. **Test**: Uninstall app, reinstall, and verify new content appears

## 📝 File Naming Conventions

- **Audio**: `{songId}.mp3` (e.g., `song_1.mp3`, `Lingashtakam.mp3`)
- **Images**: `{godId}.png` or `.jpeg` (e.g., `vishnu.png`, `Hanuman.jpeg`)
- **Lyrics**: `{songId}_{language}.lrc` (e.g., `song_1_te.lrc`, `Hanuman_Chalisa_en.lrc`)
  - Telugu: `*_te.lrc`
  - English: `*_en.lrc`

## 🎨 LRC File Format

```lrc
[offset:0]
[00:00.00]First line of lyrics
[00:05.50]Second line with timestamp
[00:10.25]Third line
```

- Timestamps: `[mm:ss.xx]` format
- Offset: Optional global offset in milliseconds
- Untimed lines: Lines without timestamps are displayed statically

## 🎯 Performance Metrics

| Metric | Value |
|--------|-------|
| APK Size | 13.94 MB |
| App Data (First Launch) | ~2-3 MB |
| Lyrics Load Time | < 50ms (cached) |
| Search Response Time | < 100ms (debounced) |
| Min Android Version | 7.0 (API 24) |
| Target Android Version | 15 (API 35) |

## 🔮 Future Enhancements

- [ ] Playlist creation and management
- [ ] Sleep timer for meditation sessions
- [ ] Repeat modes (single, all, shuffle)
- [ ] Equalizer integration
- [ ] Home screen widget
- [ ] Share songs with friends
- [ ] Lyrics editing and contribution
- [ ] Multiple user profiles
- [ ] Cloud backup for favorites and counters
- [ ] Analytics and listening statistics
- [ ] Chromecast support
- [ ] Android Auto integration

## 🐛 Known Issues & Limitations

- Downloaded songs are stored in app-specific storage and will be deleted when app data is cleared
- Lyrics must be manually created in LRC format
- No automatic lyrics fetching from online sources
- Theme changes require app restart
- Maximum playback speed is 2.0x

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is developed for spiritual and educational purposes. All devotional content should respect copyright and licensing requirements.

## 👨‍💻 Developer

Developed with 🙏 for the spiritual community.

## 📞 Support

For issues, questions, or suggestions, please open an issue on GitHub.

---

**Note**: This app requires an active internet connection for streaming. Downloaded songs can be played offline.
