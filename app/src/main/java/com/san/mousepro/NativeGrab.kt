package com.san.mousepro

/** JNI kecil untuk EVIOCGRAB. Dimuat di proses Shizuku (shell). */
object NativeGrab {
    fun load(path: String) { System.load(path) }
    @JvmStatic external fun openGrab(path: String, grab: Boolean): Int
    @JvmStatic external fun setGrab(fd: Int, on: Boolean)
}
