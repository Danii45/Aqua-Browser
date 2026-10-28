package app.aquabrowser.android

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/* ======================= passwords and cards ======================= */

data class Login(val guid: String, val origin: String, val formAction: String?, val realm: String?, val username: String, val password: String, val created: Long, val used: Long) {
    val host: String get() = runCatching { Uri.parse(origin).host ?: origin }.getOrDefault(origin).removePrefix("www.")
    fun toEntry(): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
        .guid(guid).origin(origin).formActionOrigin(formAction).httpRealm(realm).username(username).password(password).build()
}

data class Card(val guid: String, val name: String, val number: String, val month: String, val year: String) {
    fun toEntry(): Autocomplete.CreditCard = Autocomplete.CreditCard.Builder()
        .guid(guid).name(name).number(number).expirationMonth(month).expirationYear(year).build()
}

/**
 * Saved passwords and cards for each profile, in a file encrypted with a key that lives in the phone's
 * hardware key store (it can't be copied off the phone). Nothing is sent anywhere.
 */
class Vault(ctx: Context) {
    private val dir = File(ctx.filesDir, "vault").apply { mkdirs() }
    val logins = mutableStateListOf<Login>()
    val cards = mutableStateListOf<Card>()
    private var loadedFor: String? = null

    private fun file(profile: String) = File(dir, "$profile.bin")

    /** Loads the current profile's vault (a no-op if it already is). */
    fun ensure() {
        val p = Aqua.profiles.current.id
        if (loadedFor == p) return
        loadedFor = p
        logins.clear(); cards.clear()
        val f = file(p)
        if (!f.exists()) return
        runCatching {
            val o = JSONObject(String(decrypt(f.readBytes()), Charsets.UTF_8))
            val la = o.optJSONArray("logins") ?: JSONArray()
            for (i in 0 until la.length()) {
                val x = la.getJSONObject(i)
                logins += Login(x.getString("guid"), x.getString("origin"), x.optString("action").ifEmpty { null }, x.optString("realm").ifEmpty { null },
                    x.optString("user"), x.optString("pass"), x.optLong("created"), x.optLong("used"))
            }
            val ca = o.optJSONArray("cards") ?: JSONArray()
            for (i in 0 until ca.length()) {
                val x = ca.getJSONObject(i)
                cards += Card(x.getString("guid"), x.optString("name"), x.optString("number"), x.optString("month"), x.optString("year"))
            }
        }.onFailure { Ui.toast("Couldn't open saved passwords: ${it.message}") }
    }

    private fun save() {
        val p = loadedFor ?: return
        val la = JSONArray()
        logins.forEach { la.put(JSONObject().put("guid", it.guid).put("origin", it.origin).put("action", it.formAction ?: "").put("realm", it.realm ?: "").put("user", it.username).put("pass", it.password).put("created", it.created).put("used", it.used)) }
        val ca = JSONArray()
        cards.forEach { ca.put(JSONObject().put("guid", it.guid).put("name", it.name).put("number", it.number).put("month", it.month).put("year", it.year)) }
        val bytes = encrypt(JSONObject().put("logins", la).put("cards", ca).toString().toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "$p.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file(p))
    }

    private fun hostOf(s: String) = runCatching { Uri.parse(s).host ?: s }.getOrDefault(s).lowercase()

    fun loginsFor(domain: String): List<Login> {
        ensure()
        val d = domain.lowercase().removePrefix("www.")
        return logins.filter { val h = hostOf(it.origin).removePrefix("www."); h == d || h.endsWith(".$d") || d.endsWith(".$h") }
    }

    fun find(e: Autocomplete.LoginEntry): Login? {
        ensure()
        return logins.firstOrNull { (e.guid != null && it.guid == e.guid) || (hostOf(it.origin) == hostOf(e.origin) && it.username == e.username) }
    }

    fun saveLogin(e: Autocomplete.LoginEntry) {
        ensure()
        val now = System.currentTimeMillis()
        val old = find(e)
        val l = Login(old?.guid ?: e.guid ?: UUID.randomUUID().toString(), e.origin, e.formActionOrigin, e.httpRealm, e.username, e.password, old?.created ?: now, now)
        if (old != null) logins[logins.indexOf(old)] = l else logins += l
        save()
    }

    fun putLogin(l: Login) { ensure(); val i = logins.indexOfFirst { it.guid == l.guid }; if (i >= 0) logins[i] = l else logins += l; save() }
    fun deleteLogin(l: Login) { ensure(); logins.removeAll { it.guid == l.guid }; save() }
    fun touch(guid: String?) { ensure(); val i = logins.indexOfFirst { it.guid == guid }; if (i >= 0) { logins[i] = logins[i].copy(used = System.currentTimeMillis()); save() } }

    fun saveCard(c: Autocomplete.CreditCard) {
        ensure()
        val old = cards.firstOrNull { (c.guid != null && it.guid == c.guid) || it.number == c.number }
        val n = Card(old?.guid ?: c.guid ?: UUID.randomUUID().toString(), c.name, c.number, c.expirationMonth, c.expirationYear)
        if (old != null) cards[cards.indexOf(old)] = n else cards += n
        save()
    }

    fun putCard(c: Card) { ensure(); val i = cards.indexOfFirst { it.guid == c.guid }; if (i >= 0) cards[i] = c else cards += c; save() }
    fun deleteCard(c: Card) { ensure(); cards.removeAll { it.guid == c.guid }; save() }

    /* AES-256-GCM with a hardware-backed key */
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    companion object { private const val ALIAS = "aqua_vault" }
}

/** Hands saved logins and cards to the engine when a page has a sign-in or payment form. */
object VaultStorage : Autocomplete.StorageDelegate {
    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? =
        GeckoResult.fromValue(Aqua.vault.loginsFor(domain).map { it.toEntry() }.toTypedArray())

    override fun onLoginSave(login: Autocomplete.LoginEntry) { Aqua.onMain { Aqua.vault.saveLogin(login) } }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) { Aqua.onMain { Aqua.vault.touch(login.guid) } }

    override fun onCreditCardFetch(): GeckoResult<Array<Autocomplete.CreditCard>>? {
        Aqua.vault.ensure()
        return GeckoResult.fromValue(Aqua.vault.cards.map { it.toEntry() }.toTypedArray())
    }

    override fun onCreditCardSave(creditCard: Autocomplete.CreditCard) { Aqua.onMain { Aqua.vault.saveCard(creditCard) } }
}

/* ======================= history ======================= */

class Visit(val url: String, var title: String, var visits: Int, var last: Long)

/** Pages visited in each profile (never in private tabs), for address-bar suggestions and "most visited". */
class History(ctx: Context) {
    private val dir = File(ctx.filesDir, "history").apply { mkdirs() }
    private val cache = mutableMapOf<String, LinkedHashMap<String, Visit>>()
    private val dirty = mutableSetOf<String>()

    private fun map(profile: String): LinkedHashMap<String, Visit> = cache.getOrPut(profile) {
        val m = LinkedHashMap<String, Visit>()
        runCatching {
            val a = JSONArray(File(dir, "$profile.json").readText())
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); m[o.getString("u")] = Visit(o.getString("u"), o.optString("t"), o.optInt("n", 1), o.optLong("l")) }
        }
        m
    }

    fun visit(profile: String, url: String, title: String) {
        if (!url.startsWith("http")) return
        val m = map(profile)
        val v = m[url]
        if (v == null) m[url] = Visit(url, title, 1, System.currentTimeMillis()) else { v.visits++; v.last = System.currentTimeMillis(); if (title.isNotBlank()) v.title = title }
        if (m.size > 4000) m.values.sortedBy { it.last }.take(m.size - 3500).forEach { m.remove(it.url) }
        saveSoon(profile)
    }

    fun title(profile: String, url: String, title: String) {
        val v = map(profile)[url] ?: return
        if (title.isNotBlank() && v.title != title) { v.title = title; saveSoon(profile) }
    }

    fun search(profile: String, q: String, limit: Int = 6): List<Visit> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return map(profile).values
            .filter { it.url.lowercase().contains(needle) || it.title.lowercase().contains(needle) }
            .sortedWith(compareByDescending<Visit> { it.url.lowercase().removePrefix("https://").removePrefix("www.").startsWith(needle) }.thenByDescending { it.visits }.thenByDescending { it.last })
            .take(limit)
    }

    fun top(profile: String, limit: Int = 8): List<Visit> = map(profile).values.sortedByDescending { it.visits * 10 + (it.last / 86_400_000L) }.take(limit)

    fun clear(profile: String) { cache[profile] = LinkedHashMap(); File(dir, "$profile.json").delete() }

    private val saver = Runnable { flush() }
    private fun saveSoon(profile: String) { dirty += profile; Aqua.main.removeCallbacks(saver); Aqua.main.postDelayed(saver, 3000) }

    private fun flush() {
        val todo = dirty.toList(); dirty.clear()
        val snapshot = todo.associateWith { p -> JSONArray().apply { map(p).values.forEach { put(JSONObject().put("u", it.url).put("t", it.title).put("n", it.visits).put("l", it.last)) } }.toString() }
        Thread { snapshot.forEach { (p, text) -> runCatching { File(dir, "$p.json").writeText(text) } } }.start()
    }
}

/* ======================= the home page ======================= */

data class Tile(val title: String, val url: String)

class TodoItem(text: String, done: Boolean) {
    var text by mutableStateOf(text)
    var done by mutableStateOf(done)
}

class Widget(val type: String) {
    val tiles = mutableStateListOf<Tile>()
    val items = mutableStateListOf<TodoItem>()
    var text by mutableStateOf("")
    var title by mutableStateOf("")
    var date by mutableStateOf("")
    var city by mutableStateOf("")
    var lat by mutableDoubleStateOf(0.0)
    var lon by mutableDoubleStateOf(0.0)
    var version by mutableIntStateOf(0)
}

class HomeData(val profile: String) {
    val widgets = mutableStateListOf<Widget>()
}

/** Each profile's home page: which widgets, in what order, and what's in them. */
class HomeStore(private val sp: SharedPreferences) {
    private val cache = mutableMapOf<String, HomeData>()

    companion object {
        val TYPES = linkedMapOf(
            "greeting" to "Greeting", "clock" to "Clock", "search" to "Search", "shortcuts" to "Shortcuts",
            "weather" to "Weather", "todo" to "To-do", "notes" to "Notes", "countdown" to "Countdown", "visited" to "Most visited",
        )
    }

    fun data(profile: String): HomeData = cache.getOrPut(profile) { load(profile) }

    private fun defaults(profile: String): HomeData {
        val d = HomeData(profile)
        listOf("greeting", "clock", "search", "shortcuts", "todo", "notes").forEach { d.widgets += Widget(it) }
        val tiles = if (profile == "work") listOf(
            Tile("Gmail", "https://mail.google.com"), Tile("Calendar", "https://calendar.google.com"), Tile("Drive", "https://drive.google.com"),
            Tile("Docs", "https://docs.google.com"), Tile("GitHub", "https://github.com"), Tile("Wikipedia", "https://www.wikipedia.org"),
        ) else listOf(
            Tile("Google", "https://www.google.com"), Tile("YouTube", "https://m.youtube.com"), Tile("Gmail", "https://mail.google.com"),
            Tile("Wikipedia", "https://www.wikipedia.org"), Tile("Reddit", "https://www.reddit.com"), Tile("Spotify", "https://open.spotify.com"),
            Tile("GitHub", "https://github.com"), Tile("Aqua MD", Aqua.AQUA_MD),
        )
        d.widgets.first { it.type == "shortcuts" }.tiles += tiles
        return d
    }

    private fun load(profile: String): HomeData {
        val raw = sp.getString("home.$profile", null) ?: return defaults(profile)
        return runCatching {
            val d = HomeData(profile)
            val a = JSONObject(raw).getJSONArray("widgets")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val w = Widget(o.getString("type"))
                o.optJSONArray("tiles")?.let { t -> for (j in 0 until t.length()) { val x = t.getJSONObject(j); w.tiles += Tile(x.getString("t"), x.getString("u")) } }
                o.optJSONArray("items")?.let { t -> for (j in 0 until t.length()) { val x = t.getJSONObject(j); w.items += TodoItem(x.getString("t"), x.optBoolean("d")) } }
                w.text = o.optString("text"); w.title = o.optString("title"); w.date = o.optString("date")
                w.city = o.optString("city"); w.lat = o.optDouble("lat", 0.0); w.lon = o.optDouble("lon", 0.0)
                d.widgets += w
            }
            d
        }.getOrElse { defaults(profile) }
    }

    fun save(d: HomeData) {
        val a = JSONArray()
        d.widgets.forEach { w ->
            val o = JSONObject().put("type", w.type).put("text", w.text).put("title", w.title).put("date", w.date).put("city", w.city).put("lat", w.lat).put("lon", w.lon)
            if (w.tiles.isNotEmpty()) o.put("tiles", JSONArray().apply { w.tiles.forEach { put(JSONObject().put("t", it.title).put("u", it.url)) } })
            if (w.items.isNotEmpty()) o.put("items", JSONArray().apply { w.items.forEach { put(JSONObject().put("t", it.text).put("d", it.done)) } })
            a.put(o)
        }
        sp.edit().putString("home.${d.profile}", JSONObject().put("widgets", a).toString()).apply()
    }

    fun addShortcut(profile: String, title: String, url: String) {
        val d = data(profile)
        val w = d.widgets.firstOrNull { it.type == "shortcuts" } ?: Widget("shortcuts").also { d.widgets += it }
        w.tiles += Tile(title.take(40), url)
        save(d)
    }
}
