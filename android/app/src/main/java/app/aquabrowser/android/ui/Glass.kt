package app.aquabrowser.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.max

/** Tinted glass: the panels, cards and bars. */
fun Modifier.glass(c: AquaColors, shape: Shape = RoundedCornerShape(22.dp), thick: Boolean = false, edge: Color = c.edge): Modifier =
    this
        .clip(shape)
        .background(if (thick) c.panel else Color.Transparent)
        .background(Brush.linearGradient(listOf(c.glassTop, c.glassMid, c.glassBot)))
        .border(BorderStroke(1.5.dp, edge), shape)
        .drawWithContent {
            drawContent()
            val inset = 18.dp.toPx()
            drawLine(Color.White.copy(alpha = .28f), Offset(inset, 1.6.dp.toPx()), Offset(size.width - inset, 1.6.dp.toPx()), strokeWidth = 1.dp.toPx())
        }

/** Aqua MD's display: a dark LCD with a faint dot grid. */
fun Modifier.lcd(c: AquaColors, shape: Shape = RoundedCornerShape(16.dp), lit: Boolean = false): Modifier =
    this
        .clip(shape)
        .drawBehind {
            drawRect(Brush.radialGradient(listOf(c.lcdTop, c.lcd), center = Offset(size.width * .3f, 0f), radius = max(size.width, size.height) * 1.1f))
            val step = 5.dp.toPx()
            val r = .6.dp.toPx()
            val dot = c.lcdOn.copy(alpha = .06f)
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) { drawCircle(dot, r, Offset(x, y)); x += step }
                y += step
            }
        }
        .border(BorderStroke(1.5.dp, if (lit) c.lcdOn else c.lcdEdge), shape)

@Composable
fun Ind(text: String, on: Boolean = true, color: Color? = null, size: TextUnit = 9.sp) {
    val c = LocalAqua.current
    Text(
        text.uppercase(),
        fontFamily = AquaFonts.label, fontSize = size, letterSpacing = .18.em, maxLines = 1,
        color = color ?: if (on) c.lcdOn else c.lcdOff,
        style = TextStyle(shadow = if (on) Shadow(c.lcdGlow, Offset.Zero, 14f) else null),
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    val c = LocalAqua.current
    Text(text.uppercase(), modifier = modifier, fontFamily = AquaFonts.label, fontSize = 10.sp, letterSpacing = .22.em, color = c.inkDim, maxLines = 1)
}

@Composable
fun DotText(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Black, color: Color? = null, glow: Boolean = true, maxLines: Int = 1) {
    val c = LocalAqua.current
    Text(
        text, modifier = modifier, fontFamily = AquaFonts.dot, fontWeight = weight, fontSize = size, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, color = color ?: c.lcdOn,
        style = TextStyle(shadow = if (glow) Shadow(c.lcdGlow, Offset.Zero, 22f) else null),
    )
}

/** A glass transport button with an icon. */
@Composable
fun GlassIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, size: Dp = 42.dp, lit: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalAqua.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.buttonTop, c.buttonBot)))
            .border(BorderStroke(1.5.dp, if (lit) c.lcdOn else c.buttonEdge), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (!enabled) c.inkFaint else if (lit) c.lcdHot else c.ink, modifier = Modifier.size(size * .5f))
    }
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = false, ghost: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalAqua.current
    val shape = RoundedCornerShape(14.dp)
    val edge = when { danger -> c.warn.copy(alpha = .6f); primary -> c.lcdOn; ghost -> c.edgeSoft; else -> c.buttonEdge }
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(if (ghost) Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)) else Brush.verticalGradient(listOf(c.buttonTop, c.buttonBot)))
            .border(BorderStroke(1.5.dp, edge), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val tint = when { danger -> c.warn; primary -> c.lcdHot; else -> c.ink }
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, color = tint, fontFamily = AquaFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAqua.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.glassTop, c.glassMid)))
            .border(BorderStroke(1.5.dp, if (selected) c.lcdOn.copy(alpha = .75f) else c.edge.copy(alpha = .3f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(), fontFamily = AquaFonts.label, fontSize = 9.sp, letterSpacing = .15.em, maxLines = 1,
            color = if (selected) c.lcdHot else c.inkDim,
            style = TextStyle(shadow = if (selected) Shadow(c.lcdGlow, Offset.Zero, 12f) else null),
        )
    }
}

/** A colour bead. hue = null draws the rainbow "Auto" bead. */
@Composable
fun Swatch(hue: Float?, selected: Boolean, description: String, size: Dp = 40.dp, onClick: () -> Unit) {
    val c = LocalAqua.current
    val fill = if (hue == null)
        Brush.sweepGradient(Palette.all.map { hsl(it.h, .9f, .6f) } + hsl(Palette.all[0].h, .9f, .6f))
    else
        Brush.radialGradient(listOf(hsl(hue, 1f, .88f), hsl(hue, .9f, .58f), hsl(hue, .85f, .38f)))
    Box(
        Modifier
            .size(size)
            .then(if (selected) Modifier.border(BorderStroke(2.5.dp, hue?.let { hsl(it, 1f, .75f) } ?: c.lcdOn), CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .border(BorderStroke(1.5.dp, Color.White.copy(alpha = .6f)), CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
    ) {
        Box(Modifier.padding(start = size * .2f, top = size * .14f).size(size * .32f, size * .2f).clip(CircleShape).background(Color.White.copy(alpha = .55f)))
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    val c = LocalAqua.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontFamily = AquaFonts.body, fontSize = 16.sp)
            if (note != null) Text(note, color = c.inkFaint, fontFamily = AquaFonts.body, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.lcdHot, checkedTrackColor = c.lcdOn.copy(alpha = .45f), checkedBorderColor = c.lcdOn,
                uncheckedThumbColor = c.inkDim, uncheckedTrackColor = c.lcd, uncheckedBorderColor = c.edge,
            ),
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val c = LocalAqua.current
    Column(modifier.fillMaxWidth().glass(c, thick = true).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) Text(title.uppercase(), fontFamily = AquaFonts.label, fontSize = 12.sp, letterSpacing = .24.em, color = c.ink)
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))

@Composable
fun Body(text: String, modifier: Modifier = Modifier, dim: Boolean = false, size: TextUnit = 15.sp, maxLines: Int = Int.MAX_VALUE) {
    val c = LocalAqua.current
    Text(text, modifier = modifier, color = if (dim) c.inkDim else c.ink, fontFamily = AquaFonts.body, fontSize = size, maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = size * 1.35f)
}
