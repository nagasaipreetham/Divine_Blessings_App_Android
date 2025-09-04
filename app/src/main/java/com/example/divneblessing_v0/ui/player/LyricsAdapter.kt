package com.example.divneblessing_v0.ui.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.R

data class LrcLine(val timeMs: Int?, val text: String)

class LyricsAdapter(private var items: List<LrcLine>) : RecyclerView.Adapter<LyricsAdapter.VH>() {

    private var highlightedIndex = -1

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvLyric)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lyric_line, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = items[position]
        holder.tv.text = line.text

        val ctx = holder.itemView.context

        // Resolve accent color from current theme (fallback to brandAccent)
        val accent = run {
            val tv = android.util.TypedValue()
            val theme = ctx.theme
            val resolvedSecondary = theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, tv, true)
            if (resolvedSecondary) {
                if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId) else tv.data
            } else {
                val resolvedAccent = theme.resolveAttribute(android.R.attr.colorAccent, tv, true)
                if (resolvedAccent) {
                    if (tv.resourceId != 0) androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId) else tv.data
                } else {
                    androidx.core.content.ContextCompat.getColor(ctx, R.color.brandAccent)
                }
            }
        }

        // Resolve theme textColorPrimary and create a dimmed variant
        val normal = run {
            val tv = android.util.TypedValue()
            val resolved = ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            if (resolved && tv.resourceId != 0) {
                androidx.core.content.ContextCompat.getColor(ctx, tv.resourceId)
            } else {
                tv.data
            }
        }
        val dim = androidx.core.graphics.ColorUtils.setAlphaComponent(normal, 0x99)

        if (position == highlightedIndex && line.text.isNotBlank()) {
            holder.tv.setTextColor(accent)
            holder.tv.textSize = 18f
            holder.tv.setTypeface(holder.tv.typeface, android.graphics.Typeface.BOLD)
        } else {
            holder.tv.setTextColor(if (line.text.isBlank()) dim else normal)
            holder.tv.textSize = 16f
            holder.tv.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    fun submit(list: List<LrcLine>) {
        items = list
        highlightedIndex = -1
        notifyDataSetChanged()
    }

    fun highlightFor(timeMs: Int): Int? {
        if (items.isEmpty()) return null
        // Find last line with time <= current, skipping untimed lines
        var idx = -1
        for (i in items.indices) {
            val t = items[i].timeMs ?: continue
            if (t <= timeMs) idx = i else break
        }
        if (idx != highlightedIndex) {
            val old = highlightedIndex
            highlightedIndex = idx
            if (old >= 0) notifyItemChanged(old)
            if (idx >= 0) notifyItemChanged(idx)
        }
        return if (idx >= 0) idx else null
    }

    fun indexForTime(ms: Int): Int {
        if (items.isEmpty()) return -1
        var idx = -1
        for (i in items.indices) {
            val t = items[i].timeMs ?: continue
            if (t <= ms) idx = i else break
        }
        return idx
    }
}
