package com.san.mousepro

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/**
 * Editor layout layar penuh: geser joystick, area kamera, dan tombol.
 * Bisa dipakai dengan sentuhan maupun kursor mouse (mode kursor).
 */
class EditorView(
    ctx: Context,
    val cfg: Config,
    private val sw: Int,
    private val sh: Int,
    private val onDone: (Boolean) -> Unit
) : FrameLayout(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private var sel = -1            // -1 kosong, -2 joystick, -3 area kamera, >=0 indeks tombol
    private var offX = 0f
    private var offY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTxt = TextView(ctx)
    private val btnR = 30f * d

    private val canvasView = object : View(ctx) {
        override fun onDraw(c: Canvas) { drawAll(c) }
        override fun onTouchEvent(e: MotionEvent): Boolean = touch(e)
    }

    init {
        addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
        bar.setBackgroundColor(0xEE0A0E14.toInt())

        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = (8 * d).toInt()
            bar.addView(v, lp)
        }
        add(Ui.btn(ctx, "+ Tombol", Ui.GREEN) { addButton() })
        add(Ui.btn(ctx, "Ukuran −") { resize(0.92f) })
        add(Ui.btn(ctx, "Ukuran +") { resize(1.08f) })
        add(Ui.btn(ctx, "◀") { cycleKey(-1) })
        keyTxt.setTextColor(Ui.TEXT); keyTxt.textSize = 13f
        keyTxt.minWidth = (110 * d).toInt(); keyTxt.gravity = android.view.Gravity.CENTER
        add(keyTxt)
        add(Ui.btn(ctx, "▶") { cycleKey(1) })
        add(Ui.btn(ctx, "Hapus", Ui.PINK) { deleteSel() })
        add(Ui.btn(ctx, "Reset") { resetLayout() })
        add(Ui.btn(ctx, "SIMPAN", Ui.GREEN) { onDone(true) })
        add(Ui.btn(ctx, "Batal", Ui.PINK) { onDone(false) })

        val scroll = HorizontalScrollView(ctx)
        scroll.addView(bar)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refreshKey()
    }

    // ---------- aksi toolbar ----------

    private fun addButton() {
        val used = cfg.buttons.map { it.key }.toSet()
        val key = Keys.codes.firstOrNull { it !in used && it != cfg.toggleKey } ?: 16
        cfg.buttons.add(Btn(key, 0.5f, 0.5f, Keys.name(key)))
        sel = cfg.buttons.size - 1
        refreshKey(); canvasView.invalidate()
    }

    private fun deleteSel() {
        if (sel >= 0 && sel < cfg.buttons.size) { cfg.buttons.removeAt(sel); sel = -1 }
        refreshKey(); canvasView.invalidate()
    }

    private fun resetLayout() {
        val fresh = Config()
        cfg.joyX = fresh.joyX; cfg.joyY = fresh.joyY; cfg.joyR = fresh.joyR
        cfg.lookX = fresh.lookX; cfg.lookY = fresh.lookY; cfg.lookW = fresh.lookW; cfg.lookH = fresh.lookH
        cfg.buttons.clear(); cfg.buttons.addAll(Config.defaultButtons().map { it.copy() })
        sel = -1; refreshKey(); canvasView.invalidate()
    }

    private fun cycleKey(dir: Int) {
        if (sel < 0 || sel >= cfg.buttons.size) return
        val b = cfg.buttons[sel]
        val codes = Keys.codes
        val i = codes.indexOf(b.key).coerceAtLeast(0)
        val nk = codes[(i + dir + codes.size) % codes.size]
        b.key = nk; b.label = Keys.name(nk)
        refreshKey(); canvasView.invalidate()
    }

    private fun resize(f: Float) {
        when {
            sel == -2 -> cfg.joyR = (cfg.joyR * f).coerceIn(0.05f, 0.30f)
            sel == -3 -> {
                val cx = cfg.lookX + cfg.lookW / 2f
                val cy = cfg.lookY + cfg.lookH / 2f
                cfg.lookW = (cfg.lookW * f).coerceIn(0.10f, 0.95f)
                cfg.lookH = (cfg.lookH * f).coerceIn(0.10f, 0.95f)
                cfg.lookX = (cx - cfg.lookW / 2f).coerceIn(0f, 1f - cfg.lookW)
                cfg.lookY = (cy - cfg.lookH / 2f).coerceIn(0f, 1f - cfg.lookH)
            }
        }
        canvasView.invalidate()
    }

    private fun refreshKey() {
        keyTxt.text = when {
            sel >= 0 && sel < cfg.buttons.size -> "Tombol: " + Keys.name(cfg.buttons[sel].key)
            sel == -2 -> "Joystick (W A S D)"
            sel == -3 -> "Area kamera (mouse)"
            else -> "Pilih item"
        }
    }

    // ---------- gambar ----------

    private fun drawAll(c: Canvas) {
        c.drawColor(0x99000000.toInt())
        paint.textAlign = Paint.Align.CENTER

        // area kamera
        val l = cfg.lookX * sw
        val t = cfg.lookY * sh
        val r = l + cfg.lookW * sw
        val b = t + cfg.lookH * sh
        paint.style = Paint.Style.FILL
        paint.color = if (sel == -3) 0x4000E5FF else 0x1E00E5FF
        c.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * d
        paint.color = if (sel == -3) 0xFFFFE600.toInt() else Ui.CYAN
        paint.pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
        c.drawRect(l, t, r, b, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL; paint.color = Ui.CYAN; paint.textSize = 14f * d
        c.drawText("AREA KAMERA (gerak mouse)", (l + r) / 2f, t + 22f * d, paint)

        // joystick
        val jx = cfg.joyX * sw
        val jy = cfg.joyY * sh
        val jr = cfg.joyR * sh
        paint.style = Paint.Style.FILL; paint.color = 0x2239FF14
        c.drawCircle(jx, jy, jr, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d
        paint.color = if (sel == -2) 0xFFFFE600.toInt() else Ui.GREEN
        c.drawCircle(jx, jy, jr, paint)
        paint.style = Paint.Style.FILL; paint.textSize = 16f * d; paint.color = Ui.GREEN
        c.drawText("W A S D", jx, jy + 6f * d, paint)

        // tombol
        for ((i, bt) in cfg.buttons.withIndex()) {
            val x = bt.x * sw
            val y = bt.y * sh
            paint.style = Paint.Style.FILL; paint.color = 0xCC0A0E14.toInt()
            c.drawCircle(x, y, btnR, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d
            paint.color = if (i == sel) 0xFFFFE600.toInt() else Ui.PINK
            c.drawCircle(x, y, btnR, paint)
            paint.style = Paint.Style.FILL; paint.color = Ui.TEXT; paint.textSize = 11f * d
            c.drawText(Keys.name(bt.key), x, y + 4f * d, paint)
            paint.color = Ui.MUTED; paint.textSize = 10f * d
            c.drawText(bt.label, x, y + btnR + 14f * d, paint)
        }
    }

    // ---------- sentuhan ----------

    private fun hit(x: Float, y: Float): Int {
        for (i in cfg.buttons.indices.reversed()) {
            val b = cfg.buttons[i]
            if (hypot(x - b.x * sw, y - b.y * sh) < btnR * 1.3f) return i
        }
        if (hypot(x - cfg.joyX * sw, y - cfg.joyY * sh) < cfg.joyR * sh) return -2
        val l = cfg.lookX * sw; val t = cfg.lookY * sh
        if (x in l..(l + cfg.lookW * sw) && y in t..(t + cfg.lookH * sh)) return -3
        return -1
    }

    private fun centerOf(s: Int): Pair<Float, Float> = when {
        s == -2 -> Pair(cfg.joyX * sw, cfg.joyY * sh)
        s == -3 -> Pair((cfg.lookX + cfg.lookW / 2f) * sw, (cfg.lookY + cfg.lookH / 2f) * sh)
        s >= 0 -> Pair(cfg.buttons[s].x * sw, cfg.buttons[s].y * sh)
        else -> Pair(0f, 0f)
    }

    private fun touch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sel = hit(e.x, e.y)
                val (cx, cy) = centerOf(sel)
                offX = e.x - cx; offY = e.y - cy
                refreshKey(); canvasView.invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (sel != -1) {
                val nx = ((e.x - offX) / sw).coerceIn(0f, 1f)
                val ny = ((e.y - offY) / sh).coerceIn(0f, 1f)
                when {
                    sel == -2 -> { cfg.joyX = nx; cfg.joyY = ny }
                    sel == -3 -> {
                        cfg.lookX = (nx - cfg.lookW / 2f).coerceIn(0f, 1f - cfg.lookW)
                        cfg.lookY = (ny - cfg.lookH / 2f).coerceIn(0f, 1f - cfg.lookH)
                    }
                    sel >= 0 -> { cfg.buttons[sel].x = nx; cfg.buttons[sel].y = ny }
                }
                canvasView.invalidate()
            }
        }
        return true
    }
}
