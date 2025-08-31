package com.example.divneblessing_v0.ui.god

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SongItem

class GodSongsAdapter(
    private var items: MutableList<SongItem>,
    private val onPlay: (SongItem) -> Unit,
    private val onToggleLike: (SongItem, Boolean) -> Unit
) : RecyclerView.Adapter<GodSongsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.song_title)
        val like: ImageButton = v.findViewById(R.id.btn_like)
        val play: ImageButton = v.findViewById(R.id.btn_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = "${position + 1}. ${item.title}"

        fun renderLike() {
            val iconRes = if (item.isFavorite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24
            holder.like.setImageResource(iconRes)
        }
        renderLike()

        holder.like.setOnClickListener {
            onToggleLike(item, !item.isFavorite)
            // Update the item's favorite status immediately for UI feedback
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
