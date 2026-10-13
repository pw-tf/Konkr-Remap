package com.pwtf.konkrremap

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Keycodes pressed together, modifiers first: [KEYCODE_CTRL_LEFT, KEYCODE_TAB]. Empty = button disabled. */
typealias Combo = List<Int>

/** What one physical button does. [hold] / [double] are optional; without them the button is a plain 1:1 remap. */
data class Mapping(val tap: Combo, val hold: Combo? = null, val double: Combo? = null)

/** Profile "" applies to all apps; any other key is a package name whose mappings win over "". */
typealias Profiles = Map<String, Map<Int, Mapping>>

const val ALL_APPS = ""

/** Stick directions are mapped like buttons using made-up keycodes far above Android's: up, down, left, right. */
const val LEFT_STICK = 9001
const val RIGHT_STICK = 9005
const val HAT = 9009 // D-pads that report as a "hat" axis; routed to the D-pad keys, not shown in the UI
val STICK_CODES = LEFT_STICK until HAT

/**
 * Turns stick positions into direction presses/releases, 8-way: once pushed past [PRESS], the 45° slice the stick
 * points into decides the keys, so diagonals reliably hold two (e.g. W+A). Releasing needs the stick back under
 * [RELEASE], and leaving a slice needs [STICKY]° of overshoot, so a thumb resting on a border doesn't flicker.
 */
class StickButtons(private val onChange: (code: Int, down: Boolean) -> Unit) {
    private val slice = HashMap<Int, Int>() // stick's "up" code -> slice 0..7 (0 = right, counter-clockwise)

    /** [x], [y] in -1..1 (Android: y is positive downwards). [base] is the stick's "up" code. */
    fun update(base: Int, x: Float, y: Float) {
        val cur = slice[base]
        val next = if (hypot(x, y) < (if (cur == null) PRESS else RELEASE)) null else {
            val angle = Math.toDegrees(atan2(-y, x).toDouble())
            val off = cur?.let { abs((angle - it * 45 + 540) % 360 - 180) }
            if (off != null && off < 22.5 + STICKY) cur else Math.floorMod(Math.round(angle / 45).toInt(), 8)
        }
        if (next == cur) return
        if (next == null) slice -= base else slice[base] = next
        val was = dirs(cur)
        val now = dirs(next)
        (was - now).forEach { onChange(base + it, false) }
        (now - was).forEach { onChange(base + it, true) }
    }

    /** Release everything, e.g. when switching apps mid-press. */
    fun reset() {
        val held = slice.toMap()
        slice.clear()
        held.forEach { (base, s) -> dirs(s).forEach { onChange(base + it, false) } }
    }

    // Offsets from the "up" code: 0 up, 1 down, 2 left, 3 right.
    private fun dirs(s: Int?): Set<Int> = when (s) {
        null -> emptySet()
        0 -> setOf(3); 1 -> setOf(0, 3); 2 -> setOf(0); 3 -> setOf(0, 2)
        4 -> setOf(2); 5 -> setOf(1, 2); 6 -> setOf(1); else -> setOf(1, 3)
    }

    companion object {
        const val PRESS = 0.5f
        const val RELEASE = 0.35f
        const val STICKY = 7.5
    }
}

/**
 * Turns physical presses into output combos. Pure (time and output injected) so it can be unit tested.
 * Runs on one thread: key events and [post] callbacks must arrive on the same looper.
 */
class Gestures(
    private val post: (delayMs: Long, block: () -> Unit) -> () -> Unit,
    private val emit: (combo: Combo, down: Boolean, repeat: Int) -> Unit,
) {
    private class Press(val m: Mapping) {
        var cancel: () -> Unit = {}
        var held: Combo? = null // combo currently held down on the output side
        var awaitingSecond = false
        var doubled = false
    }

    private val presses = HashMap<Int, Press>()

    /** Returns true when the event is consumed (never reaches apps as the original button). */
    fun onKey(key: Int, down: Boolean, repeat: Int, mapping: Mapping?): Boolean {
        val p = presses[key]
        if (p == null) {
            if (mapping == null || !down) return false // unmapped, or a release whose press we never saw
            if (repeat > 0) return true // repeat of a press that started before we were listening
        }
        val m = p?.m ?: mapping!!

        // Plain remap: mirror down/repeat/up so holding works in games and key repeat works in Chrome.
        if (m.hold == null && m.double == null) {
            if (down) {
                if (repeat == 0) presses[key] = Press(m)
                emit(m.tap, true, repeat)
            } else {
                presses.remove(key)
                emit(m.tap, false, 0)
            }
            return true
        }

        if (down) {
            when {
                repeat > 0 -> p?.held?.let { emit(it, true, repeat) }
                p != null && p.awaitingSecond -> {
                    p.cancel()
                    p.awaitingSecond = false
                    p.doubled = true
                    click(m.double!!)
                }
                else -> {
                    p?.cancel()
                    val press = Press(m)
                    presses[key] = press
                    press.cancel = post(HOLD_MS) {
                        // Held past the threshold: press the hold action, or the tap action if there's no hold.
                        val h = m.hold ?: m.tap
                        press.held = h
                        emit(h, true, 0)
                    }
                }
            }
            return true
        }

        // Released.
        p!!.cancel()
        val held = p.held
        when {
            held != null -> { presses.remove(key); emit(held, false, 0) }
            p.doubled -> presses.remove(key)
            m.double != null -> {
                p.awaitingSecond = true
                p.cancel = post(DOUBLE_MS) { presses.remove(key); click(m.tap) }
            }
            else -> { presses.remove(key); click(m.tap) }
        }
        return true
    }

    private fun click(c: Combo) {
        emit(c, true, 0)
        emit(c, false, 0)
    }

    companion object {
        const val HOLD_MS = 400L
        const val DOUBLE_MS = 250L
    }
}

/** Mappings, persisted as JSON in SharedPreferences and observable from Compose. */
object Store {
    private lateinit var prefs: SharedPreferences
    var profiles: Profiles by mutableStateOf(emptyMap())
        private set

    /** Keycodes detected for controls on the drawing (by [Pad] name), e.g. the back buttons. */
    var learned: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("mappings", Context.MODE_PRIVATE)
        profiles = runCatching { decode(JSONObject(prefs.getString("profiles", "{}")!!)) }.getOrDefault(emptyMap())
        learned = runCatching {
            JSONObject(prefs.getString("learned", "{}")!!).let { j -> j.keys().asSequence().associateWith { j.getInt(it) } }
        }.getOrDefault(emptyMap())
    }

    fun learn(pad: String, code: Int) {
        learned = learned + (pad to code)
        prefs.edit().putString("learned", JSONObject(learned).toString()).apply()
    }

    fun lookup(pkg: String?, key: Int): Mapping? = pkg?.let { profiles[it]?.get(key) } ?: profiles[ALL_APPS]?.get(key)

    fun put(profile: String, key: Int, m: Mapping?) {
        val p = profiles[profile].orEmpty().toMutableMap()
        if (m == null) p.remove(key) else p[key] = m
        save(profiles + (profile to p))
    }

    fun addProfile(pkg: String) { if (pkg !in profiles) save(profiles + (pkg to emptyMap())) }
    fun removeProfile(pkg: String) = save(profiles - pkg)

    private fun save(p: Profiles) {
        profiles = p
        prefs.edit().putString("profiles", encode(p).toString()).apply()
    }

    private fun encode(p: Profiles) = JSONObject().apply {
        for ((profile, keys) in p) put(profile, JSONObject().apply {
            for ((key, m) in keys) put(key.toString(), JSONObject().apply {
                put("tap", JSONArray(m.tap))
                m.hold?.let { put("hold", JSONArray(it)) }
                m.double?.let { put("double", JSONArray(it)) }
            })
        })
    }

    private fun decode(j: JSONObject): Profiles = j.keys().asSequence().associateWith { profile ->
        val keys = j.getJSONObject(profile)
        keys.keys().asSequence().associate { key ->
            val m = keys.getJSONObject(key)
            fun combo(name: String) = m.optJSONArray(name)?.let { a -> List(a.length()) { a.getInt(it) } }
            key.toInt() to Mapping(combo("tap").orEmpty(), combo("hold"), combo("double"))
        }
    }
}
