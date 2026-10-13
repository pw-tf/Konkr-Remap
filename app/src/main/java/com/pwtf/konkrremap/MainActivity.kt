@file:OptIn(ExperimentalMaterial3Api::class)

package com.pwtf.konkrremap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accent = Color(0xFFFF6B2C)
private val Ok = Color(0xFF3DDC84)
private val Colors = darkColorScheme(
    primary = Accent, onPrimary = Color.White,
    secondaryContainer = Color(0xFF2A2D36), onSecondaryContainer = Color.White,
    background = Color(0xFF14161C), surface = Color(0xFF14161C),
    surfaceVariant = Color(0xFF1E2129), surfaceContainerHigh = Color(0xFF22252E),
)

/** Set while the "press a button" dialog is open; MainActivity routes the next button to it. */
private var capture by mutableStateOf<((Int) -> Unit)?>(null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        onInstallStatus(intent)
        setContent { MaterialTheme(colorScheme = Colors) { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onInstallStatus(intent)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val c = capture ?: return super.dispatchKeyEvent(e)
        // Capture on release so neither half of the press leaks into the UI.
        if (e.action == KeyEvent.ACTION_UP) { capture = null; c(e.keyCode) }
        return true
    }

    private fun onInstallStatus(i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)) {
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                @Suppress("DEPRECATION") i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let(::startActivity)
            else -> Toast.makeText(this, "Update failed: ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var helper by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var profile by rememberSaveable { mutableStateOf(ALL_APPS) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var pickingApp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { Updater.check() } }
    LaunchedEffect(Unit) {
        while (true) {
            helper = withContext(Dispatchers.IO) { Injector.connected() }
            service = Adb.serviceEnabled(ctx)
            delay(1500)
        }
    }

    fun startHelper() {
        busy = true
        scope.launch {
            message = withContext(Dispatchers.IO) { Adb.startHelper(ctx) }
            busy = false
        }
    }
    val pairLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { message = "Allow notifications so you can type the pairing code."; return@rememberLauncherForActivityResult }
        Pairing.prompt(ctx)
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(":settings:fragment_args_key", "toggle_adb_wireless"))
        }.onFailure { message = "Turn on Developer options first: Settings > About > tap Build number 7 times." }
    }

    Box {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { capture = { editing = it } },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add button") },
                    containerColor = Accent, contentColor = Color.White,
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Konkr Remap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                }
                update?.let { r ->
                    item {
                        Banner("Version ${r.version} is available", "Update") {
                            busy = true
                            scope.launch {
                                message = withContext(Dispatchers.IO) { runCatching { Updater.install(ctx, r) }.exceptionOrNull()?.let { "Download failed: ${it.message}" } }
                                busy = false
                            }
                        }
                    }
                }
                if (!helper || !service) {
                    item {
                        Step(1, "Start the helper", helper,
                            "Lets remapped keys reach every app. Needs Wi-Fi and Developer options. Pair once; after that it restarts by itself.") {
                            FilledTonalButton(onClick = { pairLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, enabled = !busy) { Text("Pair") }
                            Button(onClick = ::startHelper, enabled = !busy) { Text("Start") }
                        }
                    }
                    item {
                        Step(2, "Turn on remapping", service, "Lets Konkr Remap see your button presses.") {
                            Button(onClick = { if (!Adb.enableService(ctx)) ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Turn on") }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Ok, CircleShape))
                            Text("  Remapping is active", color = Ok, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                message?.let { item { Text(it, color = Accent, style = MaterialTheme.typography.bodyMedium) } }
    
                item { ProfileChips(profile, onSelect = { profile = it }, onAdd = { pickingApp = true }) }
    
                val mappings = Store.profiles[profile].orEmpty().entries.sortedBy { keyName(it.key) }
                if (mappings.isEmpty()) {
                    item {
                        Text(
                            if (profile == ALL_APPS) "No buttons remapped yet. Tap Add button, then press a button on your Konkr."
                            else "No overrides for this app. Buttons here replace the All apps setup, only in this app.",
                            color = Color.Gray, modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(mappings, key = { it.key }) { (key, m) -> MappingRow(key, m) { editing = key } }
            }
        }
    
        // An overlay, not a Dialog: dialogs are separate windows and would swallow the key before MainActivity sees it.
        if (capture != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).clickable { capture = null },
                contentAlignment = Alignment.Center,
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Press a button", style = MaterialTheme.typography.titleLarge)
                        Text("Press the button on your Konkr you want to remap.", color = Color.LightGray, modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { capture = null }) { Text("Cancel") }
                    }
                }
            }
        }
    }
    editing?.let { key -> EditDialog(profile, key) { editing = null } }
    if (pickingApp) AppPicker(onPick = { Store.addProfile(it); profile = it; pickingApp = false }, onDismiss = { pickingApp = false })
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.15f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f))
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, body: String, actions: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(if (done) Ok else Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (done) "✓" else "$n", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("  $title", style = MaterialTheme.typography.titleMedium)
            }
            if (!done) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

@Composable
private fun ProfileChips(selected: String, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(ALL_APPS) + Store.profiles.keys.filter { it != ALL_APPS }.sorted()).forEach { p ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(if (p == ALL_APPS) "All apps" else appLabel(ctx, p)) },
                trailingIcon = if (p != ALL_APPS && p == selected) {
                    { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp).clickable { Store.removeProfile(p); onSelect(ALL_APPS) }) }
                } else null,
            )
        }
        FilterChip(selected = false, onClick = onAdd, label = { Text("App") }, leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) })
    }
}

@Composable
private fun MappingRow(key: Int, m: Mapping, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Keycap(keyName(key))
            Text("  →  ", color = Accent, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(comboName(m.tap), style = MaterialTheme.typography.titleMedium)
                val extra = listOfNotNull(m.hold?.let { "Hold: ${comboName(it)}" }, m.double?.let { "Double: ${comboName(it)}" })
                if (extra.isNotEmpty()) Text(extra.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun Keycap(text: String) {
    Text(
        text,
        Modifier.background(Color(0xFF2E323D), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
    )
}

private enum class Slot(val label: String) { Tap("Press"), Hold("Hold"), Double("Double press") }

@Composable
private fun EditDialog(profile: String, key: Int, onDismiss: () -> Unit) {
    val existing = Store.profiles[profile]?.get(key)
    var m by remember { mutableStateOf(existing ?: Mapping(listOf(key))) }
    var picking by remember { mutableStateOf<Slot?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Keycap(keyName(key)); Text("  does…") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot.entries.forEach { slot ->
                    val combo = when (slot) { Slot.Tap -> m.tap; Slot.Hold -> m.hold; Slot.Double -> m.double }
                    Card(onClick = { picking = slot }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(slot.label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Text(combo?.let(::comboName) ?: "Not set", style = MaterialTheme.typography.titleMedium)
                            }
                            if (slot != Slot.Tap && combo != null) {
                                Icon(Icons.Default.Close, "Clear", Modifier.clickable {
                                    m = if (slot == Slot.Hold) m.copy(hold = null) else m.copy(double = null)
                                })
                            }
                        }
                    }
                }
                Text("Hold and Double press are optional. Leave them unset for an instant 1:1 remap, which games need.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = { Store.put(profile, key, m); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { Store.put(profile, key, null); onDismiss() }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    picking?.let { slot ->
        KeyPicker(onDismiss = { picking = null }) { c ->
            m = when (slot) { Slot.Tap -> m.copy(tap = c); Slot.Hold -> m.copy(hold = c); Slot.Double -> m.copy(double = c) }
            picking = null
        }
    }
}

private val MODIFIERS = listOf(
    KeyEvent.KEYCODE_CTRL_LEFT to "Ctrl", KeyEvent.KEYCODE_SHIFT_LEFT to "Shift",
    KeyEvent.KEYCODE_ALT_LEFT to "Alt", KeyEvent.KEYCODE_META_LEFT to "Meta",
)

private fun ctrl(vararg k: Int) = listOf(KeyEvent.KEYCODE_CTRL_LEFT) + k.toList()
private val CHROME = listOf(
    "Next tab" to ctrl(KeyEvent.KEYCODE_TAB),
    "Previous tab" to ctrl(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_TAB),
    "New tab" to ctrl(KeyEvent.KEYCODE_T),
    "Close tab" to ctrl(KeyEvent.KEYCODE_W),
    "Reload" to ctrl(KeyEvent.KEYCODE_R),
    "Back" to listOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
    "Forward" to listOf(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT),
    "Address bar" to ctrl(KeyEvent.KEYCODE_L),
    "Find in page" to ctrl(KeyEvent.KEYCODE_F),
    "Zoom in" to ctrl(KeyEvent.KEYCODE_EQUALS),
    "Zoom out" to ctrl(KeyEvent.KEYCODE_MINUS),
)
private val COMMON = listOf(
        KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
)
private val ALL_KEYS = (1..KeyEvent.getMaxKeyCode()).filter { !KeyEvent.keyCodeToString(it)[0].isDigit() }

@Composable
private fun KeyPicker(onDismiss: () -> Unit, onPick: (Combo) -> Unit) {
    var mods by remember { mutableStateOf(emptyList<Int>()) }
    var query by remember { mutableStateOf("") }
    val pickKey = { k: Int -> onPick(mods + k) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a key", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Close", Modifier.clickable(onClick = onDismiss))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hold with:", color = Color.Gray)
                    MODIFIERS.forEach { (code, label) ->
                        FilterChip(selected = code in mods, onClick = { mods = if (code in mods) mods - code else mods + code }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Search all keys") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
                )
                LazyColumn(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        item { Header("Chrome shortcuts") }
                        items(CHROME) { (label, combo) -> PickRow(label, comboName(combo)) { onPick(combo) } }
                        item { Header("Common") }
                        item { PickRow("Do nothing", "Disables the button") { onPick(emptyList()) } }
                        items(COMMON) { k -> PickRow(keyName(k)) { pickKey(k) } }
                        item { Header("All keys") }
                    }
                    items(ALL_KEYS.filter { query.isEmpty() || keyName(it).contains(query, ignoreCase = true) }) { k ->
                        PickRow(keyName(k)) { pickKey(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, Modifier.padding(top = 16.dp, bottom = 4.dp), color = Accent, style = MaterialTheme.typography.labelLarge)

@Composable
private fun PickRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
    }
}

@Composable
private fun AppPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val apps = remember {
        ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }
            .map { it to appLabel(ctx, it) }.sortedBy { it.second.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add an app", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Close, "Close", Modifier.clickable(onClick = onDismiss))
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search apps") }, singleLine = true)
                LazyColumn(Modifier.weight(1f)) {
                    items(apps.filter { it.second.contains(query, ignoreCase = true) }) { (pkg, label) ->
                        PickRow(label, pkg) { onPick(pkg) }
                    }
                }
            }
        }
    }
}

private fun appLabel(ctx: Context, pkg: String) =
    runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

private val NAMES = mapOf(
    KeyEvent.KEYCODE_DEL to "Backspace", KeyEvent.KEYCODE_FORWARD_DEL to "Delete", KeyEvent.KEYCODE_APP_SWITCH to "Recent apps",
    KeyEvent.KEYCODE_ESCAPE to "Esc", KeyEvent.KEYCODE_EQUALS to "=", KeyEvent.KEYCODE_MINUS to "-",
)
private val MOD_NAMES = MODIFIERS.toMap() + mapOf(
    KeyEvent.KEYCODE_CTRL_RIGHT to "Ctrl", KeyEvent.KEYCODE_SHIFT_RIGHT to "Shift",
    KeyEvent.KEYCODE_ALT_RIGHT to "Alt", KeyEvent.KEYCODE_META_RIGHT to "Meta",
)

/** KEYCODE_BUTTON_L1 -> "Button L1". */
fun keyName(code: Int): String = NAMES[code] ?: KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").split('_')
    .joinToString(" ") { w -> if (w.length == 1 || w.any(Char::isDigit)) w else w.lowercase().replaceFirstChar(Char::uppercase) }

fun comboName(c: Combo): String =
    if (c.isEmpty()) "Nothing" else c.dropLast(1).map { MOD_NAMES[it] ?: keyName(it) }.plus(keyName(c.last())).joinToString(" + ")
