package com.pwtf.konkrremap

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import java.io.IOException
import java.io.Writer
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val PKG = "com.pwtf.konkrremap"
private const val SOCKET = "konkrremap"

/**
 * Runs as the shell user (started over Wireless debugging by [Adb.startHelper]), which may inject input into any app.
 * Only accepts connections from the app's own uid.
 */
object Helper {
    /** The helper's stdout/stderr, readable over ADB when it fails to start. */
    const val LOG = "/data/local/tmp/konkrremap.log"

    /** Shell command that grants the app WRITE_SECURE_SETTINGS and launches the helper detached from the ADB session. */
    fun startCommand(appUid: Int) =
        "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS; " +
            "CLASSPATH=\$(pm path $PKG | head -n1 | cut -d: -f2) setsid app_process /system/bin " +
            "--nice-name=konkrremap_helper $PKG.Helper $appUid </dev/null >$LOG 2>&1 &"

    @JvmStatic
    fun main(args: Array<String>) {
        @Suppress("DEPRECATION") Looper.prepareMainLooper()
        val appUid = args[0].toInt()
        // Android 14 moved injectInputEvent to InputManagerGlobal.
        val im = runCatching { Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null) }
            .getOrElse { Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null) }!!
        val inject = im.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)

        val server = try { LocalServerSocket(SOCKET) } catch (e: IOException) { exitProcess(0) } // already running
        while (true) {
            val s = server.accept()
            if (s.peerCredentials.uid != appUid) { s.close(); continue }
            thread {
                s.use {
                    val r = s.inputStream.bufferedReader()
                    if (r.readLine() != BuildConfig.VERSION_CODE.toString()) {
                        // The app was updated: relaunch from the new APK (after we exit and free the socket).
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", "(sleep 1; ${startCommand(appUid)}) </dev/null >/dev/null 2>&1 &"))
                        exitProcess(0)
                    }
                    s.outputStream.write("ok\n".toByteArray())
                    r.forEachLine { line ->
                        runCatching {
                            val (action, code, repeat, meta, source) = line.split(' ').map { it.toInt() }
                            val now = SystemClock.uptimeMillis()
                            val ev = KeyEvent(now, now, action, code, repeat, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source)
                            inject.invoke(im, ev, 0) // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
                        }
                    }
                }
            }
        }
    }
}

/** App-side connection to [Helper]. */
object Injector {
    private var out: Writer? = null
    private var sock: LocalSocket? = null

    @Synchronized
    fun connected(): Boolean = out != null || connect()

    private fun connect(): Boolean = try {
        val s = LocalSocket().apply { connect(LocalSocketAddress(SOCKET)); soTimeout = 1000 }
        val w = s.outputStream.bufferedWriter()
        w.write("${BuildConfig.VERSION_CODE}\n")
        w.flush()
        if (s.inputStream.bufferedReader().readLine() != "ok") throw IOException("helper restarting")
        s.soTimeout = 0
        sock = s
        out = w
        true
    } catch (e: IOException) {
        false
    }

    /** Presses ([down]) or releases a combo: modifiers go down first and come up last. */
    fun emit(combo: Combo, down: Boolean, repeat: Int) {
        if (combo.isEmpty()) return
        val key = combo.last()
        val mods = combo.dropLast(1)
        val meta = mods.fold(0) { m, k -> m or metaOf(k) }
        val source = if (KeyEvent.isGamepadButton(key)) InputDevice.SOURCE_GAMEPAD else InputDevice.SOURCE_KEYBOARD
        val kb = InputDevice.SOURCE_KEYBOARD
        if (down && repeat == 0) {
            var m = 0
            for (k in mods) { m = m or metaOf(k); send(KeyEvent.ACTION_DOWN, k, 0, m, kb) }
        }
        send(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key, repeat, meta, source)
        if (!down) {
            var m = meta
            for (k in mods.asReversed()) { m = m and metaOf(k).inv(); send(KeyEvent.ACTION_UP, k, 0, m, kb) }
        }
    }

    @Synchronized
    private fun send(action: Int, code: Int, repeat: Int, meta: Int, source: Int) {
        if (!connected()) return
        try {
            out!!.write("$action $code $repeat $meta $source\n")
            out!!.flush()
        } catch (e: IOException) {
            runCatching { sock?.close() }
            out = null
        }
    }

    private fun metaOf(key: Int) = when (key) {
        KeyEvent.KEYCODE_CTRL_LEFT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON
        KeyEvent.KEYCODE_SHIFT_LEFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        KeyEvent.KEYCODE_ALT_LEFT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        KeyEvent.KEYCODE_META_LEFT -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON or KeyEvent.META_META_RIGHT_ON
        else -> 0
    }
}
