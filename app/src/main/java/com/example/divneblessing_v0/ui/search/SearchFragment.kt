package com.example.divneblessing_v0.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.DivineApplication
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchAdapter
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Search"

        searchEditText = view.findViewById(R.id.searchEditText)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        setupRecyclerView()
        setupSearchListener()
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter(
            items = mutableListOf(),
            onPlay = { result ->
                val args = Bundle().apply {
                    putString("songId", result.songId)
                    putString("title", result.title)
                    putString("godId", "") // Will be filled by player
                }
                findNavController().navigate(R.id.songPlayerFragment, args)
            },
            onToggleLike = { result, isFavorite ->
                toggleFavorite(result.songId, isFavorite)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupSearchListener() {
        // Handle enter key press
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchEditText.text.toString()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                // Hide keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                true
            } else {
                false
            }
        }
        
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300) // Debounce search
                    val query = s?.toString() ?: ""
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    } else {
                        adapter.updateItems(emptyList())
                    }
                }
            }
        })
    }

    private fun performSearch(query: String) {
        val repository = (requireActivity().application as DivineApplication).repository
        
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getSearchResultsWithFavorites(query).collectLatest { results ->
                adapter.updateItems(results)
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

class SearchAdapter(
    private var items: MutableList<SearchResult>,
    private val onPlay: (SearchResult) -> Unit,
    private val onToggleLike: (SearchResult, Boolean) -> Unit
) : RecyclerView.Adapter<SearchAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.song_title)
        val godName: TextView = v.findViewById(R.id.god_name)
        val like: ImageButton = v.findViewById(R.id.btn_like)
        val play: ImageButton = v.findViewById(R.id.btn_play)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.godName.text = item.godName

        val iconRes = if (item.isFavorite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24
        holder.like.setImageResource(iconRes)

        holder.like.setOnClickListener {
            onToggleLike(item, !item.isFavorite)
            // Update the item's favorite status immediately for UI feedback
            val position = holder.adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                items[position].isFavorite = !items[position].isFavorite
                val iconRes = if (items[position].isFavorite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24
                holder.like.setImageResource(iconRes)
            }
        }
        holder.play.setOnClickListener { onPlay(item) }
    }

    fun updateItems(newItems: List<SearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
