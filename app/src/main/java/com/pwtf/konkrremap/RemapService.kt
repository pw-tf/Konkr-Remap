package com.pwtf.konkrremap

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/** Sees every physical button press, remaps it via [Gestures] and sends the result to the app in front. */
class RemapService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gestures = Gestures(
        post = { delay, block -> val r = Runnable(block); handler.postDelayed(r, delay); { handler.removeCallbacks(r) } },
        emit = ::emit,
    )
    private val sticks = StickButtons { code, down ->
        // A hat D-pad behaves like the D-pad keys: their mapping if there is one, else plain D-pad presses.
        val key = if (code >= HAT) KeyEvent.KEYCODE_DPAD_UP + code - HAT else code
        val m = Store.lookup(foreground, key) ?: if (code >= HAT) Mapping(listOf(key)) else null
        gestures.onKey(key, down, 0, m)
    }
    private var foreground: String? = null

    override fun onServiceConnected() {
        Store.init(this)
        updateStickCapture()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        // Only real apps switch the profile; ignore the notification shade, keyboards and other overlays.
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageManager.getLaunchIntentForPackage(pkg) != null) {
            if (pkg != foreground) sticks.reset()
            foreground = pkg
            updateStickCapture()
        }
    }

    /**
     * Android 14+ can hand us stick movement, but then the app in front no longer gets it. So only take the sticks
     * in apps where a stick direction is mapped; everywhere else games keep their analog input.
     */
    private fun updateStickCapture() {
        val info = serviceInfo
        if (Build.VERSION.SDK_INT < 34 || info == null) return
        val keys = Store.profiles[ALL_APPS].orEmpty().keys + foreground?.let { Store.profiles[it]?.keys }.orEmpty()
        val sources = if (foreground != packageName && keys.any { it in STICK_CODES }) InputDevice.SOURCE_JOYSTICK else 0
        if (info.motionEventSources != sources) serviceInfo = info.apply { motionEventSources = sources }
    }

    override fun onMotionEvent(e: MotionEvent) {
        if (!e.isFromSource(InputDevice.SOURCE_JOYSTICK)) return
        val a = stickAxes(e)
        sticks.update(LEFT_STICK, a[0], a[1])
        sticks.update(RIGHT_STICK, a[2], a[3])
        sticks.update(HAT, a[4], a[5])
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        // Only remap physical buttons.
        if (e.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
        // Leave our own app alone so "press a button" capture sees the real button.
        if (foreground == packageName) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        return gestures.onKey(e.keyCode, down, e.repeatCount, Store.lookup(foreground, e.keyCode))
    }

    private fun emit(combo: Combo, down: Boolean, repeat: Int) {
        val key = combo.lastOrNull() ?: return
        val audio = getSystemService(AudioManager::class.java)
        // System keys can't be typed by a keyboard; use the APIs Android gives us for them instead.
        GLOBAL[key]?.let { if (down && repeat == 0) performGlobalAction(it); return }
        VOLUME[key]?.let { if (down) audio.adjustSuggestedStreamVolume(it, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI); return }
        if (key in MEDIA) {
            if (repeat == 0) audio.dispatchMediaKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key))
            return
        }
        // Everything else is typed by our keyboard. If another keyboard is selected (e.g. the user switched to type),
        // switch back; the keys are sent as soon as ours connects.
        if (!RemapKeyboard.send(combo, down, repeat) && down && repeat == 0) {
            softKeyboardController.switchToInputMethod(RemapKeyboard.id(this))
        }
    }

    override fun onInterrupt() {}

    private companion object {
        val GLOBAL = mapOf(
            KeyEvent.KEYCODE_BACK to GLOBAL_ACTION_BACK,
            KeyEvent.KEYCODE_HOME to GLOBAL_ACTION_HOME,
            KeyEvent.KEYCODE_APP_SWITCH to GLOBAL_ACTION_RECENTS,
            KeyEvent.KEYCODE_NOTIFICATION to GLOBAL_ACTION_NOTIFICATIONS,
            KeyEvent.KEYCODE_SYSRQ to GLOBAL_ACTION_TAKE_SCREENSHOT,
            KeyEvent.KEYCODE_POWER to GLOBAL_ACTION_POWER_DIALOG,
            KeyEvent.KEYCODE_SLEEP to GLOBAL_ACTION_LOCK_SCREEN,
        )
        val VOLUME = mapOf(
            KeyEvent.KEYCODE_VOLUME_UP to AudioManager.ADJUST_RAISE,
            KeyEvent.KEYCODE_VOLUME_DOWN to AudioManager.ADJUST_LOWER,
            KeyEvent.KEYCODE_VOLUME_MUTE to AudioManager.ADJUST_TOGGLE_MUTE,
        )
        val MEDIA = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_HEADSETHOOK,
        )
    }
}

/** Left x/y, right x/y and hat (D-pad) x/y. Controllers put the right stick on Z/RZ or on RX/RY. */
fun stickAxes(e: MotionEvent): FloatArray {
    val zrz = e.device?.getMotionRange(MotionEvent.AXIS_Z) != null
    return floatArrayOf(
        e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y),
        e.getAxisValue(if (zrz) MotionEvent.AXIS_Z else MotionEvent.AXIS_RX), e.getAxisValue(if (zrz) MotionEvent.AXIS_RZ else MotionEvent.AXIS_RY),
        e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )
}
