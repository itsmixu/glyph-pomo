package org.stuhi.glyphpomodoro

import android.content.Context

/**
 * A user-editable bitmap font for the digits 0–9.
 *
 * Each digit is drawn on a fixed [W]×[H] grid in the in-app editor. When rendering a number
 * we auto-crop each digit to the columns it actually uses, then pack the digits side by side
 * (with a 1-column gap) and center the result on the matrix — so digit widths can differ.
 *
 * Shared singleton: the editor and the toy service run in the same process, so edits show on
 * the matrix immediately; patterns are also persisted so they survive process death.
 */
object DigitFont {

    const val W = 5
    const val H = 9
    private const val GAP = 1

    private var cache: Array<BooleanArray>? = null

    /** The grid for [digit] (row-major, length W*H), loading defaults on first use. */
    fun cells(ctx: Context, digit: Int): BooleanArray = ensure(ctx)[digit].copyOf()

    fun set(ctx: Context, digit: Int, grid: BooleanArray) {
        ensure(ctx)[digit] = grid.copyOf()
        prefs(ctx).edit()
            .putString("digit_$digit", grid.joinToString("") { if (it) "1" else "0" })
            .apply()
    }

    /** Renders [text] (digits only) centered, each digit cropped to its drawn width. */
    fun renderNumber(ctx: Context, text: String, n: Int, brightness: Int): IntArray {
        val out = IntArray(n * n)
        val font = ensure(ctx)
        val b = brightness.coerceIn(0, 255)

        val glyphs = text.mapNotNull { ch ->
            val d = ch - '0'
            if (d in 0..9) font[d] else null
        }
        if (glyphs.isEmpty()) return out

        val spans = glyphs.map { cropX(it) }
        val totalW = spans.sumOf { it.second - it.first + 1 } + GAP * (glyphs.size - 1)
        var cx = (n - totalW) / 2
        val startY = (n - H) / 2

        glyphs.forEachIndexed { i, cells ->
            val (x0, x1) = spans[i]
            for (y in 0 until H) {
                for (x in x0..x1) {
                    if (cells[y * W + x]) {
                        val px = cx + (x - x0)
                        val py = startY + y
                        if (px in 0 until n && py in 0 until n) out[py * n + px] = b
                    }
                }
            }
            cx += (x1 - x0 + 1) + GAP
        }
        return out
    }

    /** Used-column bounds (inclusive). Falls back to a 2-wide blank for an empty digit. */
    private fun cropX(cells: BooleanArray): Pair<Int, Int> {
        var lo = W
        var hi = -1
        for (x in 0 until W) {
            var used = false
            for (y in 0 until H) if (cells[y * W + x]) { used = true; break }
            if (used) { if (x < lo) lo = x; if (x > hi) hi = x }
        }
        return if (hi < 0) 0 to 1 else lo to hi
    }

    private fun ensure(ctx: Context): Array<BooleanArray> {
        cache?.let { return it }
        val p = prefs(ctx)
        val arr = Array(10) { d ->
            val s = p.getString("digit_$d", null)
            if (s != null && s.length == W * H) BooleanArray(W * H) { s[it] == '1' }
            else defaultGlyph(d)
        }
        cache = arr
        return arr
    }

    /** Built-in font (5×9, row-major). */
    private fun defaultGlyph(d: Int): BooleanArray {
        val s = DEFAULTS[d]
        return BooleanArray(W * H) { it < s.length && s[it] == '1' }
    }

    private val DEFAULTS = arrayOf(
        "000000000001110010100101001010011100000000000", // 0
        "000000000000100011000010000100011100000000000", // 1
        "000000000001110000100111001000011100000000000", // 2
        "000000000001110000100111000010011100000000000", // 3
        "000000000001010010100111000010000100000000000", // 4
        "000000000001110010000111000010011100000000000", // 5
        "000000000001110010000111001010011100000000000", // 6
        "000000000001110000100001000010000100000000000", // 7
        "000000000001110010100111001010011100000000000", // 8
        "000000000001110010100111000010011100000000000", // 9
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("digitfont", Context.MODE_PRIVATE)
}
