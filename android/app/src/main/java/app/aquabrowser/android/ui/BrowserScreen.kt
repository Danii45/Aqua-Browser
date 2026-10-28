package app.aquabrowser.android.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog as ComposeDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.aquabrowser.android.Aqua
import app.aquabrowser.android.Dialog
import app.aquabrowser.android.Search
import app.aquabrowser.android.Tab
import app.aquabrowser.android.Ui
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView
import kotlin.math.max

@Composable
fun AquaRoot() {
    val s = Aqua.settings
    val b = Aqua.browser
    val profile = Aqua.profiles.current
    val colorKey = if (b.privateMode) "purple" else profile.color
    val (h, lh) = rememberHues(colorKey, s.autoCycle.value, s.autoSmooth.value)
    val c = AquaColors(h, lh, s.glass.value / 60f)
    val scheme = darkColorScheme(primary = c.lcdOn, onPrimary = c.desk, surface = c.panel, onSurface = c.ink, background = c.desk, onBackground = c.ink, secondary = c.lcdOn, outline = c.edge)

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalAqua provides c,
            LocalContentColor provides c.ink,
            LocalTextStyle provides TextStyle(fontFamily = AquaFonts.body, color = c.ink, fontSize = 16.sp),
        ) {
            val tab = b.current
            val full = tab?.fullscreen == true && !tab.home
            FullscreenEffect(full)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.desk)
                    .drawBehind {
                        drawRect(Brush.verticalGradient(listOf(c.desk2, c.desk), endY = size.height * .8f))
                        drawRect(Brush.radialGradient(listOf(c.deskGlow, Color.Transparent), center = Offset(size.width * .85f, -size.height * .05f), radius = max(size.width, size.height) * .7f))
                    }
            ) {
                Column(Modifier.fillMaxSize().then(if (full) Modifier else Modifier.safeDrawingPadding())) {
                    if (!full && !s.toolbarBottom.value) Toolbar(tab)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(if (full) Modifier else Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(20.dp)).border(BorderStroke(1.5.dp, c.edgeSoft), RoundedCornerShape(20.dp)))
                            .background(c.desk)
                    ) {
                        GeckoHost(tab)
                        if (tab == null || tab.home) HomeScreen(tab)
                    }
                    if (!full && s.toolbarBottom.value) Toolbar(tab)
                }

                Overlays()
            }
        }
    }
}

@Composable
private fun FullscreenEffect(full: Boolean) {
    val view = LocalView.current
    LaunchedEffect(full) {
        val w = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val ctl = WindowCompat.getInsetsController(w, view)
        if (full) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        } else ctl.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** The engine's view. It stays put; tabs swap their sessions into it. */
@Composable
private fun GeckoHost(tab: Tab?) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> GeckoView(ctx).apply { setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW) } },
        update = { v ->
            if (tab != null && !tab.home) {
                val s = Aqua.browser.session(tab)
                val attach = {
                    if (s.isOpen && v.session !== s) { v.releaseSession(); v.setSession(s) }
                }
                if (s.isOpen) attach() else v.post { attach() }
            }
        },
    )
}

/* ---------------------------- the toolbar ---------------------------- */

@Composable
private fun Toolbar(tab: Tab?) {
    val c = LocalAqua.current
    val b = Aqua.browser
    Column(Modifier.fillMaxWidth()) {
        if (tab != null && tab.loading && !tab.home) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp)) {
                Box(Modifier.fillMaxWidth(tab.progress.coerceIn(5, 100) / 100f).fillMaxHeight().clip(CircleShape).background(c.lcdOn))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ProfileDisc { Ui.profileMenu = true }
            AddressBar(tab, Modifier.weight(1f))
            TabCount(b.visibleTabs().size) { Ui.open("tabs") }
            GlassIcon(Icons.Filled.MoreVert, "Menu", size = 44.dp) { Ui.menu = true }
        }
    }
}

@Composable
private fun ProfileDisc(onClick: () -> Unit) {
    val c = LocalAqua.current
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(c.lcdHot.copy(alpha = .55f), c.buttonTop, c.buttonBot)))
            .border(BorderStroke(1.5.dp, c.buttonEdge), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // A little MiniDisc: the hub and the ring
        Box(Modifier.size(30.dp).clip(CircleShape).border(BorderStroke(1.dp, c.ink.copy(alpha = .5f)), CircleShape))
        Box(Modifier.size(9.dp).clip(CircleShape).background(c.desk).border(BorderStroke(1.dp, c.lcdOn), CircleShape))
    }
}

@Composable
private fun TabCount(n: Int, onClick: () -> Unit) {
    val c = LocalAqua.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(44.dp).clip(shape).background(Brush.verticalGradient(listOf(c.buttonTop, c.buttonBot))).border(BorderStroke(1.5.dp, c.buttonEdge), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).border(BorderStroke(1.8.dp, c.ink), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
            Text(if (n > 99) "∞" else n.toString(), fontFamily = AquaFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.ink)
        }
    }
}

@Composable
private fun AddressBar(tab: Tab?, modifier: Modifier) {
    val c = LocalAqua.current
    val s = Aqua.settings
    val b = Aqua.browser
    val label = when {
        b.privateMode -> "PRIVATE"
        else -> Aqua.profiles.current.name.uppercase()
    }
    val showing = tab != null && !tab.home
    Row(
        modifier.height(46.dp).lcd(c, RoundedCornerShape(15.dp)).clickable { Ui.startEditing(if (showing) tab!!.url else "") }.padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ind(label)
        Spacer(Modifier.width(10.dp))
        if (showing) {
            val t = tab!!
            Icon(if (t.secure) Icons.Filled.Lock else Icons.Filled.Warning, if (t.secure) "Secure" else "Not secure", tint = if (t.secure) c.lcdOn else c.warm, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            val text = t.host.ifBlank { t.url }
            if (s.addressDot.value) DotText(text, 18.sp, Modifier.weight(1f), weight = FontWeight.Bold)
            else Text(text, Modifier.weight(1f), color = c.lcdHot, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { if (t.loading) t.session?.stop() else t.session?.reload() }, contentAlignment = Alignment.Center) {
                Icon(if (t.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (t.loading) "Stop" else "Reload", tint = c.lcdOn, modifier = Modifier.size(19.dp))
            }
        } else {
            DotText("Search or type an address", 16.sp, Modifier.weight(1f), weight = FontWeight.Bold, color = c.lcdOn.copy(alpha = .45f), glow = false)
            Icon(Icons.Filled.Search, null, tint = c.lcdOn, modifier = Modifier.padding(end = 8.dp).size(19.dp))
        }
    }
}

/* ---------------------------- overlays ---------------------------- */

@Composable
private fun BoxScope.Overlays() {
    val c = LocalAqua.current
    val b = Aqua.browser
    val tab = b.current

    // The search overlay, the menu, the profile menu
    if (Ui.editing) { BackHandler { Ui.editing = false }; SearchOverlay(tab) }

    if (Ui.menu || Ui.profileMenu) {
        BackHandler { Ui.menu = false; Ui.profileMenu = false }
        Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null) { Ui.menu = false; Ui.profileMenu = false })
    }
    AnimatedVisibility(Ui.menu, Modifier.align(if (Aqua.settings.toolbarBottom.value) Alignment.BottomCenter else Alignment.TopCenter), enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
        MenuSheet(tab)
    }
    AnimatedVisibility(Ui.profileMenu, Modifier.align(if (Aqua.settings.toolbarBottom.value) Alignment.BottomStart else Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
        ProfileMenu()
    }

    when (Ui.screen) {
        "tabs" -> { BackHandler { Ui.open(null) }; TabsScreen() }
        "settings" -> { BackHandler { Ui.open(null) }; SettingsScreen() }
        "vault" -> { BackHandler { Ui.open(null) }; VaultScreen() }
    }

    if (!Aqua.settings.welcomed.value) Welcome()

    Ui.dialogs.lastOrNull()?.let { DialogView(it) }
    ToastView(Modifier.align(Alignment.BottomCenter))
}

@Composable
private fun MenuSheet(tab: Tab?) {
    val c = LocalAqua.current
    val b = Aqua.browser
    val page = tab != null && !tab.home
    fun go(f: () -> Unit) { Ui.menu = false; f() }
    Column(
        Modifier.fillMaxWidth().safeDrawingPadding().padding(10.dp).glass(c, RoundedCornerShape(26.dp), thick = true).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GlassIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.weight(1f), enabled = page && tab!!.canBack) { go { tab?.session?.goBack() } }
            GlassIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", Modifier.weight(1f), enabled = page && tab!!.canForward) { go { tab?.session?.goForward() } }
            GlassIcon(Icons.Filled.Refresh, "Reload", Modifier.weight(1f), enabled = page) { go { tab?.session?.reload() } }
            GlassIcon(Icons.Filled.Home, "Home page", Modifier.weight(1f), lit = tab?.home == true) { go { tab?.let { b.goHome(it) } } }
            GlassIcon(Icons.Filled.Share, "Share", Modifier.weight(1f), enabled = page) { go { tab?.let { b.share(it.url) } } }
        }
        MenuRow("New tab", Icons.Filled.Add) { go { b.newTab(private = false) } }
        if (b.privateMode) MenuRow("Close private tabs", Icons.Filled.Close) { go { b.closeAllPrivate() } }
        else MenuRow("New private tab", Icons.Filled.Lock) { go { b.enterPrivate() } }
        if (page) {
            MenuRow("Add to home page", Icons.Filled.Star) { go { Aqua.home.addShortcut(tab!!.profileId, tab.label.take(30), tab.url); Ui.toast("Added to your home page") } }
            MenuRow(if (tab!!.desktop) "Mobile site" else "Desktop site", Icons.Filled.Check) { go { b.setDesktop(tab, !tab.desktop) } }
            MenuRow("Copy link", Icons.Filled.Share) { go { b.copy(tab.url) } }
        }
        MenuRow("Passwords and cards", Icons.Filled.Lock) { go { Ui.open("vault") } }
        MenuRow("Settings", Icons.Filled.Settings) { go { Ui.open("settings") } }
    }
}

@Composable
private fun MenuRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAqua.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.lcdOn, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = c.ink, fontSize = 17.sp)
    }
}

@Composable
private fun ProfileMenu() {
    val c = LocalAqua.current
    val b = Aqua.browser
    Column(
        Modifier.safeDrawingPadding().padding(10.dp).width(260.dp).glass(c, RoundedCornerShape(24.dp), thick = true).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Label("Profiles", Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp))
        Aqua.profiles.list.forEach { p ->
            val on = !b.privateMode && p.id == Aqua.profiles.current.id
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Ui.profileMenu = false; b.switchProfile(p.id) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pc = Palette.byKey(if (p.color == "auto") "blue" else p.color)
                Box(Modifier.size(18.dp).clip(CircleShape).background(Brush.radialGradient(listOf(hsl(pc.h, 1f, .85f), hsl(pc.h, .85f, .45f)))))
                Spacer(Modifier.width(12.dp))
                Text(p.name, color = if (on) c.lcdHot else c.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (on) Ind("ON")
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Ui.profileMenu = false; b.enterPrivate() }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, null, tint = hsl(266f, 1f, .78f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text("Private", color = if (b.privateMode) c.lcdHot else c.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (b.privateMode) Ind("ON")
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Ui.open("settings") }.padding(8.dp)) {
            Text("Manage profiles…", color = c.inkDim, fontSize = 15.sp)
        }
    }
}

/* ---------------------------- typing an address ---------------------------- */

@Composable
private fun SearchOverlay(tab: Tab?) {
    val c = LocalAqua.current
    val b = Aqua.browser
    var value by remember { mutableStateOf(TextFieldValue(Ui.editText, TextRange(0, Ui.editText.length))) }
    val focus = remember { FocusRequester() }
    val remote = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { delay(60); runCatching { focus.requestFocus() } }
    LaunchedEffect(value.text) {
        remote.clear()
        if (value.text.isNotBlank() && !b.privateMode) { delay(180); remote.addAll(Search.suggest(value.text)) }
    }
    val local = if (b.privateMode) emptyList() else Aqua.history.search(b.profileId, value.text)

    fun go(text: String) {
        Ui.editing = false
        if (text.isBlank()) return
        val t = b.current ?: b.newTab()
        b.load(t, text)
    }

    Column(Modifier.fillMaxSize().background(c.scrim).safeDrawingPadding().imePadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(54.dp).lcd(c, RoundedCornerShape(16.dp), lit = true).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ind(Search.engine().name)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value, onValueChange = { value = it }, singleLine = true,
                textStyle = TextStyle(color = c.lcdHot, fontSize = 18.sp, fontFamily = AquaFonts.body),
                cursorBrush = SolidColor(c.lcdOn),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { go(value.text) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (value.text.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable { value = TextFieldValue("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Clear, "Clear", tint = c.lcdOn, modifier = Modifier.size(18.dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).glass(c, RoundedCornerShape(22.dp), thick = true), contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            items(local) { v ->
                SuggestRow(v.title.ifBlank { v.url }, v.url, Icons.Filled.Star) { go(v.url) }
            }
            items(remote.filter { r -> local.none { it.title.equals(r, true) } }) { q ->
                SuggestRow(q, null, Icons.Filled.Search) { go(q) }
            }
            if (value.text.isNotBlank()) item { SuggestRow("Search for “${value.text}”", Search.engine().name, Icons.Filled.Search) { go(value.text) } }
            if (value.text.isBlank() && tab != null && !tab.home) item { SuggestRow("Copy this page's link", tab.url, Icons.Filled.Share) { b.copy(tab.url); Ui.editing = false } }
        }
    }
}

@Composable
private fun SuggestRow(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalAqua.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.lcdOn, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.lcdOn.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------------------------- dialogs and toasts ---------------------------- */

@Composable
fun DialogView(d: Dialog) {
    val c = LocalAqua.current
    fun done() = Ui.close(d)
    val dismiss: () -> Unit = {
        done()
        when (d) {
            is Dialog.Message -> d.onResult(false)
            is Dialog.Input -> d.onResult(null)
            is Dialog.SignIn -> d.onResult(null)
            is Dialog.Pick -> d.onResult(null)
            is Dialog.Form -> d.onResult(null)
        }
    }
    ComposeDialog(onDismissRequest = dismiss) {
        CompositionLocalProvider(LocalAqua provides c, LocalContentColor provides c.ink, LocalTextStyle provides TextStyle(fontFamily = AquaFonts.body, color = c.ink, fontSize = 16.sp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).glass(c, RoundedCornerShape(26.dp), thick = true).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (d) {
                    is Dialog.Message -> {
                        Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                        if (d.message.isNotBlank()) Body(d.message, Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), dim = true)
                        DialogButtons(d.ok, d.cancel, { done(); d.onResult(true) }, { done(); d.onResult(false) })
                    }
                    is Dialog.Input -> {
                        var text by remember { mutableStateOf(d.initial) }
                        Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (!d.message.isNullOrBlank()) Body(d.message, dim = true)
                        LcdField(text, { text = it }, password = d.password, autoFocus = true)
                        DialogButtons(d.ok, "Cancel", { done(); d.onResult(text) }, { done(); d.onResult(null) })
                    }
                    is Dialog.SignIn -> {
                        var user by remember { mutableStateOf("") }
                        var pass by remember { mutableStateOf("") }
                        Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (!d.message.isNullOrBlank()) Body(d.message, dim = true)
                        LcdField(user, { user = it }, placeholder = "User name", autoFocus = true)
                        LcdField(pass, { pass = it }, placeholder = "Password", password = true)
                        DialogButtons("Sign in", "Cancel", { done(); d.onResult(user to pass) }, { done(); d.onResult(null) })
                    }
                    is Dialog.Pick -> {
                        val chosen = remember { mutableStateListOf<Int>().apply { addAll(d.selected) } }
                        if (d.title != null) Text(d.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            items(d.items.size) { i ->
                                val on = i in chosen
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                        .background(if (on) c.lcdOn.copy(alpha = .14f) else Color.Transparent)
                                        .clickable {
                                            if (d.multiple) { if (on) chosen.remove(i) else chosen.add(i) } else { done(); d.onResult(listOf(i)) }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.items[i], fontSize = 16.sp, color = if (on) c.lcdHot else c.ink)
                                        d.subtitles?.getOrNull(i)?.let { Text(it, fontSize = 13.sp, color = c.inkDim) }
                                    }
                                    if (on) Icon(Icons.Filled.Check, null, tint = c.lcdOn, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (d.multiple) DialogButtons("Done", "Cancel", { done(); d.onResult(chosen.sorted()) }, { done(); d.onResult(null) })
                    }
                    is Dialog.Form -> {
                        val values = remember { mutableStateListOf<String>().apply { addAll(d.fields.map { it.value }) } }
                        Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            d.fields.forEachIndexed { i, f ->
                                Label(f.label)
                                LcdField(values[i], { values[i] = it }, password = f.password, number = f.number, autoFocus = i == 0)
                            }
                        }
                        DialogButtons(d.ok, "Cancel", { done(); d.onResult(values.toList()) }, { done(); d.onResult(null) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButtons(ok: String, cancel: String?, onOk: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
        if (cancel != null) GlassButton(cancel, ghost = true, onClick = onCancel)
        GlassButton(ok, primary = true, onClick = onOk)
    }
}

@Composable
fun LcdField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", password: Boolean = false, number: Boolean = false, autoFocus: Boolean = false, singleLine: Boolean = true, onDone: (() -> Unit)? = null) {
    val c = LocalAqua.current
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() } }
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).lcd(c, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = c.inkFaint, fontSize = 16.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = TextStyle(color = c.lcdHot, fontSize = 16.sp, fontFamily = AquaFonts.body),
            cursorBrush = SolidColor(c.lcdOn),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when { password -> KeyboardType.Password; number -> KeyboardType.Number; else -> KeyboardType.Text },
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

@Composable
private fun ToastView(modifier: Modifier) {
    val c = LocalAqua.current
    val t = Ui.toast
    LaunchedEffect(t?.stamp) { if (t != null) { delay(3800); if (Ui.toast === t) Ui.toast = null } }
    AnimatedVisibility(t != null, modifier.navigationBarsPadding().padding(bottom = 76.dp), enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
        val shown = t ?: return@AnimatedVisibility
        Row(Modifier.padding(horizontal = 16.dp).glass(c, RoundedCornerShape(18.dp), thick = true).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shown.message, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
            if (shown.action != null) {
                Spacer(Modifier.width(14.dp))
                Text(shown.action.uppercase(), color = c.lcdOn, fontFamily = AquaFonts.label, fontSize = 10.sp, modifier = Modifier.clickable { Ui.toast = null; shown.onAction?.invoke() }.padding(6.dp))
            }
        }
    }
}

/* ---------------------------- first run ---------------------------- */

@Composable
private fun Welcome() {
    val c = LocalAqua.current
    val p = Aqua.profiles.current
    Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp).glass(c, RoundedCornerShape(28.dp), thick = true).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ind("Aqua"); Ind("BR-02"); Ind("Android", on = false) }
                DotText("Welcome", 44.sp)
                DotText("${p.name} profile", 16.sp, weight = FontWeight.Bold, glow = false, color = c.lcdOn.copy(alpha = .7f))
            }
            Body("Aqua Browser keeps Home and Work apart (tap the disc to switch), has a home page you can arrange, saves your passwords and cards encrypted on this phone, and blocks ads.", dim = true)
            Label("Pick a colour")
            ColourPicker(p.color) { Aqua.profiles.setColor(p.id, it) }
            Label("Toolbar")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("At the bottom", Aqua.settings.toolbarBottom.value) { Aqua.settings.toolbarBottom.value = true }
                Chip("At the top", !Aqua.settings.toolbarBottom.value) { Aqua.settings.toolbarBottom.value = false }
            }
            GlassButton("Start browsing", Modifier.fillMaxWidth(), primary = true) { Aqua.settings.welcomed.value = true }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColourPicker(value: String, withAuto: Boolean = true, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palette.all.forEach { col -> Swatch(col.h, value == col.key, col.name) { onPick(col.key) } }
        if (withAuto) Swatch(null, value == "auto", "Auto: cycles through all seven") { onPick("auto") }
    }
}
