package com.example.divneblessing_v0.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SongItem
import com.example.divneblessing_v0.data.SlokaItem
import com.example.divneblessing_v0.ui.god.SlokaAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.ConcatAdapter

class FavoritesFragment : Fragment() {

    private lateinit var slokaAdapter: FavoriteSlokaAdapter
    private lateinit var slokaHeaderAdapter: HeaderAdapter // Separate header for Slokas
    private lateinit var songsHeaderAdapter: HeaderAdapter 
    private lateinit var songsAdapter: FavoriteSongsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Favorites"

        val recyclerView = view.findViewById<RecyclerView>(R.id.favoritesRecyclerView)

        // 1. Sloka Section (Header + Adapter)
        slokaHeaderAdapter = HeaderAdapter("Slokas")
        slokaAdapter = FavoriteSlokaAdapter(
            onSlokaClick = { sloka ->
                val bundle = Bundle().apply {
                    putString("slokaId", sloka.id)
                    putString("title", sloka.title)
                    putString("godId", sloka.godId)
                }
                findNavController().navigate(R.id.action_global_slokaViewerFragment, bundle)
            },
            onToggleLike = { sloka ->
                 toggleSlokaFavorite(sloka)
            }
        )

        // 2. Songs Section (Header + Adapter)
        songsHeaderAdapter = HeaderAdapter("Songs")
        songsAdapter = FavoriteSongsAdapter(
            items = mutableListOf(),
            onPlay = { song ->
                val args = Bundle().apply {
                    putString("songId", song.id)
                    putString("title", song.title)
                    putString("godId", song.godId)
                }
                findNavController().navigate(R.id.songPlayerFragment, args)
            },
            onToggleLike = { song ->
                toggleSongFavorite(song)
            }
        )

        val concatAdapter = ConcatAdapter(
            slokaHeaderAdapter, slokaAdapter, 
            songsHeaderAdapter, songsAdapter
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = concatAdapter

        loadFavoritesOneShot()
    }

    private fun loadFavoritesOneShot() {
        val repository = (requireActivity().application as DivineApplication).repository
        
        viewLifecycleOwner.lifecycleScope.launch {
            // Load Slokas
            val slokas = repository.getFavoriteSlokasWithDetails().first()
            if (slokas.isNotEmpty()) {
                slokaHeaderAdapter.updateCount(slokas.size)
                slokaHeaderAdapter.setVisible(true)
                slokaAdapter.updateItems(slokas)
            } else {
                slokaHeaderAdapter.setVisible(false)
                slokaAdapter.updateItems(emptyList())
            }

            // Load Songs
            val songs = repository.getFavoritesWithDetails().first()
            if (songs.isNotEmpty()) {
                songsHeaderAdapter.updateCount(songs.size)
                songsHeaderAdapter.setVisible(true)
                songsAdapter.updateItems(songs)
            } else {
                songsHeaderAdapter.setVisible(false) // Or handle empty state globally
                songsAdapter.updateItems(emptyList())
            }
        }
    }

    private fun toggleSlokaFavorite(sloka: SlokaItem) {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            // Note: Visuals are updated immediately by the Adapter.
            // We just need to sync with DB.
            repository.toggleSlokaFavorite(sloka.id)
        }
    }

    private fun toggleSongFavorite(song: SongItem) {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            song.isFavorite = !song.isFavorite
            repository.toggleFavorite(song.id)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload when returning to screen to reflect removals
        loadFavoritesOneShot()
    }
}

// Simple Header Adapter for "Songs"
class HeaderAdapter(private val titleBase: String) : RecyclerView.Adapter<HeaderAdapter.VH>() {
    private var count = 0
    private var isVisible = true

    fun updateCount(n: Int) {
        count = n
        notifyDataSetChanged()
    }
    
    fun setVisible(visible: Boolean) {
        isVisible = visible
        notifyDataSetChanged()
    }

    override fun getItemCount() = if (isVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sloka_header, parent, false) // Reuse sloka header layout
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.title.text = "$titleBase ($count)"
        // Hide arrow or make it static
        holder.arrow.visibility = View.GONE
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.header_title)
        val arrow: View = v.findViewById(R.id.header_arrow)
    }
}


class FavoriteSongsAdapter(
    private var items: MutableList<SongItem>,
    private val onPlay: (SongItem) -> Unit,
    private val onToggleLike: (SongItem) -> Unit
) : RecyclerView.Adapter<FavoriteSongsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.song_title)
        val godName: TextView = v.findViewById(R.id.god_name)
        val like: ImageButton = v.findViewById(R.id.btn_like)
        val play: ImageButton = v.findViewById(R.id.btn_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = "${position + 1}. ${item.title}"
        holder.godName.text = item.godName

        fun renderLike() {
            val iconRes = if (item.isFavorite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24
            holder.like.setImageResource(iconRes)
            if (item.isFavorite) {
                val redColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.red)
                holder.like.imageTintList = android.content.res.ColorStateList.valueOf(redColor)
            } else {
                holder.like.imageTintList = null
            }
        }
        renderLike()

        // Apply theme-based tinting to play button - use current accent color
        val typedValue = android.util.TypedValue()
        val theme = holder.itemView.context.theme
        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
        val accentColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, typedValue.resourceId)
        holder.play.imageTintList = android.content.res.ColorStateList.valueOf(accentColor)

        holder.like.setOnClickListener {
            // Update Local Item State immediately
            item.isFavorite = !item.isFavorite
            onToggleLike(item) // Trigger DB update
            renderLike()      // Update UI
        }
        holder.play.setOnClickListener { onPlay(item) }
    }

    fun updateItems(newItems: List<SongItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
