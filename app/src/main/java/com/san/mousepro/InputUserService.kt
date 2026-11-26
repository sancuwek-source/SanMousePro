package com.san.mousepro

import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.system.exitProcess

/**
 * Jalan sebagai UserService Shizuku (UID shell):
 *  - membaca mouse/keyboard fisik dari /dev/input/event*  (+ grab eksklusif)
 *  - menerjemahkan ke sentuhan layar (joystick WASD, kamera, tombol)
 */
class InputUserService : IInputService.Stub() {

    private val ID_JOY = 0
    private val ID_LOOK = 1
    private val ID_CUR = 2
    private val ID_BTN = 3

    private val lock = Any()
    private var cfg = Config()
    private var cb: IInputCallback? = null
    private var running = false
    private var gen = 0
    private var active = true           // true = mode GAME, false = mode KURSOR
    private var nativeOk = false
    private var inj: TouchInjector? = null

    private var kw = false
    private var ka = false
    private var ks = false
    private var kd = false
    private var joyDown = false
    private var lookDown = false
    private var lookX = 0f
    private var lookY = 0f
    private var lastMove = 0L
    private var accDx = 0f
    private var accDy = 0f
    private var cx = 0f
    private var cy = 0f
    private var curDirty = false
    private var lastCur = 0L

    // ---------- AIDL ----------

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    override fun start(configJson: String?, libDir: String?, callback: IInputCallback?) {
        val g: Int
        synchronized(lock) {
            if (running) shutdownLocked()
            cfg = Config.fromJson(configJson)
            cb = callback
            nativeOk = try { NativeGrab.load("$libDir/libsangrab.so"); true } catch (t: Throwable) { false }
            inj = TouchInjector()
            resetState()
            cx = cfg.screenW / 2f; cy = cfg.screenH / 2f
            active = true
            running = true
            gen++
            g = gen
        }
        val devs = findDevices()
        for (p in devs) Thread { readLoop(p, g) }.apply { isDaemon = true; start() }
        Thread { pump(g) }.apply { isDaemon = true; start() }
        val injOk = inj?.ok == true
        report(
            if (devs.isEmpty()) "Mouse/keyboard tidak ditemukan. Colok dulu lewat OTG/Bluetooth."
            else "Aktif: ${devs.size} perangkat input" + (if (nativeOk) " (grab ON)" else " (grab OFF)") +
                    (if (injOk) "" else " | injector GAGAL")
        )
        try { callback?.onMode(true) } catch (_: Throwable) {}
    }

    override fun stop() {
        synchronized(lock) { shutdownLocked() }
        report("Berhenti")
    }

    override fun setConfig(configJson: String?) {
        synchronized(lock) {
            inj?.releaseAll(); resetState()
            cfg = Config.fromJson(configJson)
        }
    }

    override fun setActive(a: Boolean) {
        synchronized(lock) { setMode(a) }
    }

    // ---------- internal ----------

    private fun report(msg: String) { try { cb?.onStatus(msg) } catch (_: Throwable) {} }

    private fun shutdownLocked() {
        running = false
        gen++
        inj?.releaseAll()
        resetState()
    }

    private fun resetState() {
        kw = false; ka = false; ks = false; kd = false
        joyDown = false; lookDown = false
        accDx = 0f; accDy = 0f
    }

    private fun setMode(a: Boolean) {
        inj?.releaseAll(); resetState()
        active = a
        try { cb?.onMode(a) } catch (_: Throwable) {}
    }

    /** Hanya perangkat USB (0003) atau Bluetooth (0005) yang punya handler kbd/mouse. */
    private fun findDevices(): List<String> {
        val out = ArrayList<String>()
        val txt = try { File("/proc/bus/input/devices").readText() } catch (e: Exception) { return out }
        for (blk in txt.split("\n\n")) {
            val bus = Regex("Bus=([0-9a-fA-F]+)").find(blk)?.groupValues?.get(1)?.lowercase() ?: continue
            if (bus != "0003" && bus != "0005") continue
            val h = Regex("Handlers=(.*)").find(blk)?.groupValues?.get(1) ?: continue
            if (!(h.contains("kbd") || h.contains("mouse"))) continue
            val ev = Regex("event(\\d+)").find(h)?.groupValues?.get(1) ?: continue
            out.add("/dev/input/event$ev")
        }
        return out
    }

    private fun alive(g: Int): Boolean = synchronized(lock) { running && g == gen }

    private fun readLoop(path: String, g: Int) {
        var pfd: ParcelFileDescriptor? = null
        var grabbed = false
        try {
            if (nativeOk) {
                val fd = NativeGrab.openGrab(path, true)
                if (fd >= 0) { pfd = ParcelFileDescriptor.adoptFd(fd); grabbed = true }
            }
            if (pfd == null) pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val p = pfd!!
            val fis = FileInputStream(p.fileDescriptor)
            val evSize = if (Process.is64Bit()) 24 else 16
            val buf = ByteArray(evSize * 64)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val pf = arrayOf(StructPollfd())
            pf[0].fd = p.fileDescriptor
            pf[0].events = OsConstants.POLLIN.toShort()
            while (alive(g)) {
                val r = try { Os.poll(pf, 200) } catch (e: ErrnoException) { 0 }
                if (r <= 0) continue
                val n = fis.read(buf)
                if (n <= 0) break
                var o = 0
                while (o + evSize <= n) {
                    val type = bb.getShort(o + evSize - 8).toInt() and 0xFFFF
                    val code = bb.getShort(o + evSize - 6).toInt() and 0xFFFF
                    val value = bb.getInt(o + evSize - 4)
                    handle(type, code, value, g)
                    o += evSize
                }
            }
        } catch (t: Throwable) {
            report("Gagal baca $path: ${t.message}")
        } finally {
            try { if (grabbed && pfd != null) NativeGrab.setGrab(pfd.fd, false) } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    private fun handle(type: Int, code: Int, value: Int, g: Int) {
        synchronized(lock) {
            if (!running || g != gen) return
            if (type == 1) onKey(code, value)
            else if (type == 2) {
                if (code == 0) accDx += value else if (code == 1) accDy += value
            }
        }
    }

    private fun onKey(code: Int, value: Int) {
        if (value == 2) return
        val down = value == 1
        val j = inj ?: return
        if (code == cfg.toggleKey) { if (down) setMode(!active); return }

        if (!active) {
            // Mode KURSOR: klik kiri = tap/drag di posisi kursor
            if (code == 272) { if (down) j.down(ID_CUR, cx, cy) else j.up(ID_CUR) }
            return
        }
        when (code) {
            17 -> { kw = down; updateJoy(); return }
            30 -> { ka = down; updateJoy(); return }
            31 -> { ks = down; updateJoy(); return }
            32 -> { kd = down; updateJoy(); return }
        }
        val sw = cfg.screenW
        val sh = cfg.screenH
        for ((i, b) in cfg.buttons.withIndex()) {
            if (b.key != code) continue
            val id = ID_BTN + i
            if (id > 31) break
            if (down) j.down(id, b.x * sw, b.y * sh) else j.up(id)
        }
    }

    private fun updateJoy() {
        val j = inj ?: return
        var vx = (if (kd) 1f else 0f) - (if (ka) 1f else 0f)
        var vy = (if (ks) 1f else 0f) - (if (kw) 1f else 0f)
        if (vx == 0f && vy == 0f) {
            if (joyDown) { j.up(ID_JOY); joyDown = false }
            return
        }
        val len = hypot(vx, vy)
        vx /= len; vy /= len
        val jx = cfg.joyX * cfg.screenW
        val jy = cfg.joyY * cfg.screenH
        val r = cfg.joyR * cfg.screenH
        if (!joyDown) { j.down(ID_JOY, jx, jy); joyDown = true }
        j.move(ID_JOY, jx + vx * r, jy + vy * r)
    }

    /** Dipanggil tiap ~8 ms: gerakan mouse diakumulasi lalu dikirim sekali -> halus & stabil. */
    private fun pump(g: Int) {
        while (true) {
            try { Thread.sleep(8) } catch (_: InterruptedException) {}
            synchronized(lock) {
                if (!running || g != gen) return
                tick()
            }
        }
    }

    private fun tick() {
        val j = inj ?: return
        val now = SystemClock.uptimeMillis()
        val dx = accDx
        val dy = accDy
        accDx = 0f; accDy = 0f
        val sw = cfg.screenW
        val sh = cfg.screenH

        if (active) {
            val l = cfg.lookX * sw
            val t = cfg.lookY * sh
            val r = l + cfg.lookW * sw
            val b = t + cfg.lookH * sh
            if (dx != 0f || dy != 0f) {
                if (!lookDown) {
                    lookX = (l + r) / 2f; lookY = (t + b) / 2f
                    j.down(ID_LOOK, lookX, lookY); lookDown = true
                }
                lookX += dx * cfg.sensX
                lookY += dy * cfg.sensY
                if (lookX < l || lookX > r || lookY < t || lookY > b) {
                    // mentok tepi area: angkat jari lalu taruh lagi di tengah
                    j.move(ID_LOOK, lookX.coerceIn(l, r), lookY.coerceIn(t, b))
                    j.up(ID_LOOK)
                    lookX = (l + r) / 2f; lookY = (t + b) / 2f
                    j.down(ID_LOOK, lookX, lookY)
                } else {
                    j.move(ID_LOOK, lookX, lookY)
                }
                lastMove = now
            } else if (lookDown && now - lastMove > 150) {
                j.up(ID_LOOK); lookDown = false
            }
        } else if (dx != 0f || dy != 0f) {
            cx = (cx + dx * cfg.sensX * 1.5f).coerceIn(0f, sw - 1f)
            cy = (cy + dy * cfg.sensY * 1.5f).coerceIn(0f, sh - 1f)
            j.move(ID_CUR, cx, cy)
            curDirty = true
        }

        if (curDirty && now - lastCur >= 16) {
            curDirty = false; lastCur = now
            try { cb?.onCursor(cx, cy) } catch (_: Throwable) {}
        }
    }
}
