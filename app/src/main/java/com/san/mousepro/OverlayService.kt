package com.san.mousepro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Overlay: tombol melayang "SM" + panel + kursor + editor layout. */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "san.start"
        const val ACTION_STOP = "san.stop"
        @Volatile var running = false
    }

    private lateinit var wm: WindowManager
    private var pkg = ""
    private var cfg = Config()
    private var screenW = 1920
    private var screenH = 1080
    private var gameMode = true

    private var bubble: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private var panel: View? = null
    private var modeBtn: Button? = null
    private var cursor: CursorView? = null
    private lateinit var cursorLp: WindowManager.LayoutParams
    private var editor: EditorView? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (i?.action == ACTION_STOP) { teardown(); stopSelf(); return START_NOT_STICKY }
        pkg = i?.getStringExtra("pkg") ?: Store.lastGame
        foreground()
        if (!running) {
            running = true
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            measure()
            buildBubble()
            buildCursor()
            Engine.modeListener = { g -> onMode(g) }
            Engine.cursorListener = { x, y -> moveCursor(x, y) }
            Engine.statusListener = { m -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show() }
        }
        cfg = Store.load(this, pkg)
        Engine.connect(this) { Engine.start(this, cfg) }
        return START_NOT_STICKY
    }

    override fun onDestroy() { teardown(); super.onDestroy() }

    // ---------- util ----------

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("san", "San Mouse Pro", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "san")
            .setContentTitle("San Mouse Pro aktif")
            .setContentText("Mapping mouse & keyboard berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    @Suppress("DEPRECATION")
    private fun measure() {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        screenW = max(dm.widthPixels, dm.heightPixels)
        screenH = min(dm.widthPixels, dm.heightPixels)
        Engine.screenW = screenW; Engine.screenH = screenH
    }

    private fun lp(w: Int, h: Int, flags: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28)
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return p
    }

    private fun saveAndPush() {
        Store.save(this, pkg, cfg)
        Engine.push(cfg)
    }

    private fun teardown() {
        if (!running) return
        running = false
        Engine.stop()
        Engine.modeListener = null; Engine.cursorListener = null; Engine.statusListener = null
        closePanel(); closeEditor()
        try { bubble?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { cursor?.let { wm.removeView(it) } } catch (_: Throwable) {}
        bubble = null; cursor = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ---------- bubble ----------

    private fun buildBubble() {
        val v = TextView(this)
        v.text = "SM"; v.gravity = Gravity.CENTER; v.textSize = 15f
        v.setTextColor(Ui.CYAN); v.typeface = Typeface.DEFAULT_BOLD
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL; bg.setColor(0xCC0A0E14.toInt()); bg.setStroke(dp(2), Ui.CYAN)
        v.background = bg

        val p = lp(dp(52), dp(52), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        p.x = dp(12); p.y = dp(120)
        bubbleLp = p

        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bubbleLp.x; oy = bubbleLp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            bubbleLp.x = ox + dx; bubbleLp.y = oy + dy
                            try { wm.updateViewLayout(view, bubbleLp) } catch (_: Throwable) {}
                            if (panel != null) closePanel()
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) { if (panel != null) closePanel() else openPanel() }
                }
                return true
            }
        })
        wm.addView(v, p)
        bubble = v
    }

    // ---------- cursor ----------

    private fun buildCursor() {
        val v = CursorView(this)
        val p = lp(dp(24), dp(24),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        p.x = screenW / 2; p.y = screenH / 2
        cursorLp = p
        v.visibility = View.INVISIBLE
        wm.addView(v, p)
        cursor = v
    }

    private fun applyCursorVisibility() {
        cursor?.visibility = if (!gameMode && cfg.cursorVisible) View.VISIBLE else View.INVISIBLE
    }

    private fun moveCursor(x: Float, y: Float) {
        val c = cursor ?: return
        cursorLp.x = x.toInt(); cursorLp.y = y.toInt()
        try { wm.updateViewLayout(c, cursorLp) } catch (_: Throwable) {}
    }

    private fun onMode(g: Boolean) {
        gameMode = g
        applyCursorVisibility()
        modeBtn?.text = modeLabel()
    }

    private fun modeLabel() =
        if (gameMode) "MODE: GAME  (tekan ~ → kursor)" else "MODE: KURSOR  (tekan ~ → game)"

    // ---------- panel ----------

    private fun sensRow(name: String, init: Float, set: (Float) -> Unit): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val tv = Ui.text(this, name + ": " + String.format("%.2f", init), 12f)
        val sb = SeekBar(this)
        sb.max = 290
        sb.progress = ((init - 0.1f) * 100).toInt().coerceIn(0, 290)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 0.1f + p / 100f
                tv.text = name + ": " + String.format("%.2f", v)
                if (fromUser) set(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { saveAndPush() }
        })
        col.addView(tv); col.addView(sb)
        return col
    }

    private fun openPanel() {
        cfg = Store.load(this, pkg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = Ui.box(this, 0xEE0A0E14.toInt(), Ui.CYAN, 14, 2)
        root.setPadding(dp(14), dp(10), dp(14), dp(12))

        fun gap(v: View) {
            val l = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            l.topMargin = dp(6)
            root.addView(v, l)
        }

        root.addView(Ui.text(this, "SAN MOUSE PRO", 14f, Ui.CYAN, true))
        val mb = Ui.btn(this, modeLabel()) { Engine.setActive(!gameMode) }
        modeBtn = mb
        gap(mb)
        gap(sensRow("Sens X", cfg.sensX) { cfg.sensX = it })
        gap(sensRow("Sens Y", cfg.sensY) { cfg.sensY = it })
        gap(Ui.btn(this, "Edit Layout", Ui.GREEN) { openEditor() })
        val cb = Ui.btn(this, "") { }
        cb.text = if (cfg.cursorVisible) "Kursor: TAMPIL" else "Kursor: SEMBUNYI"
        cb.setOnClickListener {
            cfg.cursorVisible = !cfg.cursorVisible
            cb.text = if (cfg.cursorVisible) "Kursor: TAMPIL" else "Kursor: SEMBUNYI"
            saveAndPush(); applyCursorVisibility()
        }
        gap(cb)
        gap(Ui.btn(this, "Stop & tutup", Ui.PINK) { teardown(); stopSelf() })

        val w = dp(270)
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val p = lp(w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        p.x = (bubbleLp.x + dp(60)).coerceAtMost(screenW - w - dp(8))
        p.y = bubbleLp.y.coerceAtMost(screenH - root.measuredHeight - dp(8)).coerceAtLeast(0)
        wm.addView(root, p)
        panel = root
        topCursor()
    }

    private fun closePanel() {
        try { panel?.let { wm.removeView(it) } } catch (_: Throwable) {}
        panel = null; modeBtn = null
    }

    /** Pastikan kursor selalu di atas jendela lain. */
    private fun topCursor() {
        val c = cursor ?: return
        try { wm.removeView(c); wm.addView(c, cursorLp) } catch (_: Throwable) {}
    }

    // ---------- editor ----------

    private fun openEditor() {
        closePanel()
        cfg = Store.load(this, pkg)
        Engine.setActive(false)               // mode kursor saat edit (mouse bisa dipakai)
        val copy = Config.fromJson(cfg.toJson())
        val ed = EditorView(this, copy, screenW, screenH) { save ->
            if (save) { cfg = copy; saveAndPush() }
            closeEditor()
            Engine.setActive(true)
        }
        wm.addView(ed, lp(screenW, screenH, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE))
        editor = ed
        topCursor()
    }

    private fun closeEditor() {
        try { editor?.let { wm.removeView(it) } } catch (_: Throwable) {}
        editor = null
    }
}
