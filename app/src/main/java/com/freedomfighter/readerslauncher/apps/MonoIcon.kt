package com.freedomfighter.readerslauncher.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

/**
 * Renders an app icon as a monochrome glyph in the foreground colour.
 *
 * Preference order:
 *  1. the adaptive icon's monochrome layer (Android 13+, what "themed icons" use) — clean silhouette;
 *  2. the adaptive icon's foreground layer alone, as a silhouette (holes painted in the
 *     background colour are cut out);
 *  3. a legacy icon: a shape on transparency becomes a silhouette, a full-bleed square has its
 *     dominant edge colour removed and the rest becomes ink.
 */
object MonoIcon {
    fun render(drawable: Drawable, sizePx: Int, foreground: Int, background: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome
            if (mono != null) {
                val m = mono.mutate()
                m.colorFilter = PorterDuffColorFilter(foreground, PorterDuff.Mode.SRC_IN)
                // Monochrome layers are designed with the same 108/72 safe zone as foregrounds.
                val inset = (sizePx * 0.12f).toInt()
                m.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                m.draw(canvas)
                return bmp.asImageBitmap()
            }
        }

        val source: Drawable = if (drawable is AdaptiveIconDrawable) {
            drawable.foreground ?: drawable
        } else drawable

        // Adaptive icons: the background layer tells us which colour counts as "paper", so a
        // foreground that paints holes in that colour (a lens, a letter counter) keeps them.
        var paper: Int? = null
        if (drawable is AdaptiveIconDrawable) {
            drawable.background?.let { bgLayer ->
                val small = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                val b = bgLayer.mutate()
                b.setBounds(-2, -2, 10, 10)
                b.draw(Canvas(small))
                var r = 0; var g = 0; var bl = 0; var n = 0
                for (y in 0 until 8) for (x in 0 until 8) {
                    val c = small.getPixel(x, y)
                    if ((c ushr 24) < 200) continue
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bl += c and 0xFF; n++
                }
                if (n >= 32) paper = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (bl / n)
            }
        }

        // Draw the source at full colour into a scratch bitmap.
        val scratch = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val sc = Canvas(scratch)
        val s = source.mutate()
        if (drawable is AdaptiveIconDrawable) {
            val inset = (sizePx * 0.18f).toInt()
            s.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        } else {
            val pad = (sizePx * 0.08f).toInt()
            s.setBounds(pad, pad, sizePx - pad, sizePx - pad)
        }
        s.draw(sc)

        // Convert to a glyph. Two cases:
        //  - a shape on a transparent background (most adaptive foregrounds, modern legacy icons):
        //    every opaque pixel becomes ink → a solid silhouette;
        //  - a full-bleed square (old legacy icons): the dominant edge colour is the "paper";
        //    pixels that differ from it become ink, the rest turn transparent.
        val px = IntArray(sizePx * sizePx)
        scratch.getPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        val inner = (sizePx * 0.15f).toInt()
        var opaque = 0
        var innerCount = 0
        for (y in inner until sizePx - inner) for (x in inner until sizePx - inner) {
            innerCount++
            if ((px[y * sizePx + x] ushr 24) > 40) opaque++
        }
        val coverage = if (innerCount == 0) 0f else opaque.toFloat() / innerCount
        val fgRgb = foreground and 0x00FFFFFF
        fun dist(c: Int, pr: Int, pg: Int, pb: Int): Int = maxOf(
            abs(((c shr 16) and 0xFF) - pr),
            abs(((c shr 8) and 0xFF) - pg),
            abs((c and 0xFF) - pb)
        )
        if (coverage < 0.85f) {
            val p = paper
            for (i in px.indices) {
                val c = px[i]
                val a = c ushr 24
                // Soft shadows and anti-aliased fringes go; holes painted in the paper colour go.
                val keep = a >= 110 && (p == null || dist(c, (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) > 60)
                px[i] = if (keep) (a shl 24) or fgRgb else 0
            }
        } else {
            // Paper colour = adaptive background if known, else the average of opaque pixels
            // along the border of the opaque area.
            var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
            val ring = (sizePx * 0.06f).toInt().coerceAtLeast(1)
            for (y in 0 until sizePx) for (x in 0 until sizePx) {
                val c = px[y * sizePx + x]
                if ((c ushr 24) < 200) continue
                val onRing = x < inner + ring || x >= sizePx - inner - ring || y < inner + ring || y >= sizePx - inner - ring
                if (!onRing) continue
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
            }
            val p = paper
            val pr = if (p != null) (p shr 16) and 0xFF else if (n == 0) 0 else (rs / n).toInt()
            val pg = if (p != null) (p shr 8) and 0xFF else if (n == 0) 0 else (gs / n).toInt()
            val pb = if (p != null) p and 0xFF else if (n == 0) 0 else (bs / n).toInt()
            for (i in px.indices) {
                val c = px[i]
                val a = c ushr 24
                if (a == 0) continue
                val d = dist(c, pr, pg, pb)
                val k = ((d - 40) / 70.0).coerceIn(0.0, 1.0)
                val outA = (a * k).toInt().coerceIn(0, 255)
                px[i] = if (outA == 0) 0 else (outA shl 24) or fgRgb
            }
        }
        scratch.setPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        canvas.drawBitmap(scratch, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return bmp.asImageBitmap()
    }

}
