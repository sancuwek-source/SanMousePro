package com.san.mousepro

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView

object Ui {
    val BG = 0xFF0A0E14.toInt()
    val CARD = 0xFF121A24.toInt()
    val CYAN = 0xFF00E5FF.toInt()
    val PINK = 0xFFFF2E63.toInt()
    val GREEN = 0xFF39FF14.toInt()
    val TEXT = 0xFFE6F1FF.toInt()
    val MUTED = 0xFF7F93A8.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun box(c: Context, fill: Int, stroke: Int, radiusDp: Int = 12, strokeDp: Int = 1): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        if (stroke != 0) g.setStroke(dp(c, strokeDp), stroke)
        return g
    }

    fun text(c: Context, s: String, sizeSp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun btn(c: Context, label: String, accent: Int = CYAN, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = label
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(accent)
        b.typeface = Typeface.DEFAULT_BOLD
        b.background = box(c, 0xFF0F1620.toInt(), accent, 10, 2)
        b.stateListAnimator = null
        b.minHeight = 0; b.minimumHeight = 0; b.minWidth = 0; b.minimumWidth = 0
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        b.setOnClickListener { onClick() }
        return b
    }
}
