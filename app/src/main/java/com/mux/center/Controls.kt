package com.mux.center

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class Tile(
    val id: String,
    val label: String,
    val icon: Ic,
    val on: (Context) -> Boolean,
    val sub: (Context) -> String,
    val click: (Context) -> Unit,
    val long: ((Context) -> Unit)? = null
)

class Shortcut(val pkg: String, val label: String)

/** Everything the Control Center can do. Android blocks some switches for normal apps, those open the system panel. */
object Controls {
    var torch by mutableStateOf(false)
    private var torchId: String? = null
    private var torchRegistered = false

    val shortcuts = listOf(
        Shortcut("com.mahfuz0712.calculator", "CALC"),
        Shortcut("com.mux.recorder", "REC"),
        Shortcut("com.mux.compass", "COMPASS"),
        Shortcut("com.mux.notes", "NOTES"),
        Shortcut("com.device.mux.manager", "DEVICE")
    )

    fun go(ctx: Context, action: String, withPackage: Boolean = false) {
        try {
            val i = Intent(action)
            if (withPackage) i.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { }
        }
        CenterService.instance?.closePanel()
    }

    fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        CenterService.instance?.closePanel()
    }

    private fun internetPanel(): String =
        if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS

    fun launch(ctx: Context, pkg: String) {
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        CenterService.instance?.closePanel()
    }

    // ------------------------------------------------------------------ state readers

    fun wifiOn(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) { false }

    fun wifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun btOn(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    } catch (e: Exception) { false }

    fun airplane(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun dataOn(ctx: Context): Boolean = Settings.Global.getInt(ctx.contentResolver, "mobile_data", 0) == 1

    fun saver(ctx: Context): Boolean = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode

    fun dnd(ctx: Context): Boolean {
        val f = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter
        return f > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun ringer(ctx: Context): Int = (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode

    fun location(ctx: Context): Boolean = try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) { false }

    fun rotate(ctx: Context): Boolean = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun dark(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun nfc(ctx: Context): Boolean = try { NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true } catch (e: Exception) { false }

    // ------------------------------------------------------------------ actions

    fun initTorch(ctx: Context) {
        if (torchRegistered) return
        torchRegistered = true
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchId = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) torch = enabled
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { }
    }

    fun toggleTorch(ctx: Context) {
        initTorch(ctx)
        val id = torchId ?: return
        try { (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, !torch) } catch (e: Exception) { }
    }

    fun cycleRinger(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE ->
                    if (nm.isNotificationPolicyAccessGranted) am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    else am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                else -> am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: SecurityException) {
            go(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    fun toggleDnd(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            go(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return
        }
        nm.setInterruptionFilter(
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    fun toggleRotate(ctx: Context) {
        if (!Settings.System.canWrite(ctx)) {
            go(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
            return
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotate(ctx)) 0 else 1)
    }

    fun brightness(ctx: Context): Float =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun autoBrightness(ctx: Context): Boolean =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setBrightness(ctx: Context, f: Float) {
        if (!Settings.System.canWrite(ctx)) {
            go(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
            return
        }
        val cr = ctx.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (f * 255f).toInt().coerceIn(1, 255))
    }

    fun toggleAutoBrightness(ctx: Context) {
        if (!Settings.System.canWrite(ctx)) {
            go(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
            return
        }
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (autoBrightness(ctx)) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
    }

    fun volume(ctx: Context, stream: Int): Float {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        return am.getStreamVolume(stream).toFloat() / max
    }

    fun setVolume(ctx: Context, stream: Int, f: Float) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try { am.setStreamVolume(stream, (f * am.getStreamMaxVolume(stream)).toInt(), 0) } catch (e: SecurityException) { }
    }

    private val timeouts = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)

    fun timeoutMs(ctx: Context): Int = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)

    fun timeoutLabel(ms: Int): String = if (ms < 60_000) "${ms / 1000}s" else "${ms / 60_000}m"

    fun cycleTimeout(ctx: Context) {
        if (!Settings.System.canWrite(ctx)) {
            go(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, true)
            return
        }
        val cur = timeoutMs(ctx)
        val next = timeouts.firstOrNull { it > cur } ?: timeouts.first()
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, next)
    }

    private fun global(action: Int) { CenterService.instance?.act(action) }

    private fun levelName(l: Int): String = when (l) { 0 -> "Off"; 1 -> "Low"; 2 -> "Medium"; else -> "High" }

    // ------------------------------------------------------------------ tiles (the first two are shown wide)

    val tiles: List<Tile> = listOf(
        Tile("wifi", "Wi-Fi", Ic.WIFI, { wifiOn(it) }, { if (wifiConnected(it)) "Connected" else if (wifiOn(it)) "On" else "Off" },
            { go(it, internetPanel()) }, { go(it, Settings.ACTION_WIFI_SETTINGS) }),
        Tile("bluetooth", "Bluetooth", Ic.BLUETOOTH, { btOn(it) }, { if (btOn(it)) "On" else "Off" },
            { go(it, Settings.ACTION_BLUETOOTH_SETTINGS) }, { go(it, Settings.ACTION_BLUETOOTH_SETTINGS) }),
        Tile("airplane", "Airplane", Ic.AIRPLANE, { airplane(it) }, { if (airplane(it)) "On" else "Off" },
            { go(it, Settings.ACTION_AIRPLANE_MODE_SETTINGS) }),
        Tile("data", "Mobile Data", Ic.DATA, { dataOn(it) }, { if (dataOn(it)) "On" else "Off" },
            { go(it, internetPanel()) }, { go(it, Settings.ACTION_DATA_ROAMING_SETTINGS) }),
        Tile("hotspot", "Hotspot", Ic.HOTSPOT, { false }, { "Open" }, { go(it, Settings.ACTION_WIRELESS_SETTINGS) }),
        Tile("saver", "Battery Saver", Ic.BATTERY, { saver(it) }, { if (saver(it)) "On" else "Off" },
            { go(it, Settings.ACTION_BATTERY_SAVER_SETTINGS) }),
        Tile("dnd", "Do Not Disturb", Ic.MOON, { dnd(it) }, { if (dnd(it)) "On" else "Off" }, { toggleDnd(it) }),
        Tile("location", "Location", Ic.PIN, { location(it) }, { if (location(it)) "On" else "Off" },
            { go(it, Settings.ACTION_LOCATION_SOURCE_SETTINGS) }),
        Tile("rotate", "Auto Rotate", Ic.ROTATE, { rotate(it) }, { if (rotate(it)) "On" else "Off" }, { toggleRotate(it) }),
        Tile("torch", "Flashlight", Ic.TORCH, { torch }, { if (torch) "On" else "Off" }, { toggleTorch(it) }),
        Tile("sound", "Sound", Ic.BELL, { ringer(it) == AudioManager.RINGER_MODE_NORMAL },
            { when (ringer(it)) { AudioManager.RINGER_MODE_NORMAL -> "Ring"; AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; else -> "Silent" } },
            { cycleRinger(it) }),
        Tile("dark", "Dark Mode", Ic.CONTRAST, { dark(it) }, { if (dark(it)) "On" else "Off" },
            { go(it, Settings.ACTION_DISPLAY_SETTINGS) }),
        Tile("eye", "Eye Care", Ic.EYE, { Prefs.eyeLevel > 0 }, { levelName(Prefs.eyeLevel) },
            { Prefs.eyeLevel = if (Prefs.eyeLevel == 0) 2 else 0; Prefs.save() },
            { Prefs.eyeLevel = if (Prefs.eyeLevel >= 3) 1 else Prefs.eyeLevel + 1; Prefs.save() }),
        Tile("reading", "Reading Mode", Ic.BOOK, { Prefs.reading }, { if (Prefs.reading) "On" else "Off" },
            { Prefs.reading = !Prefs.reading; Prefs.save() }),
        Tile("dim", "Extra Dim", Ic.DIM, { Prefs.dimLevel > 0 }, { levelName(Prefs.dimLevel) },
            { Prefs.dimLevel = (Prefs.dimLevel + 1) % 4; Prefs.save() }),
        Tile("night", "Night Light", Ic.MOON, { false }, { "Open" }, { go(it, Settings.ACTION_NIGHT_DISPLAY_SETTINGS) }),
        Tile("awake", "Keep Awake", Ic.SUN, { Prefs.awake }, { if (Prefs.awake) "On" else "Off" },
            { Prefs.awake = !Prefs.awake; Prefs.save() }),
        Tile("timeout", "Screen Timeout", Ic.CLOCK, { false }, { timeoutLabel(timeoutMs(it)) }, { cycleTimeout(it) }),
        Tile("nfc", "NFC", Ic.NFC, { nfc(it) }, { if (nfc(it)) "On" else "Off" }, { go(it, Settings.ACTION_NFC_SETTINGS) }),
        Tile("cast", "Cast", Ic.CAST, { false }, { "Open" }, { go(it, Settings.ACTION_CAST_SETTINGS) }),
        Tile("screenshot", "Screenshot", Ic.SHOT, { false }, { "" }, { global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }),
        Tile("camera", "Camera", Ic.CAMERA, { false }, { "" }, { start(it, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }),
        Tile("calc", "Calculator", Ic.CALC, { false }, { "" },
            { start(it, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)) }),
        Tile("clock", "Alarms", Ic.CLOCK, { false }, { "" }, { start(it, Intent(AlarmClock.ACTION_SHOW_ALARMS)) }),
        Tile("lock", "Lock", Ic.LOCK, { false }, { "" }, { global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }),
        Tile("power", "Power", Ic.POWER, { false }, { "" }, { global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }),
        Tile("recents", "Recents", Ic.RECENTS, { false }, { "" }, { global(AccessibilityService.GLOBAL_ACTION_RECENTS) }),
        Tile("settings", "Settings", Ic.GEAR, { false }, { "" }, { go(it, Settings.ACTION_SETTINGS) })
    )

    /** Wi-Fi and Bluetooth are always shown. The user can hide the rest in the TILES tab. */
    fun visible(): List<Tile> = tiles.filter { it.id == "wifi" || it.id == "bluetooth" || it.id !in Prefs.hiddenTiles }
}