@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.pwtf.konkrremap

import android.view.KeyEvent.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val KeyBg = Color(0xFF2E323D)
private val BodyBg = Color(0xFF1E2129)

/**
 * The Konkr Pocket Fit's controls (Xbox-offset sticks, Y/A/X/B), placed on a canvas twice as wide as it is tall:
 * x, y and sizes are fractions of the width. [code] is the button's standard Android keycode; null means it has
 * none (back/custom buttons) and has to be detected once. [output] = can be sent as a gamepad button.
 */
enum class Pad(
    val title: String, val long: String, val code: Int?,
    val x: Float, val y: Float, val w: Float, val h: Float = w, val output: Boolean = true,
) {
    LT("LT", "LT", KEYCODE_BUTTON_L2, .07f, .03f, .09f, .04f),
    LB("LB", "LB", KEYCODE_BUTTON_L1, .18f, .03f, .09f, .04f),
    RB("RB", "RB", KEYCODE_BUTTON_R1, .82f, .03f, .09f, .04f),
    RT("RT", "RT", KEYCODE_BUTTON_R2, .93f, .03f, .09f, .04f),
    LS_UP("↑", "Left stick ↑", LEFT_STICK, .10f, .105f, .03f, output = false),
    LS_DOWN("↓", "Left stick ↓", LEFT_STICK + 1, .10f, .235f, .03f, output = false),
    LS_LEFT("←", "Left stick ←", LEFT_STICK + 2, .035f, .17f, .03f, output = false),
    LS_RIGHT("→", "Left stick →", LEFT_STICK + 3, .165f, .17f, .03f, output = false),
    L3("L3", "L3", KEYCODE_BUTTON_THUMBL, .10f, .17f, .045f),
    D_UP("↑", "D-pad ↑", KEYCODE_DPAD_UP, .19f, .27f, .03f),
    D_DOWN("↓", "D-pad ↓", KEYCODE_DPAD_DOWN, .19f, .35f, .03f),
    D_LEFT("←", "D-pad ←", KEYCODE_DPAD_LEFT, .15f, .31f, .03f),
    D_RIGHT("→", "D-pad →", KEYCODE_DPAD_RIGHT, .23f, .31f, .03f),
    Y("Y", "Y", KEYCODE_BUTTON_Y, .90f, .125f, .038f),
    A("A", "A", KEYCODE_BUTTON_A, .90f, .215f, .038f),
    X("X", "X", KEYCODE_BUTTON_X, .855f, .17f, .038f),
    B("B", "B", KEYCODE_BUTTON_B, .945f, .17f, .038f),
    RS_UP("↑", "Right stick ↑", RIGHT_STICK, .81f, .245f, .03f, output = false),
    RS_DOWN("↓", "Right stick ↓", RIGHT_STICK + 1, .81f, .375f, .03f, output = false),
    RS_LEFT("←", "Right stick ←", RIGHT_STICK + 2, .745f, .31f, .03f, output = false),
    RS_RIGHT("→", "Right stick →", RIGHT_STICK + 3, .875f, .31f, .03f, output = false),
    R3("R3", "R3", KEYCODE_BUTTON_THUMBR, .81f, .31f, .045f),
    BACK_L("Back L", "Back L", null, .07f, .455f, .12f, .045f, output = false),
    SELECT("Select", "Select", KEYCODE_BUTTON_SELECT, .25f, .455f, .12f, .045f),
    HOME("Home", "Home button", KEYCODE_BUTTON_MODE, .42f, .455f, .12f, .045f),
    CUSTOM("Custom", "Custom button", null, .58f, .455f, .12f, .045f, output = false),
    START("Start", "Start", KEYCODE_BUTTON_START, .75f, .455f, .12f, .045f),
    BACK_R("Back R", "Back R", null, .93f, .455f, .12f, .045f, output = false);

    /** The keycode this control sends on this device: detected, else the standard one. */
    val current: Int? get() = Store.learned[name] ?: code
}

/** Name of a physical input: the drawing's name for detected buttons, else the key's name. */
fun inputName(code: Int) = Pad.entries.firstOrNull { Store.learned[it.name] == code }?.long ?: keyName(code)

/**
 * Drawing of the Konkr. In input mode ([output] = false) it shows every control and lights the mapped ones;
 * in output mode only buttons that can be sent as a gamepad button.
 */
@Composable
fun ControllerDrawing(
    output: Boolean,
    lit: (Int) -> Boolean,
    onTap: (Pad) -> Unit,
    onLongPress: (Pad) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(2f)) {
        val w = maxWidth
        val density = LocalDensity.current
        fun at(x: Float, y: Float, bw: Float, bh: Float) = Modifier.offset(w * (x - bw / 2), w * (y - bh / 2)).size(w * bw, w * bh)

        Box(Modifier.offset(0.dp, w * .07f).size(w, w * .34f).background(BodyBg, RoundedCornerShape(w * .08f)))
        Box(
            Modifier.offset(w * .28f, w * .10f).size(w * .44f, w * .28f).background(Color.Black, RoundedCornerShape(w * .015f)),
            contentAlignment = Alignment.Center,
        ) { Text("KONKR", color = Color(0xFF3A3F4B), fontWeight = FontWeight.Bold, fontSize = with(density) { (w * .03f).toSp() }) }
        // Stick wells.
        for ((cx, cy) in listOf(.10f to .17f, .81f to .31f)) {
            Box(at(cx, cy, .13f, .13f).border(1.dp, Color(0xFF3A3F4B), CircleShape))
        }
        for (p in Pad.entries) {
            if (output && !p.output) continue
            val code = if (output) p.code else p.current
            val on = code != null && lit(code)
            Box(
                at(p.x, p.y, p.w, p.h).clip(RoundedCornerShape(50)).background(if (on) Accent else KeyBg)
                    .combinedClickable(onClick = { onTap(p) }, onLongClick = { onLongPress(p) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.title, maxLines = 1, fontWeight = FontWeight.SemiBold,
                    color = if (code == null) Color.Gray else Color.White,
                    fontSize = with(density) { (w * p.h * .45f).toSp() },
                )
            }
        }
    }
}

/** A key on the keyboard drawing, in key units (a letter key is 1 x 1). */
private data class Cap(val code: Int, val label: String, val w: Float = 1f, val h: Float = 1f, val x: Float = 0f, val y: Float = 0f)

private fun gap(w: Float) = Cap(0, "", w)
private fun letters(s: String) = s.map { Cap(KEYCODE_A + (it - 'A'), it.toString()) }

/** Full 104-key layout: function row, main block, navigation cluster, arrows and numpad. 23 x 6.5 units. */
private val KEYBOARD: List<Cap> = buildList {
    fun row(y: Float, x0: Float, keys: List<Cap>) {
        var x = x0
        for (k in keys) { if (k.code != 0) add(k.copy(x = x, y = y)); x += k.w }
    }
    fun fn(from: Int) = (from..from + 3).map { Cap(KEYCODE_F1 + it - 1, "F$it") }
    row(0f, 0f, listOf(Cap(KEYCODE_ESCAPE, "Esc"), gap(1f)) + fn(1) + gap(.5f) + fn(5) + gap(.5f) + fn(9))
    row(0f, 15.5f, listOf(Cap(KEYCODE_SYSRQ, "PrtSc"), Cap(KEYCODE_SCROLL_LOCK, "ScrLk"), Cap(KEYCODE_BREAK, "Pause")))

    row(1.5f, 0f, listOf(Cap(KEYCODE_GRAVE, "`")) + (1..9).map { Cap(KEYCODE_0 + it, "$it") } + listOf(
        Cap(KEYCODE_0, "0"), Cap(KEYCODE_MINUS, "-"), Cap(KEYCODE_EQUALS, "="), Cap(KEYCODE_DEL, "Backspace", 2f),
    ))
    row(2.5f, 0f, listOf(Cap(KEYCODE_TAB, "Tab", 1.5f)) + letters("QWERTYUIOP") + listOf(
        Cap(KEYCODE_LEFT_BRACKET, "["), Cap(KEYCODE_RIGHT_BRACKET, "]"), Cap(KEYCODE_BACKSLASH, "\\", 1.5f),
    ))
    row(3.5f, 0f, listOf(Cap(KEYCODE_CAPS_LOCK, "Caps", 1.75f)) + letters("ASDFGHJKL") + listOf(
        Cap(KEYCODE_SEMICOLON, ";"), Cap(KEYCODE_APOSTROPHE, "'"), Cap(KEYCODE_ENTER, "Enter", 2.25f),
    ))
    row(4.5f, 0f, listOf(Cap(KEYCODE_SHIFT_LEFT, "Shift", 2.25f)) + letters("ZXCVBNM") + listOf(
        Cap(KEYCODE_COMMA, ","), Cap(KEYCODE_PERIOD, "."), Cap(KEYCODE_SLASH, "/"), Cap(KEYCODE_SHIFT_RIGHT, "Shift", 2.75f),
    ))
    row(5.5f, 0f, listOf(
        Cap(KEYCODE_CTRL_LEFT, "Ctrl", 1.25f), Cap(KEYCODE_META_LEFT, "Win", 1.25f), Cap(KEYCODE_ALT_LEFT, "Alt", 1.25f),
        Cap(KEYCODE_SPACE, "Space", 6.25f), Cap(KEYCODE_ALT_RIGHT, "Alt", 1.25f), Cap(KEYCODE_META_RIGHT, "Win", 1.25f),
        Cap(KEYCODE_MENU, "Menu", 1.25f), Cap(KEYCODE_CTRL_RIGHT, "Ctrl", 1.25f),
    ))

    row(1.5f, 15.5f, listOf(Cap(KEYCODE_INSERT, "Ins"), Cap(KEYCODE_MOVE_HOME, "Home"), Cap(KEYCODE_PAGE_UP, "PgUp")))
    row(2.5f, 15.5f, listOf(Cap(KEYCODE_FORWARD_DEL, "Del"), Cap(KEYCODE_MOVE_END, "End"), Cap(KEYCODE_PAGE_DOWN, "PgDn")))
    row(4.5f, 16.5f, listOf(Cap(KEYCODE_DPAD_UP, "↑")))
    row(5.5f, 15.5f, listOf(Cap(KEYCODE_DPAD_LEFT, "←"), Cap(KEYCODE_DPAD_DOWN, "↓"), Cap(KEYCODE_DPAD_RIGHT, "→")))

    fun num(vararg n: Int) = n.map { Cap(KEYCODE_NUMPAD_0 + it, "$it") }
    row(1.5f, 19f, listOf(Cap(KEYCODE_NUM_LOCK, "Num"), Cap(KEYCODE_NUMPAD_DIVIDE, "/"), Cap(KEYCODE_NUMPAD_MULTIPLY, "*"), Cap(KEYCODE_NUMPAD_SUBTRACT, "-")))
    row(2.5f, 19f, num(7, 8, 9) + Cap(KEYCODE_NUMPAD_ADD, "+", h = 2f))
    row(3.5f, 19f, num(4, 5, 6))
    row(4.5f, 19f, num(1, 2, 3) + Cap(KEYCODE_NUMPAD_ENTER, "Enter", h = 2f))
    row(5.5f, 19f, listOf(Cap(KEYCODE_NUMPAD_0, "0", 2f), Cap(KEYCODE_NUMPAD_DOT, ".")))
}

@Composable
private fun KeyboardDrawing(lit: (Int) -> Boolean, onTap: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fit the width, but never shrink keys below a tappable size (scroll sideways instead).
        val u: Dp = maxOf(maxWidth / 23, 30.dp)
        val text = with(LocalDensity.current) { (u * .3f).toSp() }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.size(u * 23, u * 6.5f)) {
                for (k in KEYBOARD) {
                    Box(
                        Modifier.offset(u * k.x, u * k.y).size(u * k.w, u * k.h).padding(u * .05f)
                            .clip(RoundedCornerShape(u * .15f)).background(if (lit(k.code)) Accent else KeyBg)
                            .clickable { onTap(k.code) },
                        contentAlignment = Alignment.Center,
                    ) { Text(k.label, fontSize = text, maxLines = 1) }
                }
            }
        }
    }
}

private val EXTRA_PAD = listOf(KEYCODE_BUTTON_C, KEYCODE_BUTTON_Z) + (KEYCODE_BUTTON_1..KEYCODE_BUTTON_16)

private val SYSTEM = listOf(
    "Home" to KEYCODE_HOME, "Back" to KEYCODE_BACK, "Recent apps" to KEYCODE_APP_SWITCH,
    "Notifications" to KEYCODE_NOTIFICATION, "Screenshot" to KEYCODE_SYSRQ, "Lock screen" to KEYCODE_SLEEP,
    "Power menu" to KEYCODE_POWER,
)
private val MEDIA = listOf(
    "Play / pause" to KEYCODE_MEDIA_PLAY_PAUSE, "Next track" to KEYCODE_MEDIA_NEXT, "Previous track" to KEYCODE_MEDIA_PREVIOUS,
    "Stop" to KEYCODE_MEDIA_STOP, "Volume up" to KEYCODE_VOLUME_UP, "Volume down" to KEYCODE_VOLUME_DOWN,
    "Mute" to KEYCODE_VOLUME_MUTE,
)

private fun ctrl(vararg k: Int) = listOf(KEYCODE_CTRL_LEFT) + k.toList()
private val CHROME = listOf(
    "Next tab" to ctrl(KEYCODE_TAB),
    "Previous tab" to ctrl(KEYCODE_SHIFT_LEFT, KEYCODE_TAB),
    "New tab" to ctrl(KEYCODE_T),
    "Close tab" to ctrl(KEYCODE_W),
    "Reload" to ctrl(KEYCODE_R),
    "Back" to listOf(KEYCODE_ALT_LEFT, KEYCODE_DPAD_LEFT),
    "Forward" to listOf(KEYCODE_ALT_LEFT, KEYCODE_DPAD_RIGHT),
    "Address bar" to ctrl(KEYCODE_L),
    "Find in page" to ctrl(KEYCODE_F),
    "Zoom in" to ctrl(KEYCODE_EQUALS),
    "Zoom out" to ctrl(KEYCODE_MINUS),
)
private val ALL_KEYS = (1..getMaxKeyCode()).filter { !keyCodeToString(it)[0].isDigit() }

/** Modifiers first (so they're held while the key is pressed), otherwise in the order they were tapped. */
private fun ordered(c: Combo) = c.sortedBy { if (it in MOD_NAMES) 0 else 1 }

/**
 * Picks what a button does. Keyboard and Gamepad tabs build a combo by tapping keys (tap again to remove),
 * then Done; System actions are picked with one tap.
 */
@Composable
fun KeyPicker(onDismiss: () -> Unit, onPick: (Combo) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chord by remember { mutableStateOf(emptyList<Int>()) }
    val toggle = { c: Int -> chord = if (c in chord) chord - c else chord + c }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (chord.isEmpty()) "Choose what it does" else comboName(ordered(chord)),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    if (chord.isNotEmpty()) TextButton(onClick = { chord = emptyList() }) { Text("Clear") }
                    Button(onClick = { onPick(ordered(chord)) }, enabled = chord.isNotEmpty()) { Text("Done") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    listOf("Keyboard", "Gamepad", "System").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (tab) {
                        0 -> {
                            Hint("Tap keys to build a combo (e.g. Ctrl, then Tab), then Done. Tap a key again to remove it.")
                            KeyboardDrawing(lit = { it in chord }, onTap = toggle)
                        }
                        1 -> {
                            Hint("Tap buttons, then Done. Sticks can't be sent, only buttons.")
                            ControllerDrawing(
                                output = true, lit = { it in chord }, onTap = { p -> p.code?.let(toggle) },
                                modifier = Modifier.widthIn(max = 640.dp),
                            )
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EXTRA_PAD.forEach { FilterChip(selected = it in chord, onClick = { toggle(it) }, label = { Text(keyName(it)) }) }
                            }
                        }
                        else -> SystemTab(onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = Color.Gray)

@Composable
private fun SystemTab(onPick: (Combo) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("Search every Android key") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        if (query.isNotEmpty()) {
            ALL_KEYS.filter { keyName(it).contains(query, ignoreCase = true) }.forEach { k -> PickRow(keyName(k)) { onPick(listOf(k)) } }
            return@Column
        }
        Header("Android")
        SYSTEM.forEach { (label, k) -> PickRow(label) { onPick(listOf(k)) } }
        Header("Media and volume")
        MEDIA.forEach { (label, k) -> PickRow(label) { onPick(listOf(k)) } }
        Header("Chrome shortcuts")
        CHROME.forEach { (label, combo) -> PickRow(label, comboName(combo)) { onPick(combo) } }
        Header("Other")
        PickRow("Do nothing", "Disables the button") { onPick(emptyList()) }
    }
}
