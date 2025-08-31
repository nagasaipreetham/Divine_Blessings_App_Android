# Divine App - Slokas, Songs, and Serenity

A fully offline Android application for Telugu and English spiritual content with synchronized audio and lyrics.

## Features Implemented

### ✅ Core Functionality
- **Offline Operation**: All content stored locally in the app
- **Audio Player**: Full media player with seek, play/pause, and time display
- **Lyrics Sync**: .lrc file support with real-time highlighting
- **Language Toggle**: Switch between Telugu (అ) and English (A) scripts
- **Counter System**: Per-song and global counters for chanting

### ✅ Navigation & UI
- **Home Screen**: Grid of gods with images and names
- **God Category Screen**: List of songs for selected god
- **Song Player**: Full-featured audio player with lyrics
- **Search**: Real-time search across songs and gods
- **Favorites**: Like/unlike songs with persistent storage
- **Profile/Settings**: Theme, language, and user preferences
- **Big Counter**: Dedicated counter screen accessible via FAB

### ✅ Data Management
- **Room Database**: Local SQLite database with Room ORM
- **Repository Pattern**: Clean data access layer
- **Persistent Storage**: Favorites, settings, and counters saved
- **Sample Data**: Pre-populated with 6 gods and 9 songs

### ✅ Technical Features
- **Material Design 3**: Modern UI with theme support
- **Dark/Light Mode**: System, light, and dark theme options
- **Accent Colors**: Blue, green, purple, orange, red options
- **Image Loading**: Glide for efficient image loading from assets
- **Coroutines**: Async operations with Flow for reactive UI

## Project Structure

```
app/src/main/java/com/example/divneblessing_v0/
├── data/
│   ├── Models.kt              # Data classes and entities
│   ├── Database.kt            # Room database and DAOs
│   └── Repository.kt          # Data access layer
├── ui/
│   ├── home/                  # Home screen with god grid
│   ├── god/                   # God category screen
│   ├── player/                # Song player with lyrics
│   ├── search/                # Search functionality
│   ├── favorites/             # Favorites management
│   ├── counter/               # Big counter screen
│   └── profile/               # Settings and profile
├── MainActivity.kt            # Main activity with navigation
└── DivineApplication.kt       # Application class
```

## Database Schema

### Tables
- **gods**: God information (id, name, image, display order)
- **songs**: Song metadata (id, title, god_id, audio file, lyrics files)
- **favorites**: User's favorite songs
- **song_counters**: Persistent counters for each song
- **user_settings**: User preferences (theme, language, profile)

### Sample Data
The app comes pre-loaded with:
- 6 Gods: Vishnu, Shiva, Ganesha, Hanuman, Krishna, Rama
- 9 Songs: Various stotrams and bhajans

## Asset Structure

```
app/src/main/assets/
├── images/                    # God images (PNG format)
├── audio/                     # Song audio files (MP3 format)
└── lyrics/                    # Lyrics files (LRC format)
    ├── *_te.lrc              # Telugu lyrics
    └── *_en.lrc              # English lyrics
```

## Key Features in Detail

### 1. Audio Player
- **File Location**: `assets/audio/{songId}.mp3`
- **Controls**: Play/pause, seek bar, time display
- **No Audio State**: Shows lyrics without audio controls
- **Background Play**: Continues when app is minimized

### 2. Lyrics Synchronization
- **File Format**: .lrc files with timestamps
- **Languages**: Separate files for Telugu and English
- **Real-time Highlighting**: Current line highlighted during playback
- **Locate Button**: Jump to current playing line
- **Static Display**: Shows lyrics even without audio

### 3. Counter System
- **Small Counter**: Per-song counter in player (session-based)
- **Big Counter**: Global counter accessible via FAB
- **Persistent Storage**: Counters saved to database
- **Reset Functionality**: Individual and global reset options

### 4. Search & Favorites
- **Real-time Search**: Debounced search across songs and gods
- **Favorite Management**: Like/unlike with heart icons
- **Persistent Favorites**: Saved to local database
- **Search Results**: Shows song title, god name, and favorite status

### 5. Settings & Theme
- **Theme Modes**: System, Light, Dark
- **Accent Colors**: 5 color options affecting UI elements
- **Language Preference**: Telugu or English default
- **Profile Image**: User can upload profile picture
- **User Name**: Customizable display name

## Dependencies

```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Lifecycle & Coroutines
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Image Loading
implementation("com.github.bumptech.glide:glide:4.16.0")

// Material Design
implementation("com.google.android.material:material:1.11.0")
```

## Usage Instructions

### For Users
1. **Browse Gods**: Tap on any god from the home screen
2. **Play Songs**: Select a song to open the player
3. **Use Counters**: Use + and - buttons to count chanting
4. **Search**: Use the search tab to find specific content
5. **Favorites**: Tap heart icon to save favorite songs
6. **Settings**: Customize theme and language in profile

### For Developers
1. **Add Content**: Place audio files in `assets/audio/`
2. **Add Images**: Place god images in `assets/images/`
3. **Add Lyrics**: Create .lrc files in `assets/lyrics/`
4. **Update Database**: Modify sample data in `Repository.kt`

## File Naming Convention

- **Audio**: `{songId}.mp3` (e.g., `vishnu_sahasranamam.mp3`)
- **Images**: `{godId}.png` (e.g., `vishnu.png`)
- **Lyrics**: `{songId}_{language}.lrc` (e.g., `vishnu_sahasranamam_te.lrc`)

## Future Enhancements

- [ ] Online version checking
- [ ] Content updates via download
- [ ] Playlist functionality
- [ ] Background audio service
- [ ] Widget support
- [ ] Share functionality
- [ ] Analytics and usage tracking

## Technical Notes

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM with Repository pattern
- **Database**: Room with SQLite
- **UI**: Material Design 3 with custom themes
- **Audio**: MediaPlayer for local file playback
- **Images**: Glide for efficient loading and caching

## License

This project is developed for spiritual and educational purposes. All content should respect copyright and licensing requirements.
