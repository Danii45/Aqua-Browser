package app.aquabrowser.android.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aquabrowser.android.Aqua
import app.aquabrowser.android.Dialog
import app.aquabrowser.android.HomeData
import app.aquabrowser.android.HomeStore
import app.aquabrowser.android.Tab
import app.aquabrowser.android.Tile
import app.aquabrowser.android.TodoItem
import app.aquabrowser.android.Ui
import app.aquabrowser.android.Widget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(tab: Tab?) {
    val c = LocalAqua.current
    val b = Aqua.browser
    val profile = if (b.privateMode) "private" else (tab?.profileId ?: b.profileId)
    val data = remember(profile) { Aqua.home.data(profile) }
    val editing = Ui.homeEdit
    fun save() = Aqua.home.save(data)
    fun open(url: String) { val t = b.current ?: b.newTab(); b.load(t, url) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.desk2, c.desk)))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 600.dp
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = if (wide) 48.dp else 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (b.privateMode) item { PrivateNote() }
                itemsIndexed(data.widgets, key = { i, w -> "${w.type}-$i-${System.identityHashCode(w)}" }) { i, w ->
                    Column {
                        if (editing) EditBar(data, i, w) { save() }
                        WidgetView(w, profile, wide, ::open) { save() }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        if (editing) {
                            GlassButton("Add a widget", icon = Icons.Filled.Add) { addWidget(data) { save() } }
                            Spacer(Modifier.width(10.dp))
                            GlassButton("Done", primary = true, icon = Icons.Filled.Check) { Ui.homeEdit = false; save() }
                        } else GlassButton("Customize", ghost = true, icon = Icons.Filled.Edit) { Ui.homeEdit = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateNote() {
    val c = LocalAqua.current
    Column(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ind("Private"); Ind("No history"); Ind("No cookies kept") }
        DotText("Private", 34.sp)
        Body("Pages you visit in private tabs aren't saved, and their cookies are deleted when you close the last private tab.", dim = true, size = 14.sp)
    }
}

@Composable
private fun EditBar(data: HomeData, i: Int, w: Widget, save: () -> Unit) {
    val c = LocalAqua.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(HomeStore.TYPES[w.type] ?: w.type, Modifier.weight(1f))
        GlassIcon(Icons.Filled.KeyboardArrowUp, "Move up", size = 34.dp, enabled = i > 0) { data.widgets.add(i - 1, data.widgets.removeAt(i)); save() }
        GlassIcon(Icons.Filled.KeyboardArrowDown, "Move down", size = 34.dp, enabled = i < data.widgets.size - 1) { data.widgets.add(i + 1, data.widgets.removeAt(i)); save() }
        GlassIcon(Icons.Filled.Delete, "Remove", size = 34.dp) { data.widgets.removeAt(i); save() }
    }
}

private fun addWidget(data: HomeData, save: () -> Unit) {
    val types = HomeStore.TYPES.entries.toList()
    Ui.show(Dialog.Pick("Add a widget", types.map { it.value }) { picked ->
        val i = picked?.firstOrNull() ?: return@Pick
        data.widgets += Widget(types[i].key)
        save()
    })
}

@Composable
private fun WidgetView(w: Widget, profile: String, wide: Boolean, open: (String) -> Unit, save: () -> Unit) {
    when (w.type) {
        "greeting" -> Greeting(profile)
        "clock" -> ClockWidget()
        "search" -> SearchWidget()
        "shortcuts" -> Shortcuts(w, wide, open, save)
        "weather" -> WeatherWidget(w, save)
        "todo" -> TodoWidget(w, save)
        "notes" -> NotesWidget(w, save)
        "countdown" -> CountdownWidget(w, save)
        "visited" -> VisitedWidget(profile, wide, open)
    }
}

@Composable
private fun Greeting(profile: String) {
    val c = LocalAqua.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val text = when { hour < 5 -> "Good night"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        Ind(if (profile == "private") "Private" else (Aqua.profiles.byId(profile)?.name ?: profile) + " profile")
    }
}

@Composable
private fun ClockWidget() {
    val c = LocalAqua.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) } }
    val d = Date(now)
    val is24 = android.text.format.DateFormat.is24HourFormat(Aqua.app)
    Column(Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ind("Time"); Ind(if (is24) "24H" else SimpleDateFormat("a", Locale.getDefault()).format(d)); Ind(SimpleDateFormat("ss", Locale.getDefault()).format(d), on = false)
        }
        DotText(SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(d), 64.sp)
        DotText(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(d).uppercase(), 18.sp, weight = FontWeight.Bold, glow = false, color = c.lcdOn.copy(alpha = .75f))
    }
}

@Composable
private fun SearchWidget() {
    val c = LocalAqua.current
    Row(
        Modifier.fillMaxWidth().height(58.dp).lcd(c, RoundedCornerShape(18.dp)).clickable { Ui.startEditing("") }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ind(app.aquabrowser.android.Search.engine().name)
        Spacer(Modifier.width(12.dp))
        DotText("Search the web", 18.sp, Modifier.weight(1f), weight = FontWeight.Bold, color = c.lcdOn.copy(alpha = .5f), glow = false)
        Icon(Icons.Filled.Search, "Search", tint = c.lcdOn, modifier = Modifier.size(22.dp))
    }
}

private fun hashHue(s: String): Float { var x = 0; for (ch in s) x = (x * 31 + ch.code) % 360; return ((x % 360) + 360) % 360f }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shortcuts(w: Widget, wide: Boolean, open: (String) -> Unit, save: () -> Unit) {
    val c = LocalAqua.current
    Column(Modifier.fillMaxWidth().glass(c).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (wide) 6 else 4
            val cell = (maxWidth - 8.dp * (cols - 1)) / cols
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                w.tiles.forEachIndexed { i, t ->
                    TileView(t, Modifier.width(cell), onClick = { open(t.url) }, onLongClick = { editTile(w, i, save) })
                }
                Column(Modifier.width(cell).clip(RoundedCornerShape(14.dp)).clickable { editTile(w, -1, save) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).border(BorderStroke(1.5.dp, c.edge), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, "Add a shortcut", tint = c.lcdOn)
                    }
                    Text("Add", fontSize = 13.sp, color = c.inkDim)
                }
            }
        }
    }
}

@Composable
private fun TileView(t: Tile, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalAqua.current
    val host = runCatching { Uri.parse(t.url).host ?: t.title }.getOrDefault(t.title)
    val hue = hashHue(host.removePrefix("www."))
    Column(modifier.clip(RoundedCornerShape(14.dp)).press(onClick, onLongClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(17.dp))
                .background(Brush.radialGradient(listOf(hsl(hue, 1f, .82f, .9f), hsl(hue, .85f, .52f, .7f), hsl(hue, .8f, .32f, .85f))))
                .border(BorderStroke(1.5.dp, hsl(hue, 1f, .85f, .55f)), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(t.title.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White)
        }
        Text(t.title, fontSize = 13.sp, color = c.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun editTile(w: Widget, i: Int, save: () -> Unit) {
    val old = w.tiles.getOrNull(i)
    Ui.show(Dialog.Form(if (old == null) "Add a shortcut" else "Edit shortcut", listOf(Dialog.Field("Name", old?.title ?: ""), Dialog.Field("Address", old?.url ?: "https://")), ok = "Save") { v ->
        if (v == null) return@Form
        val url = app.aquabrowser.android.Search.toUrl(v[1])
        if (url.isBlank()) return@Form
        val name = v[0].ifBlank { Uri.parse(url).host?.removePrefix("www.") ?: url }
        if (old == null) w.tiles += Tile(name, url) else w.tiles[i] = Tile(name, url)
        save()
    })
    if (old != null) Ui.toast("Editing ${old.title}", "Remove") { w.tiles.remove(old); save() }
}

/* ---------- weather (Open-Meteo: free, no account, no tracking) ---------- */

private val WEATHER = mapOf(
    0 to "Clear", 1 to "Mostly clear", 2 to "Partly cloudy", 3 to "Cloudy", 45 to "Fog", 48 to "Freezing fog",
    51 to "Light drizzle", 53 to "Drizzle", 55 to "Heavy drizzle", 56 to "Freezing drizzle", 57 to "Freezing drizzle",
    61 to "Light rain", 63 to "Rain", 65 to "Heavy rain", 66 to "Freezing rain", 67 to "Freezing rain",
    71 to "Light snow", 73 to "Snow", 75 to "Heavy snow", 77 to "Snow grains", 80 to "Showers", 81 to "Showers", 82 to "Heavy showers",
    85 to "Snow showers", 86 to "Snow showers", 95 to "Thunderstorm", 96 to "Thunderstorm, hail", 99 to "Thunderstorm, hail",
)

private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000; conn.readTimeout = 8000
    JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
}

@Composable
private fun WeatherWidget(w: Widget, save: () -> Unit) {
    val c = LocalAqua.current
    var temp by remember { mutableStateOf<String?>(null) }
    var desc by remember { mutableStateOf("") }
    var hiLo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(w.lat, w.lon, w.city) {
        if (w.city.isBlank()) return@LaunchedEffect
        runCatching {
            val o = getJson("https://api.open-meteo.com/v1/forecast?latitude=${w.lat}&longitude=${w.lon}&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
            val cur = o.getJSONObject("current")
            temp = Math.round(cur.getDouble("temperature_2m")).toString() + "°"
            desc = WEATHER[cur.getInt("weather_code")] ?: ""
            val d = o.getJSONObject("daily")
            hiLo = "H ${Math.round(d.getJSONArray("temperature_2m_max").getDouble(0))}°  L ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(0))}°"
            error = null
        }.onFailure { error = "Couldn't get the weather right now." }
    }
    Column(Modifier.fillMaxWidth().glass(c).clickable { pickCity(w, save) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label("Weather")
        if (w.city.isBlank()) Body("Tap to pick your city.", dim = true)
        else {
            Row(verticalAlignment = Alignment.Bottom) {
                DotText(temp ?: "--", 48.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(desc, fontSize = 17.sp, color = c.ink)
                    Text("${w.city}  $hiLo", fontSize = 13.sp, color = c.inkDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            error?.let { Body(it, dim = true, size = 13.sp) }
        }
    }
}

private fun pickCity(w: Widget, save: () -> Unit) {
    Ui.show(Dialog.Input("Your city", "Type a city, then OK.", w.city) { name ->
        if (name.isNullOrBlank()) return@Input
        Thread {
            runCatching {
                val conn = URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(name.trim())).openConnection() as HttpURLConnection
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val r = o.optJSONArray("results")?.optJSONObject(0) ?: throw IllegalStateException()
                Aqua.onMain {
                    w.lat = r.getDouble("latitude"); w.lon = r.getDouble("longitude"); w.city = r.optString("name", name)
                    save()
                }
            }.onFailure { Ui.toast("Couldn't find $name") }
        }.start()
    })
}

@Composable
private fun TodoWidget(w: Widget, save: () -> Unit) {
    val c = LocalAqua.current
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass(c).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("To-do")
        LcdField(draft, { draft = it }, placeholder = "Add a task", onDone = {
            if (draft.isNotBlank()) { w.items += TodoItem(draft.trim(), false); draft = ""; save() }
        })
        if (w.items.isEmpty()) Body("Nothing to do.", dim = true, size = 14.sp)
        w.items.toList().forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { item.done = !item.done; save() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (item.done) c.lcdOn.copy(alpha = .35f) else androidx.compose.ui.graphics.Color.Transparent)
                        .border(BorderStroke(1.5.dp, if (item.done) c.lcdOn else c.edge), RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (item.done) Icon(Icons.Filled.Check, null, tint = c.lcdHot, modifier = Modifier.size(15.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(item.text, fontSize = 16.sp, color = if (item.done) c.inkFaint else c.ink, textDecoration = if (item.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp).clip(CircleShape).clickable { w.items.remove(item); save() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Delete task", tint = c.inkFaint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesWidget(w: Widget, save: () -> Unit) {
    val c = LocalAqua.current
    var text by remember(w) { mutableStateOf(w.text) }
    LaunchedEffect(text) { delay(600); if (w.text != text) { w.text = text; save() } }
    Column(Modifier.fillMaxWidth().glass(c).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Notes")
        LcdField(text, { text = it }, placeholder = "Write anything…", singleLine = false, modifier = Modifier.height(120.dp))
    }
}

@Composable
private fun CountdownWidget(w: Widget, save: () -> Unit) {
    val c = LocalAqua.current
    val days = runCatching {
        val target = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(w.date)!!.time
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        TimeUnit.MILLISECONDS.toDays(target - today + TimeUnit.HOURS.toMillis(12))
    }.getOrNull()
    Column(
        Modifier.fillMaxWidth().lcd(c, RoundedCornerShape(22.dp)).clickable {
            Ui.show(Dialog.Form("Countdown", listOf(Dialog.Field("What", w.title), Dialog.Field("Date (YYYY-MM-DD)", w.date)), ok = "Save") { v ->
                if (v != null) { w.title = v[0]; w.date = v[1].trim(); save() }
            })
        }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ind("Countdown"); Ind(w.title.ifBlank { "Tap to set" }, on = w.title.isNotBlank()) }
        if (days == null) DotText("-- DAYS", 40.sp)
        else DotText(if (days == 0L) "TODAY" else if (days < 0) "${-days} DAYS AGO" else "$days ${if (days == 1L) "DAY" else "DAYS"}", 40.sp)
    }
}

@Composable
private fun VisitedWidget(profile: String, wide: Boolean, open: (String) -> Unit) {
    val c = LocalAqua.current
    val top = remember(profile) { if (profile == "private") emptyList() else Aqua.history.top(profile, if (wide) 12 else 8) }
    Column(Modifier.fillMaxWidth().glass(c).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Most visited")
        if (top.isEmpty()) Body("Sites you visit often will show up here.", dim = true, size = 14.sp)
        top.forEach { v ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(v.url) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val host = runCatching { Uri.parse(v.url).host ?: "" }.getOrDefault("").removePrefix("www.")
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(hsl(hashHue(host), .8f, .5f, .7f)), contentAlignment = Alignment.Center) {
                    Text(host.take(1).uppercase(), fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title.ifBlank { host }, fontSize = 15.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(host, fontSize = 12.sp, color = c.lcdOn.copy(alpha = .8f), maxLines = 1)
                }
            }
        }
    }
}
