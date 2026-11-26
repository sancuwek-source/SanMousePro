package com.san.mousepro

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private var cfg = Config()
    private var games = listOf<Pair<String, String>>()
    private var pkg = ""
    private var syncing = false

    private lateinit var tvShizuku: TextView
    private lateinit var tvOverlay: TextView
    private lateinit var barX: SeekBar
    private lateinit var barY: SeekBar
    private lateinit var tvX: TextView
    private lateinit var tvY: TextView
    private lateinit var swCursor: Switch
    private lateinit var spKey: Spinner

    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
    private val onBinder = Shizuku.OnBinderReceivedListener { refresh() }
    private val onDead = Shizuku.OnBinderDeadListener { refresh() }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        loadGames()
        pkg = Store.lastGame.takeIf { p -> games.any { it.first == p } } ?: games.firstOrNull()?.first ?: ""
        cfg = Store.load(this, pkg)
        setContentView(buildUi())
        syncControls()

        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        cfg = Store.load(this, pkg)
        syncControls()
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
        super.onDestroy()
    }

    // ---------- data ----------

    private fun loadGames() {
        val known = listOf(
            "com.dts.freefireth", "com.dts.freefiremax", "com.tencent.ig", "com.pubg.imobile",
            "com.activision.callofduty.shooter", "com.garena.game.codm", "com.mobile.legends"
        )
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = packageManager.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
        games = all.sortedWith(compareBy({ if (it.first in known) 0 else 1 }, { it.second.lowercase() }))
    }

    private fun saveCfg() {
        if (pkg.isEmpty()) return
        Store.save(this, pkg, cfg)
        if (OverlayService.running) Engine.push(cfg)
    }

    private fun syncControls() {
        if (!::barX.isInitialized) return
        syncing = true
        barX.progress = ((cfg.sensX - 0.1f) * 100).toInt().coerceIn(0, 290)
        barY.progress = ((cfg.sensY - 0.1f) * 100).toInt().coerceIn(0, 290)
        tvX.text = "Sensitivitas X: " + String.format("%.2f", cfg.sensX)
        tvY.text = "Sensitivitas Y: " + String.format("%.2f", cfg.sensY)
        swCursor.isChecked = cfg.cursorVisible
        spKey.setSelection(Keys.codes.indexOf(cfg.toggleKey).coerceAtLeast(0))
        syncing = false
    }

    private fun refresh() {
        runOnUiThread {
            if (!::tvShizuku.isInitialized) return@runOnUiThread
            val installed = try { packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true } catch (e: Exception) { false }
            val running = try { Shizuku.pingBinder() } catch (t: Throwable) { false }
            val granted = running && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (t: Throwable) { false }
            tvShizuku.text = when {
                granted -> "✔ Terhubung (Shizuku aktif & diizinkan)"
                running -> "● Shizuku jalan, belum diberi izin"
                installed -> "✖ Shizuku belum dijalankan"
                else -> "✖ Shizuku belum terpasang"
            }
            tvShizuku.setTextColor(if (granted) Ui.GREEN else Ui.PINK)
            val ov = Settings.canDrawOverlays(this)
            tvOverlay.text = if (ov) "✔ Izin overlay OK" else "✖ Izin overlay belum diberikan"
            tvOverlay.setTextColor(if (ov) Ui.GREEN else Ui.PINK)
        }
    }

    // ---------- aksi ----------

    private fun shizukuAction() {
        val running = try { Shizuku.pingBinder() } catch (t: Throwable) { false }
        if (running) {
            try { Shizuku.requestPermission(100) } catch (t: Throwable) { toast("Gagal minta izin: ${t.message}") }
        } else {
            val li = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (li != null) startActivity(li)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }

    private fun startGame() {
        if (pkg.isEmpty()) { toast("Tidak ada game yang dipilih"); return }
        if (!Engine.shizukuReady()) { toast("Shizuku belum aktif / belum diizinkan"); return }
        if (!Settings.canDrawOverlays(this)) { toast("Beri izin overlay dulu"); return }
        Store.save(this, pkg, cfg)
        Store.lastGame = pkg
        startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START).putExtra("pkg", pkg))
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun stopGame() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- UI ----------

    private fun card(title: String, accent: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.box(this, Ui.CARD, accent, 14, 1)
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        c.addView(Ui.text(this, title, 15f, accent, true))
        return c
    }

    private fun add(parent: LinearLayout, v: View, top: Int = 10) {
        val l = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        l.topMargin = dp(top)
        parent.addView(v, l)
    }

    private fun darkAdapter(items: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        override fun getView(p: Int, v: View?, g: android.view.ViewGroup): View {
            val t = super.getView(p, v, g) as TextView; t.setTextColor(Ui.TEXT); return t
        }
        override fun getDropDownView(p: Int, v: View?, g: android.view.ViewGroup): View {
            val t = super.getDropDownView(p, v, g) as TextView
            t.setTextColor(Ui.TEXT); t.setBackgroundColor(Ui.CARD); return t
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.setPadding(dp(18), dp(10), dp(18), dp(12))

        val header = Ui.text(this, "SAN MOUSE PRO", 22f, Ui.CYAN, true)
        header.gravity = Gravity.CENTER
        root.addView(header)
        val sub = Ui.text(this, "Mouse & Keyboard Mapper • Landscape", 11f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        root.addView(sub)

        val cols = LinearLayout(this)
        cols.orientation = LinearLayout.HORIZONTAL
        fun colLp(): LinearLayout.LayoutParams {
            val l = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            l.setMargins(dp(6), dp(8), dp(6), 0)
            return l
        }

        // --- kolom 1: koneksi & izin ---
        val c1 = card("KONEKSI & IZIN", Ui.CYAN)
        tvShizuku = Ui.text(this, "…", 13f)
        add(c1, tvShizuku)
        add(c1, Ui.btn(this, "Hubungkan / Beri izin Shizuku") { shizukuAction() })
        tvOverlay = Ui.text(this, "…", 13f)
        add(c1, tvOverlay, 16)
        add(c1, Ui.btn(this, "Beri izin overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        add(c1, Ui.text(this, "Sambung nirkabel: Opsi Pengembang → Debugging nirkabel → Pairing dengan kode, lalu masukkan kode itu di aplikasi Shizuku.", 11f, Ui.MUTED), 16)
        add(c1, Ui.btn(this, "Buka Opsi Pengembang") {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            catch (e: Exception) { toast("Aktifkan Opsi Pengembang dulu") }
        })
        val s1 = ScrollView(this); s1.addView(c1)
        cols.addView(s1, colLp())

        // --- kolom 2: pilih game ---
        val c2 = card("PILIH GAME", Ui.PINK)
        val sp = Spinner(this)
        sp.adapter = darkAdapter(games.map { it.second })
        sp.setSelection(games.indexOfFirst { it.first == pkg }.coerceAtLeast(0))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos < games.size && games[pos].first != pkg) {
                    pkg = games[pos].first
                    cfg = Store.load(this@MainActivity, pkg)
                    syncControls()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(c2, sp)
        add(c2, Ui.text(this, "Layout & sensitivitas disimpan terpisah untuk tiap game.", 11f, Ui.MUTED))
        val start = Ui.btn(this, "▶  MULAI MAIN", Ui.GREEN) { startGame() }
        start.textSize = 17f
        add(c2, start, 18)
        add(c2, Ui.btn(this, "■  Stop overlay", Ui.PINK) { stopGame() }, 10)
        add(c2, Ui.text(this, "Di dalam game, ketuk bulatan \"SM\" untuk buka menu: mode, sensitivitas, Edit Layout (geser-geser tombol), kursor.", 11f, Ui.MUTED), 16)
        val s2 = ScrollView(this); s2.addView(c2)
        cols.addView(s2, colLp())

        // --- kolom 3: sensitivitas ---
        val c3 = card("SENSITIVITAS & KONTROL", Ui.GREEN)
        tvX = Ui.text(this, "Sensitivitas X", 13f)
        barX = SeekBar(this); barX.max = 290
        tvY = Ui.text(this, "Sensitivitas Y", 13f)
        barY = SeekBar(this); barY.max = 290
        fun bind(bar: SeekBar, tv: TextView, name: String, set: (Float) -> Unit) {
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (syncing) return
                    val v = 0.1f + p / 100f
                    set(v); tv.text = name + ": " + String.format("%.2f", v)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCfg() }
            })
        }
        bind(barX, tvX, "Sensitivitas X") { cfg.sensX = it }
        bind(barY, tvY, "Sensitivitas Y") { cfg.sensY = it }
        add(c3, tvX); add(c3, barX, 2)
        add(c3, tvY, 14); add(c3, barY, 2)

        swCursor = Switch(this)
        swCursor.text = "Tampilkan kursor San Mouse"
        swCursor.setTextColor(Ui.TEXT)
        swCursor.setOnCheckedChangeListener { _, on -> if (!syncing) { cfg.cursorVisible = on; saveCfg() } }
        add(c3, swCursor, 18)

        add(c3, Ui.text(this, "Tombol ganti mode GAME ⇄ KURSOR", 12f, Ui.MUTED), 16)
        spKey = Spinner(this)
        spKey.adapter = darkAdapter(Keys.codes.map { Keys.name(it) })
        spKey.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (syncing) return
                cfg.toggleKey = Keys.codes[pos]; saveCfg()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        add(c3, spKey, 4)
        val s3 = ScrollView(this); s3.addView(c3)
        cols.addView(s3, colLp())

        root.addView(cols, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }
}
