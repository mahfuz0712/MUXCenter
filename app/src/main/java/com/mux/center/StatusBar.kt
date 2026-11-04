package com.mux.center

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Live system info for the status bar, refreshed once a second by the service. */
object Sys {
    var time by mutableStateOf("")
    var date by mutableStateOf("")
    var battery by mutableIntStateOf(0)
    var charging by mutableStateOf(false)
    var wifi by mutableIntStateOf(-1)
    var signal by mutableIntStateOf(-1)
    var net by mutableStateOf("")
    var bt by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var airplane by mutableStateOf(false)
    var muted by mutableStateOf(false)
    var tick by mutableIntStateOf(0)

    private fun netName(t: Int): String = when (t) {
        20 -> "5G"
        13, 19 -> "4G"
        3, 8, 9, 10, 15, 17 -> "3G"
        1, 2, 4, 16 -> "2G"
        else -> ""
    }

    @Suppress("DEPRECATION", "MissingPermission")
    fun update(ctx: Context) {
        val now = Date()
        val pattern = when {
            Prefs.use24h && Prefs.showSeconds -> "HH:mm:ss"
            Prefs.use24h -> "HH:mm"
            Prefs.showSeconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
        time = SimpleDateFormat(pattern, Locale.US).format(now)
        date = SimpleDateFormat("EEE, MMM d", Locale.US).format(now).uppercase()

        try {
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (scale > 0) level * 100 / scale else level
                charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        } catch (e: Exception) { }

        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val onWifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            wifi = if (onWifi) {
                val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 5)
            } else -1
        } catch (e: Exception) { wifi = -1 }

        try {
            if (Access.phone(ctx)) {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                signal = if (Build.VERSION.SDK_INT >= 28) (tm.signalStrength?.level ?: -1) else -1
                net = netName(tm.dataNetworkType)
            } else {
                signal = -1
                net = ""
            }
        } catch (e: Exception) { signal = -1; net = "" }

        bt = Controls.btOn(ctx)
        dnd = Controls.dnd(ctx)
        airplane = Controls.airplane(ctx)
        muted = Controls.ringer(ctx) != android.media.AudioManager.RINGER_MODE_NORMAL
        tick++
    }
}

@Composable
private fun Bars(level: Int, count: Int) {
    val on = cMain
    val off = cDim
    Canvas(Modifier.size(width = 16.dp, height = 11.dp)) {
        val w = size.width / (count * 2f - 1f)
        for (i in 0 until count) {
            val h = size.height * (i + 1) / count
            drawRect(
                if (i < level) on else off,
                Offset(i * 2f * w, size.height - h),
                Size(w, h)
            )
        }
    }
}

@Composable
private fun BatteryIcon(pct: Int, charging: Boolean) {
    val c = if (charging) cGood else if (pct <= 15) cBad else cMain
    Canvas(Modifier.size(width = 22.dp, height = 11.dp)) {
        val bodyW = size.width - 2.dp.toPx()
        drawRect(c, Offset.Zero, Size(bodyW, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        drawRect(c, Offset(bodyW, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
        val pad = 2.dp.toPx()
        drawRect(c, Offset(pad, pad), Size((bodyW - 2 * pad) * pct.coerceIn(0, 100) / 100f, size.height - 2 * pad))
    }
}

/** The HUD status bar. Used as the overlay window and again on top of both panels. */
@Composable
fun HudStatusBar(modifier: Modifier = Modifier) {
    val dim = cDim
    Row(
        modifier.fillMaxWidth().background(Bg)
            .drawBehind { drawLine(dim, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(Sys.time, 13f, cMain, true)
        if (Prefs.showDate) T(Sys.date, 9f, cSub, mod = Modifier.padding(start = 8.dp))
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            val alerts = Notifs.items.filter { !it.ongoing && it.icon != null }
            for (n in alerts.take(5)) {
                Image(n.icon!!, null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(cText))
            }
            if (alerts.size > 5) T("+${alerts.size - 5}", 9f, cSub)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Sys.airplane) T("AIR", 8f, cWarn, true)
            if (Sys.dnd) T("DND", 8f, cWarn, true)
            if (Sys.muted) T("MUTE", 8f, cWarn, true)
            if (Sys.bt) T("BT", 8f, cMain, true)
            if (Sys.net.isNotEmpty()) T(Sys.net, 9f, cMain, true)
            if (Sys.signal >= 0) Bars(Sys.signal + 1, 5)
            if (Sys.wifi >= 0) Bars(Sys.wifi + 1, 5)
            T("${Sys.battery}%", 10f, cText, true)
            BatteryIcon(Sys.battery, Sys.charging)
        }
    }
}
