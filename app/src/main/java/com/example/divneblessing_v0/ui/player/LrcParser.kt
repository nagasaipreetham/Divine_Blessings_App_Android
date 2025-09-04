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

    private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lines: List<String>): List<LrcLine> {
        val out = mutableListOf<LrcLine>()
        for (raw in lines) {
            val matches = timeRegex.findAll(raw).toList()
            val text = raw.replace(timeRegex, "").trim()
            val timeMs = matches.firstOrNull()?.let { m ->
                val mm = m.groupValues[1].toInt()
                val ss = m.groupValues[2].toInt()
                val frac = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                val extraMs = when (frac?.length ?: 0) {
                    1 -> (frac!!.toIntOrNull() ?: 0) * 100     // .x → deciseconds
                    2 -> (frac!!.toIntOrNull() ?: 0) * 10      // .xx → centiseconds
                    3 -> (frac!!.toIntOrNull() ?: 0)           // .xxx → milliseconds
                    else -> 0
                }
                (mm * 60 + ss) * 1000 + extraMs
            }
            out.add(LrcLine(timeMs = timeMs, text = text))
        }
        return out.sortedWith(compareBy(nullsFirst()) { it.timeMs })
    }
}
