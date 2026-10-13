package com.pwtf.konkrremap

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlin.concurrent.thread

/** Sees every physical button press, remaps it via [Gestures] and sends the result through the helper. */
class RemapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gestures = Gestures(
        post = { delay, block -> val r = Runnable(block); handler.postDelayed(r, delay); { handler.removeCallbacks(r) } },
        emit = Injector::emit,
    )
    private var foreground: String? = null
    private var lastStartAttempt = 0L

    override fun onServiceConnected() {
        Store.init(this)
        tryStartHelper()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        // Only real apps switch the profile; ignore the notification shade, keyboards and other overlays.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageManager.getLaunchIntentForPackage(pkg) != null) {
            foreground = pkg
        }
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        // Our own injected keys come from the virtual keyboard; never remap them (or we'd loop).
        // ponytail: also skips other apps' virtual keys, fine since only physical buttons need remapping.
        if (e.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
        // Leave our own app alone so "press a button" capture sees the real button.
        if (foreground == packageName) return false
        if (!Injector.connected()) {
            tryStartHelper()
            return false // pass the original button through rather than swallowing it
        }
        val down = e.action == KeyEvent.ACTION_DOWN
        return gestures.onKey(e.keyCode, down, e.repeatCount, Store.lookup(foreground, e.keyCode))
    }

    /** At most once a minute, restart the helper in the background (e.g. after a reboot). */
    private fun tryStartHelper() {
        val now = System.currentTimeMillis()
        if (now - lastStartAttempt < 60_000) return
        lastStartAttempt = now
        thread { Adb.startHelper(applicationContext) }
    }

    override fun onInterrupt() {}
}
