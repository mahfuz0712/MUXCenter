package com.mux.center

import android.Manifest
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent { App() }
    }
}

private val TABS = listOf("SETUP", "LOOK", "TILES", "GESTURES", "SYS")

@Composable
fun App() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tab by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) Access.tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        if (isOnline(ctx)) update = checkUpdate(ctx).second
    }
    update?.let { u ->
        UpdateDialog(
            u, appVersion(ctx),
            onUpdate = { openUrl(ctx, u.apkUrl); update = null },
            onCancel = { update = null }
        )
    }

    HudBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> SetupScreen()
                    1 -> LookScreen()
                    2 -> TilesScreen()
                    3 -> GestureScreen()
                    else -> SysScreen { update = it }
                }
            }
            NavBar(tab) { tab = it }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("MUX CENTER", 20f, cMain, true, Modifier.weight(1f))
            val running = CenterService.instance != null
            T(if (running) "[ ONLINE ]" else "[ OFFLINE ]", 11f, if (running) cGood else cWarn, true, align = TextAlign.End)
        }
        T("// MUX SHELL CORE", 11f, cSub)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(cDim))
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF050B10)).padding(top = 10.dp, bottom = 6.dp)) {
        TABS.forEachIndexed { i, name ->
            Column(
                Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                T(name, 12f, if (i == selected) cMain else cSub, i == selected)
                Box(
                    Modifier.padding(top = 4.dp).width(28.dp).height(2.dp)
                        .background(if (i == selected) cMain else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun Step(title: String, desc: String, done: Boolean?, action: String, onAction: () -> Unit) {
    Panel(title) {
        T(desc, 11f, cSub, lines = 7)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done != null) {
                T(if (done) "DONE" else "NOT DONE", 12f, if (done) cGood else cWarn, true, Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (done == true) Chip(action, false, Modifier.width(160.dp), onAction) else HudKey(action, Modifier.width(160.dp), onAction)
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            T(title, 12f, cText, true, lines = 2)
            T(desc, 10f, cSub, lines = 3)
        }
        Chip(if (on) "ON" else "OFF", on, Modifier.width(64.dp)) { onChange(!on) }
    }
}

// ---------------------------------------------------------------------- setup

@Composable
private fun SetupScreen() {
    val ctx = LocalContext.current
    val t = Access.tick
    val a11y = remember(t) { Access.a11y(ctx) }
    val listener = remember(t) { Access.listener(ctx) }
    val write = remember(t) { Access.writeSettings(ctx) }
    val dnd = remember(t) { Access.dnd(ctx) }
    val phone = remember(t) { Access.phone(ctx) }
    val battery = remember(t) { Access.battery(ctx) }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Access.tick++ }

    fun test(kind: Int) {
        val s = CenterService.instance
        if (s == null) Toast.makeText(ctx, "Turn on the accessibility service first", Toast.LENGTH_SHORT).show() else s.openPanel(kind)
    }

    Page {
        T("SETUP", 16f, cMain, true)
        TwoCol(
            {
                Panel("STATUS") {
                    KV("HUD OVERLAY SERVICE", if (CenterService.instance != null) "RUNNING" else "STOPPED", if (CenterService.instance != null) cGood else cWarn)
                    KV("NOTIFICATION READER", if (Notifs.connected) "CONNECTED" else "NOT CONNECTED", if (Notifs.connected) cGood else cWarn)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("TEST NOTIFICATIONS", false, Modifier.weight(1f)) { test(1) }
                        Chip("TEST CONTROLS", false, Modifier.weight(1f)) { test(2) }
                    }
                    T("After setup, swipe down from the top edge of the screen: the left part opens Notifications, the right part opens Controls.", 10f, cSub, lines = 4, mod = Modifier.padding(top = 8.dp))
                }
                Step(
                    "STEP 1  ALLOW RESTRICTED SETTINGS",
                    "Android 13 and newer blocks accessibility for apps installed from an APK. Open App info, tap the three dots at the top right, then Allow restricted settings. If you do not see it, go to step 2.",
                    null, "OPEN APP INFO"
                ) { Controls.go(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
                Step(
                    "STEP 2  ACCESSIBILITY SERVICE",
                    "Open Installed apps and turn on MUX Center. This lets the app draw the HUD status bar, the panels and catch the swipe gesture. It does not read your screen.",
                    a11y, "OPEN ACCESSIBILITY"
                ) { Controls.go(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            },
            {
                Step(
                    "STEP 3  NOTIFICATION ACCESS",
                    "Lets MUX Center show your notifications, dismiss them, and show the media player.",
                    listener, "OPEN NOTIFICATION ACCESS"
                ) { Controls.go(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                Step(
                    "STEP 4  MODIFY SYSTEM SETTINGS",
                    "Needed for the brightness slider and the auto-rotate tile.",
                    write, "ALLOW"
                ) { Controls.go(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true) }
                Step(
                    "OPTIONAL  DO NOT DISTURB",
                    "Lets the Do Not Disturb tile and the Silent mode work directly.",
                    dnd, "ALLOW"
                ) { Controls.go(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
                Step(
                    "OPTIONAL  PHONE STATE",
                    "Shows the mobile signal bars, the 4G or 5G label and the VoLTE tag in the status bar.",
                    phone, "ALLOW"
                ) { phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
                Step(
                    "RECOMMENDED  KEEP IT ALIVE",
                    "Set battery to No restrictions so the system does not stop the service. On Xiaomi, MIUI and HyperOS also turn on Autostart for MUX Center in the Security app.",
                    battery, "OPEN BATTERY"
                ) { Controls.go(ctx, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
            }
        )
    }
}

// ---------------------------------------------------------------------- look and gestures

@Composable
private fun LookScreen() {
    var picker by remember { mutableStateOf(false) }
    Page {
        T("LOOK", 16f, cMain, true)
        TwoCol(
            {
                Panel("INTERFACE COLOR") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ACCENTS.forEachIndexed { i, a ->
                            Chip(a.name, Prefs.accent == i, Modifier.weight(1f)) { Prefs.accent = i; Prefs.save() }
                        }
                    }
                }
                Panel("STATUS BAR") {
                    ToggleRow("HUD STATUS BAR", "Replaces the system status bar with the HUD one.", Prefs.barEnabled) { Prefs.barEnabled = it; Prefs.save() }
                    T("BACKGROUND", 10f, cSub, mod = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SOLID", "SEGMENTED", "CLEAR").forEachIndexed { i, s ->
                            Chip(s, Prefs.barStyle == i, Modifier.weight(1f)) { Prefs.barStyle = i; Prefs.save() }
                        }
                    }
                    T(
                        "SOLID covers the system bar fully. SEGMENTED draws HUD chips with transparent gaps. CLEAR has no background at all. " +
                            "Android cannot hide the real status bar icons, so with CLEAR (and in the gaps) the system icons can show through underneath.",
                        10f, cSub, lines = 7, mod = Modifier.padding(top = 6.dp)
                    )
                }
            },
            {
                Panel("FULL SCREEN") {
                    ToggleRow(
                        "HIDE IN FULL SCREEN VIDEO AND GAMES (EXPERIMENTAL)",
                        "Uses what Android reports about the system bar. Some phones do not report it, use the options below too.",
                        Prefs.hideInFullscreen
                    ) { Prefs.hideInFullscreen = it; Prefs.save() }
                    ToggleRow("HIDE IN LANDSCAPE", "Videos and games are almost always full screen when the phone is sideways.", Prefs.hideLandscape) {
                        Prefs.hideLandscape = it; Prefs.save()
                    }
                    ToggleRow(
                        "SWIPE FROM THE TOP EDGE EVEN WHEN HIDDEN",
                        "Keeps a thin invisible strip at the top so you can still open the panels in full screen.",
                        Prefs.edgeSwipe
                    ) { Prefs.edgeSwipe = it; Prefs.save() }
                    Chip("HIDE IN CHOSEN APPS (${Prefs.hideApps.size})", false, Modifier.fillMaxWidth().padding(top = 8.dp)) { picker = true }
                }
                Panel("CLOCK") {
                    ToggleRow("24 HOUR CLOCK", "Off shows AM and PM.", Prefs.use24h) { Prefs.use24h = it; Prefs.save() }
                    ToggleRow("SHOW SECONDS", "", Prefs.showSeconds) { Prefs.showSeconds = it; Prefs.save() }
                    ToggleRow("SHOW DATE", "", Prefs.showDate) { Prefs.showDate = it; Prefs.save() }
                }
            }
        )
    }
    if (picker) AppPickerDialog { picker = false }
}

private class AppEntry(val label: String, val pkg: String)

@Composable
private fun AppPickerDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(i, 0)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != ctx.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    val shape = HudShape(10.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).background(Bg, shape).border(1.dp, cDim, shape).padding(12.dp)
        ) {
            T("// HIDE THE HUD BAR IN THESE APPS", 12f, cSub, true, Modifier.padding(bottom = 8.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = cText, fontFamily = Mono, fontSize = 14.sp),
                cursorBrush = SolidColor(cMain),
                modifier = Modifier.fillMaxWidth().border(1.dp, cDim).padding(10.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) T("SEARCH APPS...", 12f, cSub)
                        inner()
                    }
                }
            )
            val list = apps.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                items(list, key = { it.pkg }) { a ->
                    val hidden = a.pkg in Prefs.hideApps
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            T(a.label, 13f, cText, true)
                            T(a.pkg, 9f, cSub)
                        }
                        Chip(if (hidden) "HIDDEN" else "SHOWN", hidden, Modifier.width(84.dp)) {
                            Prefs.hideApps = if (hidden) Prefs.hideApps - a.pkg else Prefs.hideApps + a.pkg
                            Prefs.save()
                        }
                    }
                }
            }
            Chip("DONE", true, Modifier.fillMaxWidth().padding(top = 8.dp)) { onClose() }
        }
    }
}

@Composable
private fun TilesScreen() {
    Page {
        T("TILES", 16f, cMain, true)
        TwoCol(
            {
                Panel("SCREEN FILTERS (TRY THEM HERE)") {
                    T("EYE CARE (WARM LIGHT)", 10f, cSub, mod = Modifier.padding(bottom = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("OFF", "LOW", "MED", "HIGH").forEachIndexed { i, s ->
                            Chip(s, Prefs.eyeLevel == i, Modifier.weight(1f)) { Prefs.eyeLevel = i; Prefs.save() }
                        }
                    }
                    T("EXTRA DIM", 10f, cSub, mod = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("OFF", "LOW", "MED", "HIGH").forEachIndexed { i, s ->
                            Chip(s, Prefs.dimLevel == i, Modifier.weight(1f)) { Prefs.dimLevel = i; Prefs.save() }
                        }
                    }
                    ToggleRow("READING MODE", "Paper like warm tint with a little dimming.", Prefs.reading) { Prefs.reading = it; Prefs.save() }
                    ToggleRow("KEEP SCREEN AWAKE", "The screen does not turn off while this is on.", Prefs.awake) { Prefs.awake = it; Prefs.save() }
                    T("In the Control Center, tap Eye Care to switch it, long press to change the strength.", 10f, cSub, lines = 3, mod = Modifier.padding(top = 6.dp))
                }
            },
            {
                Panel("CONTROL CENTER TILES") {
                    T("Choose which tiles the Control Center shows. Wi-Fi and Bluetooth are always there.", 10f, cSub, lines = 3, mod = Modifier.padding(bottom = 6.dp))
                    for (t in Controls.tiles) {
                        if (t.id == "wifi" || t.id == "bluetooth") continue
                        ToggleRow(t.label.uppercase(), "", t.id !in Prefs.hiddenTiles) { show ->
                            Prefs.hiddenTiles = if (show) Prefs.hiddenTiles - t.id else Prefs.hiddenTiles + t.id
                            Prefs.save()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun GestureScreen() {
    Page {
        T("GESTURES", 16f, cMain, true)
        TwoCol(
            {
                Panel("SWIPE DOWN") {
                    ToggleRow("SWIPE GESTURE", "Swipe down on the status bar to open the panels.", Prefs.gesturesEnabled) { Prefs.gesturesEnabled = it; Prefs.save() }
                    ToggleRow("SWAP SIDES", "Right opens Notifications, left opens Controls.", Prefs.swapSides) { Prefs.swapSides = it; Prefs.save() }
                    ToggleRow("VIBRATE ON OPEN", "", Prefs.haptic) { Prefs.haptic = it; Prefs.save() }
                }
            },
            {
                Panel("SPLIT") {
                    val left = (Prefs.split * 100).toInt()
                    T(
                        "LEFT ${left}%  " + (if (Prefs.swapSides) "CONTROLS" else "NOTIFICATIONS") +
                            "     RIGHT ${100 - left}%  " + (if (Prefs.swapSides) "NOTIFICATIONS" else "CONTROLS"),
                        10f, cSub, lines = 2
                    )
                    Slider(
                        value = Prefs.split, onValueChange = { Prefs.split = it }, onValueChangeFinished = { Prefs.save() },
                        valueRange = 0.3f..0.8f,
                        colors = SliderDefaults.colors(thumbColor = cMain, activeTrackColor = cMain, inactiveTrackColor = cDim)
                    )
                    T("Close a panel by swiping up on the line at the bottom, tapping it, or pressing back.", 10f, cSub, lines = 3, mod = Modifier.padding(top = 6.dp))
                }
            }
        )
    }
}

// ---------------------------------------------------------------------- sys

@Composable
private fun SysScreen(onUpdateFound: (UpdateInfo) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    Page {
        T("SYSTEM", 16f, cMain, true)
        TwoCol(
            {
                Panel("UPDATES") {
                    KV("INSTALLED VERSION", "v${appVersion(ctx)}")
                    Chip("CHECK FOR UPDATES", false, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        scope.launch {
                            status = "CHECKING..."
                            if (!isOnline(ctx)) {
                                status = "NO INTERNET CONNECTION"
                                return@launch
                            }
                            val (ok, info) = checkUpdate(ctx)
                            status = when {
                                !ok -> "CHECK FAILED"
                                info == null -> "UP TO DATE"
                                else -> "NEW VERSION AVAILABLE: v${info.version}"
                            }
                            if (info != null) onUpdateFound(info)
                        }
                    }
                    if (status.isNotEmpty()) T(status, 11f, cSub, mod = Modifier.padding(top = 6.dp))
                }
            },
            {
                Panel("ABOUT") {
                    T("MUX CENTER", 18f, cMain, true)
                    T("v${appVersion(ctx)}  com.mux.center", 10f, cSub, mod = Modifier.padding(bottom = 8.dp))
                    T("DEVELOPER", 10f, cSub)
                    T(AppInfo.DEV_NAME, 14f, cText, true, Modifier.padding(bottom = 6.dp))
                    T("EMAIL", 10f, cSub)
                    T(AppInfo.DEV_EMAIL, 13f, cMain, mod = Modifier.clickable { openMail(ctx, AppInfo.DEV_EMAIL) }.padding(bottom = 6.dp))
                    T("GITHUB", 10f, cSub)
                    T("github.com/${AppInfo.GITHUB_USER}", 13f, cMain, mod = Modifier.clickable { openUrl(ctx, "https://github.com/${AppInfo.GITHUB_USER}") }.padding(bottom = 6.dp))
                    T("MORE APPS", 10f, cSub)
                    T("zApps Store", 13f, cMain, mod = Modifier.clickable { openUrl(ctx, AppInfo.MORE_APPS) })
                }
            }
        )
    }
}