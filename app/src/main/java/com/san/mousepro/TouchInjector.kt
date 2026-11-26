package com.san.mousepro

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.Method

/** Menyuntik sentuhan multi-jari lewat InputManager (jalan di proses shell via Shizuku). */
class TouchInjector {
    private var target: Any? = null
    private var method: Method? = null
    private val ids = ArrayList<Int>()
    private val xs = HashMap<Int, Float>()
    private val ys = HashMap<Int, Float>()
    private var downTime = 0L

    init {
        try {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java).invoke(null, "input") as IBinder
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            target = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            method = Class.forName("android.hardware.input.IInputManager")
                .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        } catch (t: Throwable) {
            try {
                val imc = Class.forName("android.hardware.input.InputManager")
                target = imc.getMethod("getInstance").invoke(null)
                method = imc.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            } catch (t2: Throwable) {
                target = null; method = null
            }
        }
    }

    val ok: Boolean get() = target != null && method != null

    @Synchronized
    fun down(id: Int, x: Float, y: Float) {
        if (ids.contains(id)) { move(id, x, y); return }
        val now = SystemClock.uptimeMillis()
        if (ids.isEmpty()) downTime = now
        ids.add(id); xs[id] = x; ys[id] = y
        val action = if (ids.size == 1) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or ((ids.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, now)
    }

    @Synchronized
    fun move(id: Int, x: Float, y: Float) {
        if (!ids.contains(id)) return
        xs[id] = x; ys[id] = y
        send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    @Synchronized
    fun up(id: Int) {
        val idx = ids.indexOf(id)
        if (idx < 0) return
        val action = if (ids.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        send(action, SystemClock.uptimeMillis())
        ids.removeAt(idx); xs.remove(id); ys.remove(id)
    }

    @Synchronized
    fun releaseAll() {
        for (id in ArrayList(ids)) up(id)
    }

    private fun send(action: Int, now: Long) {
        val m = method ?: return
        val t = target ?: return
        val n = ids.size
        val props = Array(n) {
            MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coords = Array(n) {
            MotionEvent.PointerCoords().apply {
                x = xs[ids[it]] ?: 0f; y = ys[ids[it]] ?: 0f; pressure = 1f; size = 1f
            }
        }
        val ev = MotionEvent.obtain(
            downTime, now, action, n, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try { m.invoke(t, ev, 0) } catch (_: Throwable) { }
        ev.recycle()
    }
}
