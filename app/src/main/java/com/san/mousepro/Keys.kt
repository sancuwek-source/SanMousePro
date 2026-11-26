package com.san.mousepro

/** Kode tombol Linux evdev -> nama tampilan. */
object Keys {
    val names = LinkedHashMap<Int, String>()

    init {
        names[41] = "~ (GRAVE)"
        "1234567890".forEachIndexed { i, c -> names[2 + i] = c.toString() }
        "QWERTYUIOP".forEachIndexed { i, c -> names[16 + i] = c.toString() }
        "ASDFGHJKL".forEachIndexed { i, c -> names[30 + i] = c.toString() }
        "ZXCVBNM".forEachIndexed { i, c -> names[44 + i] = c.toString() }
        names[57] = "SPACE"
        names[42] = "L-SHIFT"
        names[29] = "L-CTRL"
        names[56] = "L-ALT"
        names[15] = "TAB"
        names[58] = "CAPS"
        names[1] = "ESC"
        names[28] = "ENTER"
        for (i in 0..9) names[59 + i] = "F${i + 1}"
        names[87] = "F11"
        names[88] = "F12"
        names[103] = "UP"
        names[108] = "DOWN"
        names[105] = "LEFT"
        names[106] = "RIGHT"
        names[272] = "MOUSE KIRI"
        names[273] = "MOUSE KANAN"
        names[274] = "MOUSE TENGAH"
        names[275] = "MOUSE SAMPING 1"
        names[276] = "MOUSE SAMPING 2"
    }

    val codes: List<Int> = names.keys.toList()
    fun name(code: Int): String = names[code] ?: "KEY $code"
}
