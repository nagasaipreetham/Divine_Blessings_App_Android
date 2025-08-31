package com.example.divneblessing_v0.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.example.divneblessing_v0.DivineApplication
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.divneblessing_v0.R
import com.example.divneblessing_v0.data.GodItem
import com.example.divneblessing_v0.home.GodAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var adapter: GodAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_gods)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.setHasFixedSize(true)

        adapter = GodAdapter(mutableListOf()) { item ->
            val args = bundleOf(
                "godId" to item.id,
                "godName" to item.name,
                "godImageFileName" to item.imageFileName
            )

            val opts = androidx.navigation.NavOptions.Builder()
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .setPopEnterAnim(android.R.anim.fade_in)
                .setPopExitAnim(android.R.anim.fade_out)
                .build()

            findNavController().navigate(R.id.action_homeFragment_to_godCategoryFragment, args, opts)
        }
        recycler.adapter = adapter

        // Load gods from repository
        loadGods()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // NEW: Personalized greeting
        val greeting = view.findViewById<android.widget.TextView>(R.id.greeting)
        val repository = (requireActivity().application as DivineApplication).repository
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getUserSettings().collectLatest { settings ->
                val raw = settings?.userName?.takeIf { it.isNotBlank() } ?: "User"
                val name = raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                greeting.text = "Good morning, $name"
            }
        }
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Home"
    }

    private fun loadGods() {
        val repository = (requireActivity().application as DivineApplication).repository
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                android.util.Log.d("HomeFragment", "Starting to load gods...")
                repository.getAllGods().collectLatest { gods ->
                    android.util.Log.d("HomeFragment", "Loaded ${gods.size} gods: ${gods.map { it.name }}")
                    val godItems = gods.map { god ->
                        GodItem(
                            id = god.id,
                            name = god.name,
                            imageFileName = god.imageFileName
                        )
                    }
                    android.util.Log.d("HomeFragment", "Created ${godItems.size} god items")
                    adapter.updateItems(godItems)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error loading gods: ${e.message}", e)
            }
        }
    }
}
