package com.mux.center

import android.content.Intent
import android.media.AudioManager
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Slides the active panel down from the top. */
@Composable
fun PanelHost(close: () -> Unit) {
    val progress by animateFloatAsState(if (UiState.open) 1f else 0f, tween(220), label = "panel")
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            translationY = -size.height * (1f - progress)
            alpha = progress.coerceIn(0f, 1f)
        }
    ) {
        if (UiState.panel == 1) NotificationPanel(close) else ControlPanel(close)
    }
}

@Composable
private fun PanelFrame(
    title: String, icon: Ic, switchLabel: String, onSwitch: () -> Unit, close: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val sh = if (UiState.statusPx > 0) with(density) { UiState.statusPx.toDp() } else 26.dp
    val closePx = with(density) { 90.dp.toPx() }
    val closeNow by rememberUpdatedState(close)
    val acc = remember { floatArrayOf(0f) }

    // Swipe up anywhere closes the panel. Scrollable content (the lists) reports what it could not
    // scroll any more through nested scroll, so a swipe up at the end of a list closes it too.
    val connection = remember(closePx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    acc[0] += available.y
                    if (acc[0] < -closePx) {
                        acc[0] = 0f
                        closeNow()
                    }
                } else if (available.y > 0f) {
                    acc[0] = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                acc[0] = 0f
                return Velocity.Zero
            }
        }
    }

    HudBackground {
        Column(
            Modifier.fillMaxSize()
                .nestedScroll(connection)
                .pointerInput(closePx) {
                    detectVerticalDragGestures(
                        onDragStart = { acc[0] = 0f },
                        onDragEnd = { if (acc[0] < -closePx * 0.6f) closeNow(); acc[0] = 0f },
                        onDragCancel = { acc[0] = 0f },
                        onVerticalDrag = { _, dy -> acc[0] += dy }
                    )
                }
        ) {
            HudStatusBar(Modifier.height(sh), 2)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIcon(icon, 24.dp, cMain)
                T(title, 16f, cMain, true, Modifier.padding(start = 10.dp).weight(1f))
                Chip(switchLabel, false, Modifier.width(120.dp)) { onSwitch() }
                Box(Modifier.padding(start = 10.dp).clickable {
                    try { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                    close()
                }) { HudIcon(Ic.GEAR, 26.dp, cMain) }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().height(40.dp).clickable { close() },
                Alignment.Center
            ) { Box(Modifier.width(120.dp).height(3.dp).background(cMain)) }
        }
    }
}

// ---------------------------------------------------------------------- notification center

@Composable
fun NotificationPanel(close: () -> Unit) {
    val all = Notifs.items
    var open by remember { mutableStateOf(setOf(0, 1)) }
    PanelFrame("NOTIFICATION CENTER", Ic.BELL, "CONTROLS", { UiState.panel = 2 }, close) {
        if (!Notifs.connected) {
            Box(Modifier.fillMaxWidth().padding(14.dp)) {
                Panel("NOTIFICATION ACCESS IS OFF") {
                    T("Open MUX Center, go to SETUP and turn on Notification access.", 11f, cSub, lines = 3)
                }
            }
        } else if (all.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                HudIcon(Ic.SHIELD, 56.dp, cGood)
                T("NO NOTIFICATIONS", 13f, cSub, true, Modifier.padding(top = 12.dp))
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (g in 0..2) {
                val list = all.filter { it.group == g }
                if (list.isEmpty()) continue
                val expanded = g in open
                item(key = "h$g") {
                    SectionHeader(
                        when (g) { 0 -> "CONVERSATIONS"; 1 -> "NOTIFICATIONS"; else -> "SILENT" },
                        list.size, expanded
                    ) { open = if (expanded) open - g else open + g }
                }
                if (expanded) items(list, key = { it.key }) { n -> NotifCard(n, close) }
            }
        }
        if (all.any { it.clearable }) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                HudKey("CLEAR ALL", Modifier.width(170.dp)) { Notifs.dismissAll() }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val shape = HudShape(8.dp)
    Row(
        Modifier.fillMaxWidth().background(cPanel.copy(alpha = 0.8f), shape).border(1.dp, cMain.copy(alpha = 0.6f), shape)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(title, 12f, cMain, true, Modifier.weight(1f))
        T("$count", 12f, cMain, true)
        HudIcon(if (expanded) Ic.UP else Ic.DOWN, 18.dp, cMain)
    }
}

@Composable
private fun NotifCard(n: NotifItem, close: () -> Unit) {
    var exp by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    val shape = HudShape(10.dp)
    Column(
        Modifier.fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(dragX) / width).coerceIn(0f, 1f) * 0.85f }
            .pointerInput(n.key, n.clearable) {
                if (n.clearable) {
                    // swipe left or right to remove the notification
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(dragX) > width * 0.33f) {
                                    val target = if (dragX > 0f) width.toFloat() else -width.toFloat()
                                    animate(dragX, target, animationSpec = tween(160)) { v, _ -> dragX = v }
                                    Notifs.dismiss(n.key)
                                    delay(800)
                                    // still here: Android did not let us remove it, slide it back
                                    animate(dragX, 0f, animationSpec = tween(200)) { v, _ -> dragX = v }
                                } else {
                                    animate(dragX, 0f, animationSpec = tween(160)) { v, _ -> dragX = v }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { animate(dragX, 0f, animationSpec = tween(160)) { v, _ -> dragX = v } } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            dragX += dx
                        }
                    )
                }
            }
            .background(cPanel, shape).border(1.dp, cDim, shape)
            .clickable {
                try { n.intent?.send() } catch (e: Exception) { }
                close()
            }.padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            n.icon?.let { Image(it, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(cMain)) }
            T(n.app, 11f, cSub, mod = Modifier.padding(start = 6.dp).weight(1f))
            T(ago(n.time), 10f, cSub)
            Box(Modifier.padding(start = 8.dp).clickable { exp = !exp }) { HudIcon(if (exp) Ic.UP else Ic.DOWN, 18.dp, cSub) }
            if (n.clearable) Box(Modifier.padding(start = 6.dp).clickable { Notifs.dismiss(n.key) }) { HudIcon(Ic.CLOSE, 18.dp, cSub) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            if (n.group == 0) {
                Box(
                    Modifier.padding(end = 10.dp).size(40.dp).clip(CircleShape).border(1.dp, cMain, CircleShape),
                    Alignment.Center
                ) {
                    val a = n.avatar
                    if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else T((n.title.firstOrNull() ?: '?').toString().uppercase(), 16f, cMain, true)
                }
            }
            Column(Modifier.weight(1f)) {
                if (n.title.isNotBlank()) T(n.title, 14f, cText, true, lines = if (exp) 3 else 1)
                val body = if (exp) n.bigText else n.text
                if (body.isNotBlank()) T(body, 12f, cSub, lines = if (exp) 10 else 2)
            }
        }
        if (n.progress >= 0) Bar(n.progress / 100f, cMain, Modifier.padding(top = 8.dp))
        if (n.actions.isNotEmpty() && exp) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in n.actions.take(3)) {
                    Chip(a.title, false, Modifier.weight(1f)) {
                        try { a.intent.send() } catch (e: Exception) { }
                        close()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- control center

@Composable
fun ControlPanel(close: () -> Unit) {
    val ctx = LocalContext.current
    val tick = Sys.tick   // read so the tiles refresh every second
    PanelFrame("CONTROL CENTER", Ic.GEAR, "NOTIFICATIONS", { UiState.panel = 1 }, close) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val tiles = Controls.visible()
            if (tick >= 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WideTile(tiles[0], Modifier.weight(1f))
                    WideTile(tiles[1], Modifier.weight(1f))
                }
            }
            if (Media.active) MediaCard()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrightnessCard(Modifier.weight(1f))
                VolumeCard(Modifier.weight(1f))
            }
            for (row in tiles.drop(2).chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in row) SmallTile(t, Modifier.weight(1f))
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Shortcuts()
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun tileFill(on: Boolean): Color = if (on) lerp(Bg, cMain, 0.22f) else cPanel

@Composable
private fun WideTile(t: Tile, modifier: Modifier) {
    val ctx = LocalContext.current
    val on = t.on(ctx)
    val shape = HudShape(12.dp)
    val fill = tileFill(on)
    Row(
        modifier.height(76.dp).background(fill, shape).border(if (on) 1.5.dp else 1.dp, if (on) cMain else cDim, shape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { t.click(ctx) }, onLongPress = { t.long?.invoke(ctx) ?: t.click(ctx) })
            }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudIcon(t.icon, 34.dp, if (on) cMain else cText, fill)
        Column(Modifier.padding(start = 12.dp)) {
            T(t.label, 13f, cText, true)
            T(t.sub(ctx), 11f, if (on) cMain else cSub)
        }
    }
}

@Composable
private fun SmallTile(t: Tile, modifier: Modifier) {
    val ctx = LocalContext.current
    val on = t.on(ctx)
    val shape = HudShape(10.dp)
    val fill = tileFill(on)
    Column(
        modifier.height(86.dp).background(fill, shape).border(if (on) 1.5.dp else 1.dp, if (on) cMain else cDim, shape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { t.click(ctx) }, onLongPress = { t.long?.invoke(ctx) ?: t.click(ctx) })
            }.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        HudIcon(t.icon, 28.dp, if (on) cMain else cText, fill)
        T(t.label, 9f, if (on) cMain else cText, lines = 2, align = TextAlign.Center, mod = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MediaCard() {
    val shape = HudShape(12.dp)
    Row(
        Modifier.fillMaxWidth().background(cPanel, shape).border(1.dp, cDim, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = Media.art
        Box(Modifier.size(64.dp).border(1.dp, cDim), Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else HudIcon(Ic.VOLUME, 28.dp, cSub)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            T(Media.title.ifBlank { "Playing" }, 14f, cText, true)
            T(Media.artist, 11f, cSub)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { Media.prev() }) { HudIcon(Ic.PREV, 26.dp, cText) }
                Box(Modifier.clickable { Media.toggle() }) { HudIcon(if (Media.playing) Ic.PAUSE else Ic.PLAY, 30.dp, cMain) }
                Box(Modifier.clickable { Media.next() }) { HudIcon(Ic.NEXT, 26.dp, cText) }
            }
        }
    }
}

@Composable
fun VSlider(value: Float, enabled: Boolean, onChange: (Float) -> Unit, onBlocked: () -> Unit, modifier: Modifier = Modifier) {
    val main = cMain
    val dim = cDim
    Canvas(
        modifier.pointerInput(enabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!enabled) {
                    onBlocked()
                    return@awaitEachGesture
                }
                fun f(y: Float) = (1f - y / size.height).coerceIn(0f, 1f)
                onChange(f(down.position.y))
                down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    onChange(f(ch.position.y))
                    ch.consume()
                }
            }
        }
    ) {
        val r = CornerRadius(size.width / 2f)
        drawRoundRect(dim.copy(alpha = 0.35f), Offset.Zero, size, r)
        val h = size.height * value.coerceIn(0f, 1f)
        if (h > 0f) drawRoundRect(main, Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h), r)
        drawRoundRect(main.copy(alpha = 0.8f), Offset.Zero, size, r, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun SliderCard(icon: Ic, label: String, modifier: Modifier, slider: @Composable (Modifier) -> Unit, extra: @Composable () -> Unit = {}) {
    val shape = HudShape(12.dp)
    Row(
        modifier.height(190.dp).background(cPanel, shape).border(1.dp, cDim, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            HudIcon(icon, 34.dp, cMain)
            T(label, 13f, cText, true)
            extra()
        }
        slider(Modifier.width(54.dp).fillMaxSize())
    }
}

@Composable
private fun BrightnessCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(Controls.brightness(ctx)) }
    val canWrite = Access.writeSettings(ctx)
    val auto = Controls.autoBrightness(ctx)
    SliderCard(
        Ic.SUN, "${(v * 100).toInt()}%", modifier,
        { m ->
            VSlider(v, canWrite, { v = it; Controls.setBrightness(ctx, it) }, { Controls.setBrightness(ctx, v) }, m)
        },
        { Chip(if (auto) "AUTO ON" else "AUTO", auto, Modifier.width(70.dp)) { Controls.toggleAutoBrightness(ctx) } }
    )
}

@Composable
private fun VolumeCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var v by remember { mutableFloatStateOf(Controls.volume(ctx, AudioManager.STREAM_MUSIC)) }
    SliderCard(
        Ic.VOLUME, "${(v * 100).toInt()}%", modifier,
        { m -> VSlider(v, true, { v = it; Controls.setVolume(ctx, AudioManager.STREAM_MUSIC, it) }, {}, m) }
    )
}

@Composable
private fun Shortcuts() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val installed = Controls.shortcuts.filter { pm.getLaunchIntentForPackage(it.pkg) != null }
    if (installed.isEmpty()) return
    val shape = HudShape(12.dp)
    Column(Modifier.fillMaxWidth().background(cPanel, shape).border(1.dp, cDim, shape).padding(12.dp)) {
        T("// MUX APPS", 11f, cSub, true, Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (s in installed) {
                val icon = remember(s.pkg) {
                    try { pm.getApplicationIcon(s.pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
                }
                Column(Modifier.clickable { Controls.launch(ctx, s.pkg) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon != null) Image(icon, null, Modifier.size(44.dp))
                    T(s.label, 9f, cText, mod = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}