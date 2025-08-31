package com.example.divneblessing_v0.ui.player

/**
 * Very small LRC parser.
 * Supports lines like:
 *   [mm:ss.xx] text
 *   [mm:ss] text
 * Multiple timestamps per line → uses the first one.
 * Blank lines preserved for readable spacing (no time).
 */
object LrcParser {

    private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,2}))?]""")

    fun parse(lines: List<String>): List<LrcLine> {
        val out = mutableListOf<LrcLine>()
        for (raw in lines) {
            val matches = timeRegex.findAll(raw).toList()
            val text = raw.replace(timeRegex, "").trim()
            val timeMs = matches.firstOrNull()?.let { m ->
                val mm = m.groupValues[1].toInt()
                val ss = m.groupValues[2].toInt()
                val cc = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                (mm * 60 + ss) * 1000 + (if (cc < 100) cc * 10 else cc) // support .xx
            }
            // If line contains no timestamp, keep it as a static (null time)
            out.add(LrcLine(timeMs = timeMs, text = text))
        }
        // Sort by time, keep non-timed lines in original order (they'll stay unhighlighted)
        return out.sortedWith(compareBy(nullsFirst()) { it.timeMs })
    }
}
