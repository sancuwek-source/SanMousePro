package com.san.mousepro

import android.content.Context

/** Penyimpanan layout per game (kunci = nama paket). */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("san", Context.MODE_PRIVATE)

    fun load(c: Context, pkg: String): Config = Config.fromJson(sp(c).getString("cfg_$pkg", null))
    fun save(c: Context, pkg: String, cfg: Config) {
        sp(c).edit().putString("cfg_$pkg", cfg.toJson()).apply()
    }

    private var ctxRef: Context? = null
    fun init(c: Context) { ctxRef = c.applicationContext }

    var lastGame: String
        get() = ctxRef?.let { sp(it).getString("last", "") } ?: ""
        set(v) { ctxRef?.let { sp(it).edit().putString("last", v).apply() } }
}
