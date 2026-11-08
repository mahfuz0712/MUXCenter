package com.mux.center

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Settings. Shared by the app screen and the accessibility service (same process). */
object Prefs {
    private lateinit var sp: SharedPreferences
    private var ready = false

    val DEFAULT_HIDE: Set<String> = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient", "org.videolan.vlc",
        "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar"
    )


    var accent by mutableIntStateOf(0)
    var barEnabled by mutableStateOf(true)
    var hideInFullscreen by mutableStateOf(true)
    var use24h by mutableStateOf(true)
    var showSeconds by mutableStateOf(false)
    var showDate by mutableStateOf(true)
    var gesturesEnabled by mutableStateOf(true)
    var split by mutableFloatStateOf(0.6f)
    var swapSides by mutableStateOf(false)
    var haptic by mutableStateOf(true)
    /** 0 solid, 1 segmented (HUD chips with clear gaps), 2 clear */
    var barStyle by mutableIntStateOf(1)
    var edgeSwipe by mutableStateOf(true)
    var hideLandscape by mutableStateOf(true)
    var hideApps by mutableStateOf<Set<String>>(DEFAULT_HIDE)
    var hiddenTiles by mutableStateOf<Set<String>>(emptySet())
    var eyeLevel by mutableIntStateOf(0)
    var reading by mutableStateOf(false)
    var dimLevel by mutableIntStateOf(0)
    var awake by mutableStateOf(false)

    fun init(ctx: Context) {
        if (ready) return
        ready = true
        sp = ctx.applicationContext.getSharedPreferences("mux_center", Context.MODE_PRIVATE)
        accent = sp.getInt("accent", 0)
        barEnabled = sp.getBoolean("bar", true)
        hideInFullscreen = sp.getBoolean("hidefs", true)
        use24h = sp.getBoolean("h24", true)
        showSeconds = sp.getBoolean("secs", false)
        showDate = sp.getBoolean("date", true)
        gesturesEnabled = sp.getBoolean("gest", true)
        split = sp.getFloat("split", 0.6f)
        swapSides = sp.getBoolean("swap", false)
        haptic = sp.getBoolean("haptic", true)
        barStyle = sp.getInt("barstyle", 1)
        edgeSwipe = sp.getBoolean("edge", true)
        hideLandscape = sp.getBoolean("hidland", true)
        hideApps = sp.getStringSet("hideapps", null)?.toSet() ?: DEFAULT_HIDE
        hiddenTiles = sp.getStringSet("hiddentiles", null)?.toSet() ?: emptySet()
        eyeLevel = sp.getInt("eye", 0)
        reading = sp.getBoolean("reading", false)
        dimLevel = sp.getInt("dim", 0)
        awake = sp.getBoolean("awake", false)
    }

    fun save() {
        if (!ready) return
        sp.edit()
            .putInt("accent", accent).putBoolean("bar", barEnabled).putBoolean("hidefs", hideInFullscreen)
            .putBoolean("h24", use24h).putBoolean("secs", showSeconds).putBoolean("date", showDate)
            .putBoolean("gest", gesturesEnabled).putFloat("split", split).putBoolean("swap", swapSides)
            .putBoolean("haptic", haptic).putInt("barstyle", barStyle).putBoolean("edge", edgeSwipe)
            .putBoolean("hidland", hideLandscape).putStringSet("hideapps", HashSet(hideApps))
            .putStringSet("hiddentiles", HashSet(hiddenTiles)).putInt("eye", eyeLevel)
            .putBoolean("reading", reading).putInt("dim", dimLevel).putBoolean("awake", awake).apply()
    }
}

/** Live UI state shared by the overlay windows. */
object UiState {
    /** 0 none, 1 notification center, 2 control center */
    var panel by mutableIntStateOf(0)
    var open by mutableStateOf(false)
    var barShown by mutableStateOf(true)
    var statusPx by mutableIntStateOf(0)
    var landscape by mutableStateOf(false)
    /** The app that is in front right now (from accessibility window events). */
    var pkg by mutableStateOf("")
}

/** Checks for the permissions and services MUX Center needs. */
object Access {
    var tick by mutableIntStateOf(0)

    fun a11y(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(ctx, CenterService::class.java)
        return s.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
    }

    fun listener(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun writeSettings(ctx: Context): Boolean = Settings.System.canWrite(ctx)

    fun dnd(ctx: Context): Boolean =
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

    fun phone(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun battery(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
}

fun f0(v: Float): String = String.format(Locale.US, "%.0f", v)

fun ago(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "now"
        d < 3_600_000 -> "${d / 60_000}m"
        d < 86_400_000 -> "${d / 3_600_000}h"
        else -> "${d / 86_400_000}d"
    }
}