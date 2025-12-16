package com.example.divneblessing_v0.ui.god

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SongItem
import com.example.divneblessing_v0.data.SlokaItem
import com.example.divneblessing_v0.service.SongDownloadManager
import com.example.divneblessing_v0.service.DownloadProgress
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.Toast

class GodCategoryFragment : Fragment() {

    private var godId: String = "unknown_god"
    private var godName: String = "Songs"
    private var godImageFileName: String = "vishnu.png"
    private lateinit var songsAdapter: GodSongsAdapter
    private lateinit var slokaAdapter: SlokaAdapter
    private lateinit var downloadManager: SongDownloadManager
    
    // Flags to prevent flow updates during toggle operations
    private var isTogglingSloka = false
    private var isTogglingSong = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { b ->
            godId = b.getString("godId") ?: godId
            godName = b.getString("godName") ?: godName
            godImageFileName = b.getString("godImageFileName") ?: godImageFileName
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_god_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set toolbar title to show the god name + scripts instead of app name
        (activity as? AppCompatActivity)?.supportActionBar?.title = "$godName songs and chants"

        // NEW: Back button
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            findNavController().navigateUp()
        }

        val headerImage = view.findViewById<ImageView>(R.id.headerImage)

        // Load god image from assets
        try {
            Glide.with(this)
                .load("file:///android_asset/images/$godImageFileName")
                .placeholder(R.drawable.img_loader)
                .error(R.drawable.img_loader)
                .into(headerImage)
        } catch (e: Exception) {
            headerImage.setImageResource(R.drawable.img_loader)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_songs)
        
        val repository = (requireActivity().application as DivineApplication).repository
        downloadManager = SongDownloadManager(requireContext(), repository)

        // 1. Sloka Adapter
        slokaAdapter = SlokaAdapter(
            onSlokaClick = { slokaItem ->
                // Navigate to Sloka Viewer
                val bundle = Bundle().apply {
                    putString("slokaId", slokaItem.id)
                    putString("title", slokaItem.title)
                    putString("godId", slokaItem.godId)
                }
                findNavController().navigate(R.id.action_godCategory_to_slokaViewer, bundle)
            },
            onToggleLike = { slokaItem ->
                toggleSlokaFavorite(slokaItem.id)
            }
        )

        // 2. Songs Adapter
        songsAdapter = GodSongsAdapter(
            items = mutableListOf<SongItem>(),
            onPlay = { song ->
                val args = Bundle().apply {
                    putString("songId", song.id)
                    putString("title", song.title)
                    putString("godId", song.godId)
                }
                findNavController().navigate(R.id.songPlayerFragment, args)
            },
            onToggleLike = { song, _ ->
                toggleFavorite(song.id)
            },
            onDownload = { song ->
                downloadSong(song)
            },
            onDelete = { song ->
                deleteSong(song)
            }
        )
        
        // Combine adapters: Slokas first, then Songs
        val concatAdapter = androidx.recyclerview.widget.ConcatAdapter(slokaAdapter, songsAdapter)
        
        recyclerView.adapter = concatAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Load data
        loadSongs()
        loadSlokas()
    }

    private fun loadSongs() {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            repository.getSongsByGodWithFavorites(godId).collectLatest { songs: List<SongItem> ->
                // Only update adapter if we're not in the middle of a toggle operation
                // This prevents flow updates from overwriting immediate UI changes
                if (!isTogglingSong) {
                    songsAdapter.updateItems(songs.toMutableList())
                }
            }
        }
    }

    private fun loadSlokas() {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            // Use getSlokasByGodWithFavorites instead of raw getSlokasByGod
            repository.getSlokasByGodWithFavorites(godId).collectLatest { slokaItems ->
                // Only update adapter if we're not in the middle of a toggle operation
                // This prevents flow updates from overwriting immediate UI changes
                if (!isTogglingSloka) {
                    slokaAdapter.updateItems(slokaItems)
                }
            }
        }
    }

    private fun toggleSlokaFavorite(slokaId: String) {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                isTogglingSloka = true
                repository.toggleSlokaFavorite(slokaId)
                // Small delay to ensure DB update completes before allowing flow updates
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                android.util.Log.e("GodCategoryFragment", "Sloka Favorite toggle error: ${e.message}", e)
            } finally {
                isTogglingSloka = false
            }
        }
    }

    private fun toggleFavorite(songId: String) {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                isTogglingSong = true
                repository.toggleFavorite(songId)
                // Small delay to ensure DB update completes before allowing flow updates
                kotlinx.coroutines.delay(100)
            } catch (e: Exception) {
                android.util.Log.e("GodCategoryFragment", "Favorite toggle error: ${e.message}", e)
            } finally {
                isTogglingSong = false
            }
        }
    }

    private fun downloadSong(song: SongItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val repository = (requireActivity().application as DivineApplication).repository
                val songEntity = repository.getSongById(song.id)
                
                if (songEntity == null || songEntity.audioFileURL.isEmpty()) {
                    Toast.makeText(requireContext(), "Download URL not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                Toast.makeText(requireContext(), "Downloading ${song.title}...", Toast.LENGTH_SHORT).show()
                
                val downloadId = downloadManager.downloadSong(
                    songId = song.id,
                    audioFileURL = songEntity.audioFileURL,
                    title = song.title
                )
                
                if (downloadId > 0) {
                    // Observe download progress
                    downloadManager.observeDownload(downloadId).collect { progress ->
                        when (progress) {
                            is DownloadProgress.Completed -> {
                                repository.markSongAsDownloaded(song.id, progress.localUri, progress.fileSize)
                                Toast.makeText(requireContext(), "Downloaded ${song.title}", Toast.LENGTH_SHORT).show()
                            }
                            is DownloadProgress.Failed -> {
                                Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show()
                            }
                            is DownloadProgress.Downloading -> {
                                // Could update UI with progress here
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteSong(song: SongItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val repository = (requireActivity().application as DivineApplication).repository
                val songEntity = repository.getSongById(song.id)
                
                val success = downloadManager.deleteSong(song.id, songEntity?.localFilePath)
                
                if (success) {
                    Toast.makeText(requireContext(), "Deleted ${song.title}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Delete error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}