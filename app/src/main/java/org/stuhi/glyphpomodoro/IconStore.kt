package org.stuhi.glyphpomodoro

import android.content.Context

/**
 * User-editable play/pause icons, each stored as an ordered list of full-matrix frames.
 *
 * One frame → shown as a static icon. Two or more → played as an animation at
 * [frameDurationMs]. Shared singleton (same process), persisted to survive process death.
 */
object IconStore {

    enum class Icon { PLAY, PAUSE, RESET }

    private const val DEFAULT_DUR = 150

    private var cache: MutableMap<Icon, MutableList<BooleanArray>>? = null
    private var dur = DEFAULT_DUR
    private var holdFirst = 0
    private var holdLast = 0

    fun frameDurationMs(ctx: Context): Int { ensure(ctx); return dur }
    fun holdFirstMs(ctx: Context): Int { ensure(ctx); return holdFirst }
    fun holdLastMs(ctx: Context): Int { ensure(ctx); return holdLast }

    fun setFrameDurationMs(ctx: Context, v: Int) {
        ensure(ctx)
        dur = v.coerceIn(20, 2000)
        prefs(ctx).edit().putInt("dur", dur).apply()
    }

    fun setHoldFirstMs(ctx: Context, v: Int) {
        ensure(ctx)
        holdFirst = v.coerceIn(0, 3000)
        prefs(ctx).edit().putInt("hold_first", holdFirst).apply()
    }

    fun setHoldLastMs(ctx: Context, v: Int) {
        ensure(ctx)
        holdLast = v.coerceIn(0, 3000)
        prefs(ctx).edit().putInt("hold_last", holdLast).apply()
    }

    /** A ready-to-play plan: frame timeline with first/last holds, and the cycle length. */
    class AnimPlan(
        val frames: List<BooleanArray>,
        private val durMs: Int,
        private val holdFirst: Int,
        private val holdLast: Int,
    ) {
        private fun frameMs(i: Int): Int =
            durMs + (if (i == 0) holdFirst else 0) + (if (i == frames.lastIndex) holdLast else 0)

        val cycleMs: Long = frames.indices.sumOf { frameMs(it).toLong() }

        /** The frame to show at [elapsed] ms into the marker. Plays once, then holds the last frame. */
        fun frameAt(elapsed: Long): BooleanArray {
            if (frames.size <= 1) return frames[0]
            var t = elapsed.coerceAtLeast(0)
            for (i in frames.indices) {
                val d = frameMs(i)
                if (t < d) return frames[i]
                t -= d
            }
            return frames.last()
        }
    }

    fun animPlan(ctx: Context, icon: Icon): AnimPlan {
        ensure(ctx)
        return AnimPlan(frames(ctx, icon), dur.coerceAtLeast(20), holdFirst, holdLast)
    }

    fun frameCount(ctx: Context, icon: Icon): Int = ensure(ctx)[icon]!!.size

    fun frames(ctx: Context, icon: Icon): List<BooleanArray> = ensure(ctx)[icon]!!.map { it.copyOf() }

    fun getFrame(ctx: Context, icon: Icon, index: Int): BooleanArray {
        val list = ensure(ctx)[icon]!!
        return list[index.coerceIn(0, list.size - 1)].copyOf()
    }

    fun setFrame(ctx: Context, icon: Icon, index: Int, cells: BooleanArray) {
        val list = ensure(ctx)[icon]!!
        if (index in list.indices) { list[index] = cells.copyOf(); persist(ctx, icon) }
    }

    fun addFrame(ctx: Context, icon: Icon) {
        ensure(ctx)[icon]!!.add(BooleanArray(cellCount()))
        persist(ctx, icon)
    }

    fun removeFrame(ctx: Context, icon: Icon, index: Int) {
        val list = ensure(ctx)[icon]!!
        if (list.size > 1 && index in list.indices) { list.removeAt(index); persist(ctx, icon) }
    }

    private fun ensure(ctx: Context): MutableMap<Icon, MutableList<BooleanArray>> {
        cache?.let { return it }
        val p = prefs(ctx)
        dur = p.getInt("dur", DEFAULT_DUR)
        holdFirst = p.getInt("hold_first", 0)
        holdLast = p.getInt("hold_last", 0)
        val n2 = cellCount()
        val map = mutableMapOf<Icon, MutableList<BooleanArray>>()
        for (icon in Icon.entries) {
            val count = p.getInt("${icon}_count", 0)
            val list = mutableListOf<BooleanArray>()
            if (count <= 0) {
                list.add(defaultFrame(icon, n2))
            } else {
                for (f in 0 until count) {
                    val s = p.getString("${icon}_$f", null)
                    list.add(
                        if (s != null && s.length == n2) BooleanArray(n2) { s[it] == '1' }
                        else defaultFrame(icon, n2)
                    )
                }
            }
            map[icon] = list
        }
        cache = map
        return map
    }

    private fun persist(ctx: Context, icon: Icon) {
        val list = ensure(ctx)[icon]!!
        val e = prefs(ctx).edit()
        e.putInt("${icon}_count", list.size)
        list.forEachIndexed { i, f -> e.putString("${icon}_$i", f.joinToString("") { if (it) "1" else "0" }) }
        e.apply()
    }

    private fun defaultFrame(icon: Icon, n2: Int): BooleanArray {
        val arr = when (icon) {
            Icon.PLAY -> MatrixRenderer.play(255)
            Icon.PAUSE -> MatrixRenderer.pause(255)
            Icon.RESET -> MatrixRenderer.edge(255) // a ring as a starting point
        }
        return BooleanArray(n2) { it < arr.size && arr[it] > 0 }
    }

    private fun cellCount(): Int = MatrixRenderer.size() * MatrixRenderer.size()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("icons", Context.MODE_PRIVATE)
}
