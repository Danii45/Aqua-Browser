package app.aquabrowser.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.aquabrowser.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Aqua MD's seven colours. h tints the glass, lh tints the LCD display. */
data class AquaColor(val key: String, val name: String, val h: Float, val lh: Float)

object Palette {
    val all = listOf(
        AquaColor("blue", "Blue", 214f, 192f),
        AquaColor("teal", "Teal", 178f, 168f),
        AquaColor("green", "Green", 140f, 128f),
        AquaColor("orange", "Orange", 26f, 36f),
        AquaColor("red", "Red", 354f, 2f),
        AquaColor("pink", "Pink", 322f, 316f),
        AquaColor("purple", "Purple", 266f, 258f),
    )
    fun byKey(k: String): AquaColor = all.firstOrNull { it.key == k } ?: all[0]
}

fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f), a.coerceIn(0f, 1f))

/** Every colour in the app, worked out from the two hues, the same formulas as the desktop CSS. */
class AquaColors(val h: Float, val lh: Float, val ga: Float) {
    val desk = hsl(h + 7, .53f, .06f)
    val desk2 = hsl(h + 9, .62f, .15f)
    val deskGlow = hsl(h, .8f, .5f, .24f)
    val ink = hsl(h, 1f, .95f)
    val inkDim = hsl(h + 4, .36f, .72f)
    val inkFaint = hsl(h + 6, .28f, .54f)
    val edge = hsl(h - 5, 1f, .79f, .42f)
    val edgeSoft = hsl(h - 5, 1f, .79f, .18f)
    val lcd = hsl(h - 2, .82f, .07f)
    val lcdTop = hsl(h - 2, .79f, .12f)
    val lcdEdge = hsl(h - 4, 1f, .79f, .34f)
    val lcdOn = hsl(lh, 1f, .78f)
    val lcdHot = hsl(lh - 3, 1f, .96f)
    val lcdGlow = hsl(lh, 1f, .72f, .55f)
    val lcdOff = hsl(lh, 1f, .78f, .16f)
    val glassTop = hsl(h - 3, 1f, .74f, .16f * ga)
    val glassMid = hsl(h + 4, .86f, .57f, .08f * ga)
    val glassBot = hsl(h + 7, .78f, .44f, .14f * ga)
    val buttonTop = hsl(h - 5, 1f, .77f, .24f * ga)
    val buttonBot = hsl(h + 4, .86f, .57f, .13f * ga)
    val buttonEdge = hsl(h - 6, 1f, .83f, .42f)
    val panel = hsl(h + 7, .58f, .085f, .94f)
    val scrim = hsl(h + 9, 1f, .03f, .62f)
    val warn = Color(0xFFFFB8A6)
    val warm = Color(0xFFFFCF7A)
}

val LocalAqua = compositionLocalOf { AquaColors(214f, 192f, 1f) }

object AquaFonts {
    val dot = FontFamily(Font(R.font.doto_black, FontWeight.Black), Font(R.font.doto_bold, FontWeight.Bold))
    val label = FontFamily(Font(R.font.michroma, FontWeight.Normal))
    val body = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )
}

private fun shortest(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

/**
 * The current hues, faded like Aqua MD. "auto" walks through all seven colours, holding each for
 * [cycleSeconds] (or drifting the whole time when [smooth]).
 */
@Composable
fun rememberHues(colorKey: String, cycleSeconds: Int, smooth: Boolean): Pair<Float, Float> {
    var index by remember { mutableIntStateOf(0) }
    val auto = colorKey == "auto"
    if (auto) {
        LaunchedEffect(cycleSeconds) {
            while (true) {
                delay(cycleSeconds.coerceAtLeast(5) * 1000L)
                index = (index + 1) % Palette.all.size
            }
        }
    }
    val target = if (auto) Palette.all[index % Palette.all.size] else Palette.byKey(colorKey)
    val h = remember { Animatable(target.h) }
    val lh = remember { Animatable(target.lh) }
    LaunchedEffect(target, smooth) {
        val ms = if (auto && smooth) cycleSeconds.coerceAtLeast(5) * 1000 else 1400
        val easing = if (auto && smooth) LinearEasing else FastOutSlowInEasing
        launch { h.animateTo(h.value + shortest(h.value, target.h), tween(ms, easing = easing)) }
        launch { lh.animateTo(lh.value + shortest(lh.value, target.lh), tween(ms, easing = easing)) }
    }
    return h.value to lh.value
}
