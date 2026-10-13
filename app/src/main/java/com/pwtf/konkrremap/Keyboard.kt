package com.pwtf.konkrremap

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Konkr Remap's keyboard. Android lets the selected keyboard send keys to whatever app is in front, even when no
 * text box is focused, which is how remapped keys reach other apps without root or ADB.
 * It has no keys of its own: when a text box is focused it shows a bar to switch to a real keyboard.
 */
class RemapKeyboard : InputMethodService() {
    override fun onCreate() {
        super.onCreate()
        active = this
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        flush() // keys pressed while we were being switched back in
    }

    // Handhelds are landscape; never take over the screen with a full-screen text editor.
    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt() }
        return LinearLayout(this).apply {
            setBackgroundColor(Color.parseColor("#14161C"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(8), dp(6))
            addView(TextView(context).apply {
                text = "Konkr Remap keyboard"
                setTextColor(Color.LTGRAY)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Switch keyboard"
                setOnClickListener {
                    if (!switchToPreviousInputMethod()) getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                }
            })
        }
    }

    private fun flush(): Boolean {
        val ic = currentInputConnection ?: return false
        while (pending.isNotEmpty()) {
            val e = pending.removeFirst()
            if (SystemClock.uptimeMillis() - e.eventTime < 2000) ic.sendKeyEvent(e) // drop stale presses
        }
        return true
    }

    companion object {
        private var active: RemapKeyboard? = null
        private val pending = ArrayDeque<KeyEvent>()

        fun id(ctx: Context): String = ComponentName(ctx, RemapKeyboard::class.java).flattenToShortString()

        fun isEnabled(ctx: Context) =
            ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.id == id(ctx) }

        fun isSelected(ctx: Context) =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id(ctx)

        /**
         * Presses ([down]) or releases a combo in the app in front: modifiers go down first and come up last.
         * Returns false if this keyboard isn't connected; the keys are then kept until it is (see onStartInput).
         */
        fun send(combo: Combo, down: Boolean, repeat: Int): Boolean {
            val key = combo.last()
            val mods = combo.dropLast(1)
            val now = SystemClock.uptimeMillis()
            fun ev(action: Int, code: Int, rep: Int, meta: Int, source: Int) =
                pending.addLast(KeyEvent(now, now, action, code, rep, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, SCAN[code] ?: 0, 0, source))

            val kb = InputDevice.SOURCE_KEYBOARD
            val meta = mods.fold(0) { m, k -> m or metaOf(k) }
            if (down && repeat == 0) {
                var m = 0
                for (k in mods) { m = m or metaOf(k); ev(KeyEvent.ACTION_DOWN, k, 0, m, kb) }
            }
            val source = if (KeyEvent.isGamepadButton(key)) InputDevice.SOURCE_GAMEPAD else kb
            ev(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key, repeat, meta, source)
            if (!down) {
                var m = meta
                for (k in mods.asReversed()) { m = m and metaOf(k).inv(); ev(KeyEvent.ACTION_UP, k, 0, m, kb) }
            }
            while (pending.size > 32) pending.removeFirst() // never connected: don't replay stale presses later
            return active?.flush() == true
        }

        /**
         * Physical (Linux) key codes for keyboard keys. Chrome derives a web page's `event.code` ("KeyW") from it, and
         * many browser games read that rather than the letter.
         */
        private val SCAN: Map<Int, Int> = buildMap {
            fun row(keys: String, first: Int) = keys.forEachIndexed { i, c -> put(KeyEvent.KEYCODE_A + (c - 'A'), first + i) }
            row("QWERTYUIOP", 16); row("ASDFGHJKL", 30); row("ZXCVBNM", 44)
            for (d in 1..9) put(KeyEvent.KEYCODE_0 + d, 1 + d)
            put(KeyEvent.KEYCODE_0, 11)
            for (f in 0..9) put(KeyEvent.KEYCODE_F1 + f, 59 + f)
            putAll(mapOf(
                KeyEvent.KEYCODE_F11 to 87, KeyEvent.KEYCODE_F12 to 88,
                KeyEvent.KEYCODE_ESCAPE to 1, KeyEvent.KEYCODE_MINUS to 12, KeyEvent.KEYCODE_EQUALS to 13,
                KeyEvent.KEYCODE_DEL to 14, KeyEvent.KEYCODE_TAB to 15, KeyEvent.KEYCODE_LEFT_BRACKET to 26,
                KeyEvent.KEYCODE_RIGHT_BRACKET to 27, KeyEvent.KEYCODE_ENTER to 28, KeyEvent.KEYCODE_CTRL_LEFT to 29,
                KeyEvent.KEYCODE_SEMICOLON to 39, KeyEvent.KEYCODE_APOSTROPHE to 40, KeyEvent.KEYCODE_GRAVE to 41,
                KeyEvent.KEYCODE_SHIFT_LEFT to 42, KeyEvent.KEYCODE_BACKSLASH to 43, KeyEvent.KEYCODE_COMMA to 51,
                KeyEvent.KEYCODE_PERIOD to 52, KeyEvent.KEYCODE_SLASH to 53, KeyEvent.KEYCODE_SHIFT_RIGHT to 54,
                KeyEvent.KEYCODE_ALT_LEFT to 56, KeyEvent.KEYCODE_SPACE to 57, KeyEvent.KEYCODE_CAPS_LOCK to 58,
                KeyEvent.KEYCODE_CTRL_RIGHT to 97, KeyEvent.KEYCODE_ALT_RIGHT to 100, KeyEvent.KEYCODE_MOVE_HOME to 102,
                KeyEvent.KEYCODE_DPAD_UP to 103, KeyEvent.KEYCODE_PAGE_UP to 104, KeyEvent.KEYCODE_DPAD_LEFT to 105,
                KeyEvent.KEYCODE_DPAD_RIGHT to 106, KeyEvent.KEYCODE_MOVE_END to 107, KeyEvent.KEYCODE_DPAD_DOWN to 108,
                KeyEvent.KEYCODE_PAGE_DOWN to 109, KeyEvent.KEYCODE_INSERT to 110, KeyEvent.KEYCODE_FORWARD_DEL to 111,
                KeyEvent.KEYCODE_META_LEFT to 125, KeyEvent.KEYCODE_META_RIGHT to 126,
            ))
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
}
