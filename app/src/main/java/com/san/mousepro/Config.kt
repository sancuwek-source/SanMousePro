package com.san.mousepro

import org.json.JSONArray
import org.json.JSONObject

/** Satu tombol layar yang dipetakan ke tombol keyboard/mouse. x,y = pecahan layar (0..1). */
data class Btn(var key: Int, var x: Float, var y: Float, var label: String)

class Config {
    var sensX = 1.0f
    var sensY = 1.0f
    var toggleKey = 41            // tombol ~ : ganti mode GAME <-> KURSOR
    var cursorVisible = true

    // Joystick WASD (pusat + radius sebagai pecahan tinggi layar)
    var joyX = 0.15f
    var joyY = 0.68f
    var joyR = 0.12f

    // Area kamera (kiri-atas + ukuran, pecahan layar)
    var lookX = 0.55f
    var lookY = 0.10f
    var lookW = 0.42f
    var lookH = 0.60f

    var screenW = 1920f
    var screenH = 1080f

    val buttons = ArrayList<Btn>()

    fun toJson(): String {
        val o = JSONObject()
        o.put("sx", sensX.toDouble()); o.put("sy", sensY.toDouble())
        o.put("tk", toggleKey); o.put("cv", cursorVisible)
        o.put("jx", joyX.toDouble()); o.put("jy", joyY.toDouble()); o.put("jr", joyR.toDouble())
        o.put("lx", lookX.toDouble()); o.put("ly", lookY.toDouble())
        o.put("lw", lookW.toDouble()); o.put("lh", lookH.toDouble())
        o.put("sw", screenW.toDouble()); o.put("sh", screenH.toDouble())
        val arr = JSONArray()
        for (b in buttons) {
            arr.put(JSONObject().put("k", b.key).put("x", b.x.toDouble()).put("y", b.y.toDouble()).put("l", b.label))
        }
        o.put("btn", arr)
        return o.toString()
    }

    companion object {
        fun defaultButtons(): List<Btn> = listOf(
            Btn(272, 0.88f, 0.66f, "TEMBAK"),
            Btn(273, 0.78f, 0.52f, "SCOPE"),
            Btn(57, 0.94f, 0.48f, "LONCAT"),
            Btn(46, 0.92f, 0.80f, "JONGKOK"),
            Btn(19, 0.72f, 0.86f, "RELOAD"),
            Btn(2, 0.50f, 0.88f, "1"),
            Btn(3, 0.56f, 0.88f, "2")
        )

        fun fromJson(s: String?): Config {
            val c = Config()
            if (s.isNullOrEmpty()) { c.buttons.addAll(defaultButtons()); return c }
            try {
                val o = JSONObject(s)
                c.sensX = o.optDouble("sx", 1.0).toFloat(); c.sensY = o.optDouble("sy", 1.0).toFloat()
                c.toggleKey = o.optInt("tk", 41); c.cursorVisible = o.optBoolean("cv", true)
                c.joyX = o.optDouble("jx", 0.15).toFloat(); c.joyY = o.optDouble("jy", 0.68).toFloat()
                c.joyR = o.optDouble("jr", 0.12).toFloat()
                c.lookX = o.optDouble("lx", 0.55).toFloat(); c.lookY = o.optDouble("ly", 0.10).toFloat()
                c.lookW = o.optDouble("lw", 0.42).toFloat(); c.lookH = o.optDouble("lh", 0.60).toFloat()
                c.screenW = o.optDouble("sw", 1920.0).toFloat(); c.screenH = o.optDouble("sh", 1080.0).toFloat()
                val arr = o.optJSONArray("btn")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val b = arr.getJSONObject(i)
                        c.buttons.add(Btn(b.getInt("k"), b.getDouble("x").toFloat(), b.getDouble("y").toFloat(), b.optString("l", "")))
                    }
                } else c.buttons.addAll(defaultButtons())
            } catch (e: Exception) {
                c.buttons.clear(); c.buttons.addAll(defaultButtons())
            }
            return c
        }
    }
}
