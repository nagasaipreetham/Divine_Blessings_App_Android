package com.example.divneblessing_v0.ui.counter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.divneblessing_v0.R

object SessionCounter { var value = 0 } // resets when app closes

class CounterFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_counter, container, false)

        val txt = v.findViewById<TextView>(R.id.txtCount)
        val plus = v.findViewById<ImageButton>(R.id.btnPlus)
        val minus = v.findViewById<ImageButton>(R.id.btnMinus)
        val reset = v.findViewById<ImageButton>(R.id.btnReset)

        fun refresh() { txt.text = SessionCounter.value.toString() }
        refresh()

        plus.setOnClickListener { SessionCounter.value++; refresh() }
        minus.setOnClickListener { if (SessionCounter.value > 0) SessionCounter.value--; refresh() }
        reset.setOnClickListener { SessionCounter.value = 0; refresh() }

        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Counter"
    }
}
