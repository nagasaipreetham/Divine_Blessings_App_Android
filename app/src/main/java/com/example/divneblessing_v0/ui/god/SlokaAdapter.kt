package com.example.divneblessing_v0.ui.god

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SlokaItem

class SlokaAdapter(
    private val onSlokaClick: (SlokaItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = listOf<SlokaItem>()
    private var isExpanded = false

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun updateItems(newItems: List<SlokaItem>) {
        val wasExpanded = isExpanded
        val oldSize = items.size
        items = newItems
        
        if (wasExpanded) {
            // If already expanded, notify changes to content
            // Simplest is notifyDataSetChanged, or diff util for better animations
            notifyDataSetChanged()
        } else {
            // Just update header if needed (count changes?)
            notifyItemChanged(0) 
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun getItemCount(): Int {
        return 1 + (if (isExpanded) items.size else 0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_sloka_header, parent, false))
        } else {
            ItemVH(inflater.inflate(R.layout.item_sloka, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            // Header logic
            holder.title.text = "Slokas (${items.size})"
            
            // Rotation: 270 (Down) if collapsed, 90 (Up) if expanded.
            // User requested: Not open -> Down, Open -> Up
            holder.arrow.rotation = if (isExpanded) 90f else 270f
            
            holder.itemView.setOnClickListener {
                toggleExpansion()
            }
        } else if (holder is ItemVH) {
            val item = items[position - 1]
            holder.title.text = item.title
            holder.itemView.setOnClickListener { onSlokaClick(item) }
        }
    }

    private fun toggleExpansion() {
        if (items.isEmpty()) return // Don't expand empty list
        
        isExpanded = !isExpanded
        if (isExpanded) {
            notifyItemRangeInserted(1, items.size)
            notifyItemChanged(0) // Update arrow
        } else {
            notifyItemRangeRemoved(1, items.size)
            notifyItemChanged(0) // Update arrow
        }
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.header_title)
        val arrow: ImageView = v.findViewById(R.id.header_arrow)
    }

    class ItemVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.sloka_title)
    }
}
