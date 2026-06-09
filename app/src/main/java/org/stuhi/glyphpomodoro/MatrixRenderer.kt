package org.stuhi.glyphpomodoro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.nothing.ketchum.Common
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Builds matrix content: a centered minutes bitmap (work), the lit outer edge (break),
 * blank, and the transient play/pause icons shown on a mode change.
 *
 * NOTE: the exact frame layout / circular masking for the 13×13 Phone (4a) Pro matrix is an
 * on-device verification item (PLAN.md §10). Row-major n*n is the working assumption.
 */
object MatrixRenderer {

    fun size(): Int = try {
        Common.getDeviceMatrixLength().coerceAtLeast(1)
    } catch (t: Throwable) {
        13 // Phone (4a) Pro fallback when running off a real device
    }

    fun blank(): IntArray = IntArray(size() * size())

    /** Whether LED (x, y) physically exists — the matrix is a circle, so corners don't. */
    fun inCircle(x: Int, y: Int, n: Int = size()): Boolean {
        val c = (n - 1) / 2.0
        return hypot(x - c, y - c) <= c + 0.5
    }

    /** A minutes number drawn centered into a matrix-sized bitmap. */
    fun textBitmap(text: String, brightness: Int): Bitmap {
        val n = size()
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val b = brightness.coerceIn(0, 255)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(b, b, b)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = n * 0.95f
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val maxW = n - 1f
        if (bounds.width() > maxW) paint.textSize *= maxW / bounds.width()
        val fm = paint.fontMetrics
        val x = n / 2f
        val y = n / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, x, y, paint)
        return bmp
    }

    /** Lights only the single outermost layer of LEDs — used during breaks. */
    fun edge(brightness: Int): IntArray {
        val n = size()
        val out = IntArray(n * n)
        val c = (n - 1) / 2.0
        val rOuter = c
        val b = brightness.coerceAtLeast(0)
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (abs(hypot(x - c, y - c) - rOuter) <= 0.5) out[y * n + x] = b
            }
        }
        return out
    }

    /** Combines two frames, taking the brighter LED at each position. */
    fun merge(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(a.size)
        for (i in a.indices) out[i] = maxOf(a[i], b[i])
        return out
    }

    /** Right-pointing triangle (▶), centered. */
    fun play(brightness: Int): IntArray {
        val n = size()
        val out = IntArray(n * n)
        val c = (n - 1) / 2
        val r = (c - 1).coerceAtLeast(1)
        val b = brightness.coerceIn(0, 255)
        for (xx in -r..r) {
            for (yy in -r..r) {
                if (abs(yy) <= (r - xx) / 2.0) out[(c + yy) * n + (c + xx)] = b
            }
        }
        return out
    }

    /** Two vertical bars (⏸), centered. */
    fun pause(brightness: Int): IntArray {
        val n = size()
        val out = IntArray(n * n)
        val c = (n - 1) / 2
        val r = (c - 1).coerceAtLeast(1)
        val b = brightness.coerceIn(0, 255)
        for (yy in -r..r) {
            for (dx in intArrayOf(-3, -2, 2, 3)) {
                val x = c + dx
                if (x in 0 until n) out[(c + yy) * n + x] = b
            }
        }
        return out
    }
}
