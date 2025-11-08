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
    private val offsetRegex = Regex("""\[\s*offset\s*:\s*(-?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<String>): List<LrcLine> {
        val out = mutableListOf<LrcLine>()

        // Detect optional global offset in milliseconds (per file only)
        var globalOffsetMs = 0
        for (raw in lines) {
            val m = offsetRegex.find(raw)
            if (m != null) {
                globalOffsetMs = m.groupValues[1].toIntOrNull() ?: 0
                break
            }
        }

        for (raw in lines) {
            // Skip metadata line itself so it never appears in UI
            if (offsetRegex.containsMatchIn(raw)) continue

            val matches = timeRegex.findAll(raw).toList()
            val text = raw.replace(timeRegex, "").trim()
            val timeMs = matches.firstOrNull()?.let { m ->
                val mm = m.groupValues[1].toInt()
                val ss = m.groupValues[2].toInt()
                val frac = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
                val extraMs = when (frac?.length ?: 0) {
                    1 -> (frac!!.toIntOrNull() ?: 0) * 100
                    2 -> (frac!!.toIntOrNull() ?: 0) * 10
                    3 -> (frac!!.toIntOrNull() ?: 0)
                    else -> 0
                }
                (mm * 60 + ss) * 1000 + extraMs
            }

            // Apply per-file offset; keep >= 0
            val adjusted = timeMs?.let { (it + globalOffsetMs).coerceAtLeast(0) }
            out.add(LrcLine(timeMs = adjusted, text = text))
        }
        // Preserve original order; do not sort
        return out
    }
}
