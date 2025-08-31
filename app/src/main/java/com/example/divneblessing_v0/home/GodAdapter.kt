package com.example.divneblessing_v0.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.GodItem
import java.io.File

class GodAdapter(
    private var items: MutableList<GodItem>,
    private val onClick: (GodItem) -> Unit
) : RecyclerView.Adapter<GodAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.god_image)
        val title: TextView = v.findViewById(R.id.god_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_god, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        // Load image from assets
        try {
            Glide.with(holder.img.context)
                .load("file:///android_asset/images/${item.imageFileName}")
                .placeholder(R.drawable.sample_vishnu)
                .error(R.drawable.sample_vishnu)
                .into(holder.img)
        } catch (e: Exception) {
            holder.img.setImageResource(R.drawable.sample_vishnu)
        }
        
        holder.title.text = item.name
        holder.itemView.setOnClickListener { onClick(item) }
    }

    fun updateItems(newItems: List<GodItem>) {
        android.util.Log.d("GodAdapter", "Updating items: ${newItems.size} gods")
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
