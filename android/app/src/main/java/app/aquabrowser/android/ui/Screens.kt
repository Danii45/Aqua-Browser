package app.aquabrowser.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aquabrowser.android.Aqua
import app.aquabrowser.android.Card as VaultCard
import app.aquabrowser.android.Dialog
import app.aquabrowser.android.Engine
import app.aquabrowser.android.Login
import app.aquabrowser.android.Search
import app.aquabrowser.android.Ui
import java.util.UUID

@Composable
private fun ScreenFrame(title: String, indicators: List<String>, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAqua.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.desk2, c.desk))).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", size = 44.dp) { Ui.open(null) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).height(48.dp).lcd(c, RoundedCornerShape(15.dp)).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { indicators.forEach { Ind(it, size = 8.sp) } }
                    DotText(title, 18.sp)
                }
            }
            content()
        }
    }
}

/* ---------------------------- tabs ---------------------------- */

@Composable
fun TabsScreen() {
    val c = LocalAqua.current
    val b = Aqua.browser
    val tabs = b.visibleTabs()
    ScreenFrame("${tabs.size} ${if (tabs.size == 1) "TAB" else "TABS"}", listOf(if (b.privateMode) "Private" else Aqua.profiles.current.name, "Tabs")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Aqua.profiles.list.forEach { p -> Chip(p.name, !b.privateMode && p.id == Aqua.profiles.current.id) { b.switchProfile(p.id) } }
            Chip("Private", b.privateMode) { b.enterPrivate() }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cols = if (maxWidth > 700.dp) 4 else if (maxWidth > 420.dp) 3 else 2
            LazyVerticalGrid(GridCells.Fixed(cols), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tabs, key = { it.id }) { t ->
                    val on = t.id == b.currentId
                    Column(
                        Modifier.fillMaxWidth().height(150.dp)
                            .glass(c, RoundedCornerShape(20.dp), thick = true, edge = if (on) c.lcdOn else c.edge)
                            .clickable { b.select(t); Ui.open(null) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ind(if (t.home) "Home" else t.host.ifBlank { "Page" }, on = on)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(30.dp).clip(CircleShape).clickable { b.close(t) }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, "Close tab", tint = c.inkDim, modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).lcd(c, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            DotText(if (t.home) "HOME" else t.host.take(1).uppercase().ifBlank { "·" }, 30.sp)
                        }
                        Text(t.label, fontSize = 14.sp, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (b.privateMode) GlassButton("Close private tabs", Modifier.weight(1f), ghost = true) { b.closeAllPrivate() }
            GlassButton(if (b.privateMode) "New private tab" else "New tab", Modifier.weight(1f), primary = true, icon = Icons.Filled.Add) { b.newTab(); Ui.open(null) }
        }
    }
}

/* ---------------------------- settings ---------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = LocalAqua.current
    val s = Aqua.settings
    val b = Aqua.browser
    val p = Aqua.profiles.current
    ScreenFrame("SETTINGS", listOf("Aqua", p.name)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(title = "Look") {
                Label("Colour for ${p.name}")
                ColourPicker(p.color) { Aqua.profiles.setColor(p.id, it) }
                if (p.color == "auto") {
                    Body("Auto fades through blue, teal, green, orange, red, pink and purple, like Aqua MD.", dim = true, size = 14.sp)
                    Label("Change colour every")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10 to "10 s", 30 to "30 s", 60 to "1 min", 300 to "5 min", 900 to "15 min", 3600 to "1 hour").forEach { (v, l) -> Chip(l, s.autoCycle.value == v) { s.autoCycle.value = v } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Hold, then fade", !s.autoSmooth.value) { s.autoSmooth.value = false }
                        Chip("Drift smoothly", s.autoSmooth.value) { s.autoSmooth.value = true }
                    }
                }
                Label("Glass")
                var glass by remember { mutableFloatStateOf(s.glass.value.toFloat()) }
                Slider(
                    value = glass, onValueChange = { glass = it; s.glass.value = it.toInt() }, valueRange = 20f..160f,
                    colors = SliderDefaults.colors(thumbColor = c.lcdHot, activeTrackColor = c.lcdOn, inactiveTrackColor = c.edgeSoft),
                )
                Label("Address bar")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Dot matrix", s.addressDot.value) { s.addressDot.value = true }
                    Chip("Plain", !s.addressDot.value) { s.addressDot.value = false }
                }
                Label("Toolbar")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("At the bottom", s.toolbarBottom.value) { s.toolbarBottom.value = true }
                    Chip("At the top", !s.toolbarBottom.value) { s.toolbarBottom.value = false }
                }
            }

            Card(title = "Profiles") {
                Body("Each profile keeps its own sign-ins, cookies, history, tabs, home page, saved passwords and colour, like Home and Work.", dim = true, size = 14.sp)
                Aqua.profiles.list.forEach { pr ->
                    val on = pr.id == p.id
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { b.switchProfile(pr.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val col = Palette.byKey(if (pr.color == "auto") "blue" else pr.color)
                        Box(Modifier.size(18.dp).clip(CircleShape).background(Brush.radialGradient(listOf(hsl(col.h, 1f, .85f), hsl(col.h, .85f, .45f)))))
                        Spacer(Modifier.width(12.dp))
                        Text(pr.name, fontSize = 17.sp, color = if (on) c.lcdHot else c.ink, modifier = Modifier.weight(1f))
                        if (on) Ind("In use")
                        else if (Aqua.profiles.list.size > 1) Box(Modifier.size(34.dp).clip(CircleShape).clickable {
                            Ui.show(Dialog.Message("Remove ${pr.name}?", "Its tabs and home page go. Its cookies and saved passwords stay on the phone until you clear them.", "Remove", "Cancel") { ok ->
                                if (ok) { b.tabs.filter { it.profileId == pr.id }.forEach { b.close(it) }; Aqua.profiles.remove(pr.id) }
                            })
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Delete, "Remove ${pr.name}", tint = c.inkFaint, modifier = Modifier.size(18.dp)) }
                    }
                }
                GlassButton("Add a profile", icon = Icons.Filled.Add) {
                    Ui.show(Dialog.Input("New profile", "A name, like School or Gaming.", "", ok = "Create") { name ->
                        if (!name.isNullOrBlank()) { val np = Aqua.profiles.add(name, Palette.all[Aqua.profiles.list.size % Palette.all.size].key); b.switchProfile(np.id); Ui.open(null) }
                    })
                }
            }

            Card(title = "Passwords and cards") {
                Column(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ind("On this phone"); Ind("Encrypted"); Ind("No Google") }
                    DotText(if (s.lockVault.value) "LOCKED" else "UNLOCKED", 30.sp)
                    DotText("Hardware-backed key", 14.sp, weight = FontWeight.Bold, glow = false, color = c.lcdOn.copy(alpha = .7f))
                }
                Body("Saved passwords and cards stay in Aqua Browser on this phone, encrypted with a key kept in the phone's secure hardware. Nothing goes to a Google account.", dim = true, size = 14.sp)
                GlassButton("Open passwords and cards", icon = Icons.Filled.Lock, primary = true) { Ui.open("vault") }
                ToggleRow("Offer to save passwords", s.offerSave.value) { s.offerSave.value = it }
                ToggleRow("Ask for fingerprint or screen lock", s.lockVault.value, "Before showing or filling a saved password or card") { s.lockVault.value = it }
            }

            Card(title = "Privacy") {
                ToggleRow("Block ads", s.blockAds.value, "Uses the engine's built-in ad and tracker lists") { s.blockAds.value = it; Engine.applyBlocking() }
                ToggleRow("Strict tracking protection", s.strictTracking.value, "Blocks trackers, cryptominers and fingerprinting") { s.strictTracking.value = it; Engine.applyBlocking() }
                GlassButton("Clear ${p.name}'s browsing data", danger = true) {
                    Ui.show(Dialog.Message("Clear ${p.name}'s data?", "Signs you out of sites in this profile and deletes its cookies, site data and history. Saved passwords stay.", "Clear", "Cancel") { ok ->
                        if (ok) {
                            Aqua.runtime.storageController.clearDataForSessionContext("aqua-" + p.id)
                            Aqua.history.clear(p.id)
                            Ui.toast("${p.name}'s browsing data cleared")
                        }
                    })
                }
            }

            Card(title = "Search") {
                Label("Search engine")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Search.engines.forEach { e -> Chip(e.name, s.engine.value == e.name) { s.engine.value = e.name } }
                }
                ToggleRow("Show suggestions while I type", s.suggestions.value, "What you type is sent to your search engine") { s.suggestions.value = it }
            }

            Card(title = "Start-up") {
                ToggleRow("Reopen my tabs", s.restoreTabs.value, "Otherwise each profile starts on its home page") { s.restoreTabs.value = it }
                GlassButton("Show the welcome again", ghost = true) { s.welcomed.value = false; Ui.open(null) }
            }

            Card(title = "About") {
                Column(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ind("Aqua"); Ind("V" + versionName()); Ind("Glass web browser", on = false) }
                    DotText("Aqua Browser", 30.sp)
                    DotText("Engine " + Aqua.engineVersion.ifBlank { "GeckoView" } + " · Mozilla", 13.sp, weight = FontWeight.Bold, glow = false, color = c.lcdOn.copy(alpha = .7f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Try Aqua MD ↗", primary = true) { b.newTab(Aqua.AQUA_MD, private = false); Ui.open(null) }
                    GlassButton("Website", ghost = true) { b.newTab(Aqua.SITE, private = false); Ui.open(null) }
                    GlassButton("Source code", ghost = true) { b.newTab(Aqua.SOURCE, private = false); Ui.open(null) }
                }
                Body("Aqua Browser is made by the maker of Aqua MD, the see-through MiniDisc deck for your desktop, and borrows its look. It runs on GeckoView, Mozilla's open-source browser engine (Mozilla Public License 2.0). Firefox is a trademark of the Mozilla Foundation; Aqua Browser is not made or endorsed by Mozilla.", dim = true, size = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun versionName(): String = runCatching { Aqua.app.packageManager.getPackageInfo(Aqua.app.packageName, 0).versionName ?: "" }.getOrDefault("")

/* ---------------------------- passwords and cards ---------------------------- */

@Composable
fun VaultScreen() {
    val c = LocalAqua.current
    val v = Aqua.vault
    var showCards by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        v.ensure()
        if (!Ui.vaultUnlocked && !asked) { asked = true; Ui.unlock("Open saved passwords") { ok -> if (ok) Ui.vaultUnlocked = true } }
    }
    ScreenFrame(if (showCards) "CARDS" else "PASSWORDS", listOf(Aqua.profiles.current.name, "Encrypted", "This phone")) {
        if (!Ui.vaultUnlocked) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText("LOCKED", 40.sp)
                Body("Unlock with your fingerprint, face or screen lock to see saved passwords and cards.", dim = true)
                GlassButton("Unlock", primary = true, icon = Icons.Filled.Lock) { Ui.unlock("Open saved passwords") { ok -> if (ok) Ui.vaultUnlocked = true } }
            }
            return@ScreenFrame
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Passwords (${v.logins.size})", !showCards) { showCards = false }
            Chip("Cards (${v.cards.size})", showCards) { showCards = true }
            Spacer(Modifier.weight(1f))
            GlassIcon(Icons.Filled.Add, if (showCards) "Add a card" else "Add a password", size = 40.dp) { if (showCards) editCard(null) else editLogin(null) }
        }
        if (!showCards) LcdField(query, { query = it }, Modifier.padding(10.dp), placeholder = "Search saved passwords")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showCards) {
                val list = v.logins.filter { query.isBlank() || it.host.contains(query, true) || it.username.contains(query, true) }.sortedBy { it.host }
                if (list.isEmpty()) Body(if (v.logins.isEmpty()) "No saved passwords yet. When you sign in to a site, Aqua offers to save the password." else "Nothing matches.", dim = true)
                list.forEach { l -> LoginRow(l) }
            } else {
                if (v.cards.isEmpty()) Body("No saved cards yet. When you pay on a site, Aqua offers to save the card (never its security code).", dim = true)
                v.cards.forEach { cd -> CardRow(cd) }
            }
        }
    }
}

@Composable
private fun LoginRow(l: Login) {
    val c = LocalAqua.current
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(18.dp), thick = true).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.host, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.username.ifBlank { "(no user name)" }, fontSize = 14.sp, color = c.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassIcon(Icons.Filled.Edit, "Edit", size = 36.dp) { editLogin(l) }
        }
        Row(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            DotText(if (show) l.password else "•".repeat(l.password.length.coerceIn(6, 14)), 15.sp, Modifier.weight(1f), weight = FontWeight.Bold)
            Text(if (show) "HIDE" else "SHOW", fontFamily = AquaFonts.label, fontSize = 9.sp, color = c.lcdOn, modifier = Modifier.clickable { show = !show }.padding(6.dp))
            Text("COPY", fontFamily = AquaFonts.label, fontSize = 9.sp, color = c.lcdOn, modifier = Modifier.clickable { Aqua.browser.copy(l.password) }.padding(6.dp))
        }
    }
}

@Composable
private fun CardRow(cd: VaultCard) {
    val c = LocalAqua.current
    Row(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(18.dp), thick = true).clickable { editCard(cd) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DotText("•••• " + cd.number.takeLast(4), 22.sp)
            Text(cd.name + "   " + cd.month.padStart(2, '0') + "/" + cd.year.takeLast(2), fontSize = 14.sp, color = c.inkDim)
        }
        Icon(Icons.Filled.Edit, "Edit card", tint = c.inkDim)
    }
}

private fun editLogin(l: Login?) {
    Ui.show(Dialog.Form(
        if (l == null) "Add a password" else "Edit ${l.host}",
        listOf(Dialog.Field("Site", l?.origin ?: "https://"), Dialog.Field("User name", l?.username ?: ""), Dialog.Field("Password", l?.password ?: "", password = true)),
    ) { v ->
        if (v == null) return@Form
        val origin = Search.toUrl(v[0]).let { u -> runCatching { val x = android.net.Uri.parse(u); "${x.scheme}://${x.host}" }.getOrDefault(u) }
        if (origin.isBlank()) return@Form
        val now = System.currentTimeMillis()
        Aqua.vault.putLogin(Login(l?.guid ?: UUID.randomUUID().toString(), origin, l?.formAction, l?.realm, v[1], v[2], l?.created ?: now, now))
        Ui.toast("Password saved")
    })
    if (l != null) Ui.toast("Editing ${l.host}", "Delete") {
        Ui.show(Dialog.Message("Delete the password for ${l.host}?", "This can't be undone.", "Delete", "Cancel") { ok -> if (ok) Aqua.vault.deleteLogin(l) })
    }
}

private fun editCard(cd: VaultCard?) {
    Ui.show(Dialog.Form(
        if (cd == null) "Add a card" else "Edit card",
        listOf(Dialog.Field("Name on card", cd?.name ?: ""), Dialog.Field("Card number", cd?.number ?: "", number = true), Dialog.Field("Expiry month (MM)", cd?.month ?: "", number = true), Dialog.Field("Expiry year (YYYY)", cd?.year ?: "", number = true)),
    ) { v ->
        if (v == null) return@Form
        val number = v[1].filter { it.isDigit() }
        if (number.length < 12) { Ui.toast("That card number looks too short"); return@Form }
        Aqua.vault.putCard(VaultCard(cd?.guid ?: UUID.randomUUID().toString(), v[0].trim(), number, v[2].trim().trimStart('0').ifBlank { "1" }, v[3].trim()))
        Ui.toast("Card saved")
    })
    if (cd != null) Ui.toast("Editing card ${cd.number.takeLast(4)}", "Delete") {
        Ui.show(Dialog.Message("Delete the card ending ${cd.number.takeLast(4)}?", "This can't be undone.", "Delete", "Cancel") { ok -> if (ok) Aqua.vault.deleteCard(cd) })
    }
}
