package com.mux.center

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF03070B)
val Mono = FontFamily.Monospace
val cPanel = Color(0xFF050D12)
val cText = Color(0xFFD8F3F7)
val cWarn = Color(0xFFFFB300)
val cBad = Color(0xFFFF3D3D)
val cGood = Color(0xFF00FF66)

class AccentTheme(val name: String, val main: Color, val dim: Color)

val ACCENTS = listOf(
    AccentTheme("CYAN", Color(0xFF00E5FF), Color(0xFF0A5C68)),
    AccentTheme("GOLD", Color(0xFFFFB300), Color(0xFF6B4A00)),
    AccentTheme("GREEN", Color(0xFF00FF66), Color(0xFF05703A)),
    AccentTheme("RED", Color(0xFFFF3D3D), Color(0xFF6B1414))
)
private val theme: AccentTheme get() = ACCENTS[Prefs.accent.coerceIn(0, ACCENTS.lastIndex)]
val cMain: Color get() = theme.main
val cDim: Color get() = theme.dim
val cSub: Color get() = theme.main.copy(alpha = 0.6f)

@Composable
fun T(
    text: String, size: Float = 14f, color: Color = cMain, bold: Boolean = false,
    mod: Modifier = Modifier, lines: Int = 1, align: TextAlign = TextAlign.Start
) = Text(
    text, modifier = mod, color = color, fontFamily = Mono, fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = align
)

/** Dark background with the HUD grid. */
@Composable
fun HudBackground(content: @Composable BoxScope.() -> Unit) {
    val line = cDim.copy(alpha = 0.22f)
    Box(
        Modifier.fillMaxSize().background(Bg).drawBehind {
            val step = 36.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y)); y += step }
        },
        content = content
    )
}

/** Chamfered corners (top left and bottom right), like the HUD keyboard keys. */
class HudShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val p = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(p)
    }
}

@Composable
fun Chip(label: String, on: Boolean, mod: Modifier = Modifier, onClick: () -> Unit) {
    val shape = HudShape(7.dp)
    Box(
        mod.background(if (on) cDim.copy(alpha = 0.55f) else cPanel.copy(alpha = 0.7f), shape)
            .border(1.dp, if (on) cMain else cDim, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        Alignment.Center
    ) { T(label, 11f, if (on) cMain else cSub, lines = 2, align = TextAlign.Center) }
}

/** The amber main action key. */
@Composable
fun HudKey(label: String, mod: Modifier = Modifier, onClick: () -> Unit) {
    val shape = HudShape(7.dp)
    Box(
        mod.background(cWarn.copy(alpha = 0.12f), shape)
            .border(1.dp, cWarn, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        Alignment.Center
    ) { T(label, 11f, cWarn, true, lines = 2, align = TextAlign.Center) }
}

@Composable
fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = HudShape(10.dp)
    Column(
        modifier.fillMaxWidth()
            .background(cPanel.copy(alpha = 0.88f), shape)
            .border(1.dp, cDim, shape)
            .padding(12.dp)
    ) {
        T("// $title", 12f, cSub, true, Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
fun KV(k: String, v: String, color: Color = cText) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        T(k, 11f, cSub, mod = Modifier.weight(0.42f), lines = 2)
        T(v, 12f, color, mod = Modifier.weight(0.58f), lines = 3, align = TextAlign.End)
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun TwoCol(a: @Composable ColumnScope.() -> Unit, b: @Composable ColumnScope.() -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = a)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = b)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { a(); b() }
    }
}

@Composable
fun Bar(frac: Float, color: Color = cMain, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).border(1.dp, cDim)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(color.copy(alpha = 0.85f)))
    }
}
