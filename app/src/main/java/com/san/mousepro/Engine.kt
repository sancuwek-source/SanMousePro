package com.san.mousepro

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

/** Jembatan antara aplikasi dan proses Shizuku (InputUserService). */
object Engine {
    private val main = Handler(Looper.getMainLooper())

    @Volatile var service: IInputService? = null
    var screenW = 1920
    var screenH = 1080

    var modeListener: ((Boolean) -> Unit)? = null
    var cursorListener: ((Float, Float) -> Unit)? = null
    var statusListener: ((String) -> Unit)? = null

    private var pending: (() -> Unit)? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = IInputService.Stub.asInterface(b)
            pending?.invoke(); pending = null
        }
        override fun onServiceDisconnected(n: ComponentName?) { service = null }
    }

    private val callback = object : IInputCallback.Stub() {
        override fun onMode(gameMode: Boolean) { main.post { modeListener?.invoke(gameMode) } }
        override fun onCursor(x: Float, y: Float) { main.post { cursorListener?.invoke(x, y) } }
        override fun onStatus(msg: String?) { main.post { statusListener?.invoke(msg ?: "") } }
    }

    fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) { false }

    fun connect(ctx: Context, whenReady: () -> Unit) {
        if (service?.asBinder()?.isBinderAlive == true) { whenReady(); return }
        service = null
        pending = whenReady
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, InputUserService::class.java.name))
                .daemon(false)
                .processNameSuffix("input")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            statusListener?.invoke("Gagal konek Shizuku: ${t.message}")
        }
    }

    private fun json(cfg: Config): String {
        cfg.screenW = screenW.toFloat(); cfg.screenH = screenH.toFloat()
        return cfg.toJson()
    }

    fun start(ctx: Context, cfg: Config) {
        try { service?.start(json(cfg), ctx.applicationInfo.nativeLibraryDir, callback) }
        catch (t: Throwable) { statusListener?.invoke("Gagal start: ${t.message}") }
    }

    fun push(cfg: Config) { try { service?.setConfig(json(cfg)) } catch (_: Throwable) {} }
    fun setActive(a: Boolean) { try { service?.setActive(a) } catch (_: Throwable) {} }
    fun stop() { try { service?.stop() } catch (_: Throwable) {} }
}
