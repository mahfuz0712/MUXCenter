package com.mux.center

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Ic {
    WIFI, BLUETOOTH, AIRPLANE, DATA, HOTSPOT, BATTERY, MOON, PIN, ROTATE, TORCH, BELL, BELL_OFF,
    CONTRAST, SHOT, LOCK, POWER, RECENTS, GEAR, SUN, VOLUME, CLOSE, UP, DOWN, PLAY, PAUSE, NEXT, PREV, SHIELD
}

/** Simple line icons drawn with primitives, so the app needs no icon library. [bg] is the color behind the icon. */
@Composable
fun HudIcon(kind: Ic, size: Dp = 28.dp, color: Color = cMain, bg: Color = Color.Transparent) {
    Canvas(Modifier.size(size)) { drawIcon(kind, color, bg) }
}

fun DrawScope.drawIcon(kind: Ic, c: Color, bg: Color) {
    val u = size.minDimension / 24f
    val sw = 2f * u
    val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, p(x1, y1), p(x2, y2), sw, StrokeCap.Round)
    fun poly(vararg v: Float, close: Boolean = false, fill: Boolean = false) {
        val path = Path()
        path.moveTo(v[0] * u, v[1] * u)
        var i = 2
        while (i + 1 < v.size) { path.lineTo(v[i] * u, v[i + 1] * u); i += 2 }
        if (close) path.close()
        if (fill) drawPath(path, c) else drawPath(path, c, style = st)
    }
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(c, start, sweep, false, p(cx - r, cy - r), Size(2 * r * u, 2 * r * u), style = st)
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(c, r * u, p(cx, cy))

    when (kind) {
        Ic.WIFI -> {
            dot(12f, 19f, 1.6f)
            arc(12f, 19f, 5f, 225f, 90f); arc(12f, 19f, 9f, 225f, 90f); arc(12f, 19f, 13f, 225f, 90f)
        }
        Ic.BLUETOOTH -> poly(7f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 7f, 16.5f)
        Ic.AIRPLANE -> poly(
            12f, 2f, 14f, 9f, 22f, 14f, 22f, 16f, 14f, 13f, 14f, 19f, 17f, 21f, 17f, 22f,
            12f, 21f, 7f, 22f, 7f, 21f, 10f, 19f, 10f, 13f, 2f, 16f, 2f, 14f, 10f, 9f, close = true, fill = true
        )
        Ic.DATA -> {
            line(8f, 20f, 8f, 5f); poly(4.5f, 8.5f, 8f, 5f, 11.5f, 8.5f)
            line(16f, 4f, 16f, 19f); poly(12.5f, 15.5f, 16f, 19f, 19.5f, 15.5f)
        }
        Ic.HOTSPOT -> {
            dot(12f, 12f, 1.8f)
            for (r in listOf(5f, 9f)) { arc(12f, 12f, r, 135f, 90f); arc(12f, 12f, r, -45f, 90f) }
        }
        Ic.BATTERY -> {
            drawRect(c, p(6f, 5f), Size(12f * u, 16f * u), style = st)
            drawRect(c, p(10f, 2.5f), Size(4f * u, 2f * u))
            line(12f, 10.5f, 12f, 16.5f); line(9f, 13.5f, 15f, 13.5f)
        }
        Ic.MOON -> {
            drawCircle(c, 8f * u, p(12f, 12f))
            if (bg != Color.Transparent) drawCircle(bg, 7f * u, p(16.5f, 8.5f))
        }
        Ic.PIN -> {
            drawCircle(c, 5f * u, p(12f, 10f), style = st)
            poly(7.5f, 12.5f, 12f, 21f, 16.5f, 12.5f)
            dot(12f, 10f, 1.8f)
        }
        Ic.ROTATE -> {
            arc(12f, 12f, 7f, 200f, 270f)
            poly(9.2f, 18.4f, 13.2f, 19.6f); poly(9.2f, 18.4f, 8.2f, 14.6f)
        }
        Ic.TORCH -> {
            poly(8f, 3f, 16f, 3f, 15f, 9f, 9f, 9f, close = true, fill = true)
            drawRoundRect(c, p(10f, 10f), Size(4f * u, 11f * u), CornerRadius(1.5f * u), style = st)
        }
        Ic.BELL, Ic.BELL_OFF -> {
            arc(12f, 11f, 6f, 180f, 180f)
            line(6f, 11f, 6f, 17f); line(18f, 11f, 18f, 17f); line(4f, 17f, 20f, 17f)
            dot(12f, 20f, 1.5f)
            if (kind == Ic.BELL_OFF) line(4f, 3.5f, 20f, 20.5f)
        }
        Ic.CONTRAST -> {
            drawCircle(c, 8f * u, p(12f, 12f), style = st)
            drawArc(c, 270f, 180f, true, p(4f, 4f), Size(16f * u, 16f * u))
        }
        Ic.SHOT -> {
            poly(4f, 9f, 4f, 4f, 9f, 4f); poly(15f, 4f, 20f, 4f, 20f, 9f)
            poly(20f, 15f, 20f, 20f, 15f, 20f); poly(9f, 20f, 4f, 20f, 4f, 15f)
        }
        Ic.LOCK -> {
            drawRoundRect(c, p(5f, 11f), Size(14f * u, 10f * u), CornerRadius(2f * u), style = st)
            arc(12f, 8f, 4f, 180f, 180f)
            line(8f, 8f, 8f, 11f); line(16f, 8f, 16f, 11f)
        }
        Ic.POWER -> {
            arc(12f, 12f, 8f, -60f, 300f)
            line(12f, 3f, 12f, 12f)
        }
        Ic.RECENTS -> {
            drawRect(c, p(4f, 8f), Size(12f * u, 12f * u), style = st)
            drawRect(c, p(8f, 4f), Size(12f * u, 12f * u), style = st)
        }
        Ic.GEAR -> {
            drawCircle(c, 4f * u, p(12f, 12f), style = st)
            for (i in 0 until 8) {
                val a = i * PI / 4
                line(
                    (12 + 6.8 * cos(a)).toFloat(), (12 + 6.8 * sin(a)).toFloat(),
                    (12 + 9.5 * cos(a)).toFloat(), (12 + 9.5 * sin(a)).toFloat()
                )
            }
        }
        Ic.SUN -> {
            drawCircle(c, 4.5f * u, p(12f, 12f), style = st)
            for (i in 0 until 8) {
                val a = i * PI / 4
                line(
                    (12 + 7.5 * cos(a)).toFloat(), (12 + 7.5 * sin(a)).toFloat(),
                    (12 + 10.5 * cos(a)).toFloat(), (12 + 10.5 * sin(a)).toFloat()
                )
            }
        }
        Ic.VOLUME -> {
            poly(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f, close = true, fill = true)
            arc(13f, 12f, 5f, -45f, 90f); arc(13f, 12f, 9f, -45f, 90f)
        }
        Ic.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.UP -> poly(6f, 15f, 12f, 9f, 18f, 15f)
        Ic.DOWN -> poly(6f, 9f, 12f, 15f, 18f, 9f)
        Ic.PLAY -> poly(8f, 5f, 19f, 12f, 8f, 19f, close = true, fill = true)
        Ic.PAUSE -> {
            drawRect(c, p(6f, 5f), Size(4f * u, 14f * u))
            drawRect(c, p(14f, 5f), Size(4f * u, 14f * u))
        }
        Ic.NEXT -> {
            poly(6f, 5f, 15f, 12f, 6f, 19f, close = true, fill = true)
            drawRect(c, p(17f, 5f), Size(2.5f * u, 14f * u))
        }
        Ic.PREV -> {
            poly(18f, 5f, 9f, 12f, 18f, 19f, close = true, fill = true)
            drawRect(c, p(4.5f, 5f), Size(2.5f * u, 14f * u))
        }
        Ic.SHIELD -> {
            poly(12f, 3f, 19f, 6f, 19f, 12f, 12f, 21f, 5f, 12f, 5f, 6f, close = true)
            poly(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f)
        }
    }
}
