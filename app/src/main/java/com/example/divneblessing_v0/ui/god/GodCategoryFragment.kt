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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GodCategoryFragment : Fragment() {

    private var godId: String = "unknown_god"
    private var godName: String = "Songs"
    private var godImageFileName: String = "vishnu.png"
    private lateinit var adapter: GodSongsAdapter

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
                .placeholder(R.drawable.sample_vishnu)
                .error(R.drawable.sample_vishnu)
                .into(headerImage)
        } catch (e: Exception) {
            headerImage.setImageResource(R.drawable.sample_vishnu)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_songs)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = GodSongsAdapter(
            items = mutableListOf<SongItem>(), // FIX: Explicit type
            onPlay = { song ->
                val args = Bundle().apply {
                    putString("songId", song.id)
                    putString("title", song.title)
                    putString("godId", song.godId)
                }
                findNavController().navigate(R.id.songPlayerFragment, args)
            },
            onToggleLike = { song, isFavorite ->
                toggleFavorite(song.id, isFavorite)
            }
        )
        recyclerView.adapter = adapter

        // Load songs
        loadSongs()
    }

    private fun loadSongs() {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            repository.getSongsByGodWithFavorites(godId).collectLatest { songs: List<SongItem> ->
                adapter.updateItems(songs.toMutableList()) // FIX: Convert to mutable list
            }
        }
    }

    private fun toggleFavorite(songId: String, isFavorite: Boolean) {
        val repository = (requireActivity().application as DivineApplication).repository

        viewLifecycleOwner.lifecycleScope.launch {
            if (isFavorite) {
                repository.removeFavorite(songId)
            } else {
                repository.addFavorite(songId)
            }
        }
    }
}