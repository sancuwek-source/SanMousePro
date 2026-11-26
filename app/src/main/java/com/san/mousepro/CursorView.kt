package com.san.mousepro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Kursor bawaan San Mouse Pro (digambar sendiri; bisa disembunyikan). */
class CursorView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path().apply {
        moveTo(2f, 2f); lineTo(2f, 34f); lineTo(11f, 26f); lineTo(18f, 40f)
        lineTo(24f, 37f); lineTo(17f, 23f); lineTo(30f, 23f); close()
    }

    override fun onDraw(c: Canvas) {
        val s = width / 44f
        c.scale(s, s)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f; p.color = Ui.CYAN; c.drawPath(path, p)
    }
}
