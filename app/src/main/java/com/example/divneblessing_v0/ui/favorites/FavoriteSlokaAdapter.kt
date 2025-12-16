package com.example.divneblessing_v0.ui.favorites

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SlokaItem

class FavoriteSlokaAdapter(
    private var items: List<SlokaItem> = emptyList(),
    private val onSlokaClick: (SlokaItem) -> Unit,
    private val onToggleLike: (SlokaItem) -> Unit
) : RecyclerView.Adapter<FavoriteSlokaAdapter.VH>() {

    fun updateItems(newItems: List<SlokaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sloka, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title

        // Visual Logic
        fun renderLike() {
            val isFav = item.isFavorite
            if (isFav) {
                holder.likeBtn.setImageResource(R.drawable.ic_heart_filled_24)
                val redColor = androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.red)
                holder.likeBtn.imageTintList = android.content.res.ColorStateList.valueOf(redColor)
            } else {
                holder.likeBtn.setImageResource(R.drawable.ic_heart_24)
                // Use default white/grey tint for unliked state
                holder.likeBtn.imageTintList = null
                holder.likeBtn.setColorFilter(android.graphics.Color.parseColor("#DDFFFFFF"))
            }
        }
        renderLike()

        holder.itemView.setOnClickListener { onSlokaClick(item) }
        
        holder.likeBtn.setOnClickListener {
            // Immediate Toggle Logic
            item.isFavorite = !item.isFavorite
            renderLike() // Update visuals immediately (Red <-> White)
            onToggleLike(item) // Notify parent to sync DB
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.sloka_title)
        val likeBtn: ImageButton = v.findViewById(R.id.btn_like_sloka)
    }
}
