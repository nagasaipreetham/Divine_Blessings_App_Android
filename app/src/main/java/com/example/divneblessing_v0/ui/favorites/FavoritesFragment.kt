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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Favorites"

        val recyclerView = view.findViewById<RecyclerView>(R.id.favoritesRecyclerView)
        adapter = FavoritesAdapter(
            items = mutableListOf(),
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
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadFavorites()
    }

    private fun loadFavorites() {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getFavoritesWithDetails().collectLatest { songs ->
                adapter.updateItems(songs)
            }
        }
    }

    private fun toggleFavorite(songId: String, isFavorite: Boolean) {
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.toggleFavorite(songId)
            } catch (e: Exception) {
                android.util.Log.e("FavoritesFragment", "Favorite toggle error: ${e.message}", e)
            }
        }
    }
}

class FavoritesAdapter(
    private var items: MutableList<SongItem>,
    private val onPlay: (SongItem) -> Unit,
    private val onToggleLike: (SongItem, Boolean) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

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
            onToggleLike(item, !item.isFavorite)
            // Update UI immediately
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                items[position].isFavorite = !items[position].isFavorite
                renderLike()
            }
        }
        holder.play.setOnClickListener { onPlay(item) }
    }

    fun updateItems(newItems: List<SongItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
