package app.aquabrowser.android

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.net.HttpURLConnection
import java.net.URL

/** Everything the app shares. Started once per process by MainActivity (never in GeckoView's own processes). */
object Aqua {
    lateinit var app: Context
    lateinit var settings: Settings
    lateinit var profiles: Profiles
    lateinit var runtime: GeckoRuntime
    lateinit var browser: Browser
    lateinit var vault: Vault
    lateinit var history: History
    lateinit var home: HomeStore
    private var started = false
    val main = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        settings = Settings(app)
        profiles = Profiles(settings)
        history = History(app)
        vault = Vault(app)
        home = HomeStore(settings.sp)
        runtime = Engine.create(app)
        browser = Browser(app)
    }

    fun onMain(f: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) f() else main.post(f) }

    val engineVersion: String by lazy {
        runCatching { Class.forName("org.mozilla.geckoview.BuildConfig").getField("MOZILLA_VERSION").get(null) as String }.getOrDefault("")
    }

    const val AQUA_MD = "https://danii45.github.io/Aqua-MD/"
    const val SITE = "https://danii45.github.io/Aqua-Browser/"
    const val SOURCE = "https://github.com/Danii45/Aqua-Browser"
}

/** GeckoView, Mozilla's engine, set up the way Aqua wants it. */
object Engine {
    fun antiTracking(): Int {
        var f = ContentBlocking.AntiTracking.DEFAULT
        if (Aqua.settings.blockAds.value) f = f or ContentBlocking.AntiTracking.AD
        if (Aqua.settings.strictTracking.value) f = f or ContentBlocking.AntiTracking.STRICT
        return f
    }

    fun create(ctx: Context): GeckoRuntime {
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(antiTracking())
            .enhancedTrackingProtectionLevel(if (Aqua.settings.strictTracking.value) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .loginAutofillEnabled(true)
            .aboutConfigEnabled(true)
            .consoleOutput(false)
            .build()
        val rt = GeckoRuntime.create(ctx, settings)
        rt.autocompleteStorageDelegate = VaultStorage
        return rt
    }

    fun applyBlocking() {
        val cb = Aqua.runtime.settings.contentBlocking
        cb.setAntiTracking(antiTracking())
        cb.setEnhancedTrackingProtectionLevel(if (Aqua.settings.strictTracking.value) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
    }
}

/** A setting kept in SharedPreferences that Compose watches. */
class Pref<T>(private val sp: SharedPreferences, private val key: String, default: T) {
    private val state = mutableStateOf(read(default))

    @Suppress("UNCHECKED_CAST")
    private fun read(d: T): T = when (d) {
        is Boolean -> sp.getBoolean(key, d) as T
        is Int -> sp.getInt(key, d) as T
        is Float -> sp.getFloat(key, d) as T
        is String -> (sp.getString(key, d) ?: d) as T
        else -> d
    }

    var value: T
        get() = state.value
        set(v) {
            state.value = v
            val e = sp.edit()
            when (v) {
                is Boolean -> e.putBoolean(key, v)
                is Int -> e.putInt(key, v)
                is Float -> e.putFloat(key, v)
                is String -> e.putString(key, v)
            }
            e.apply()
        }
}

class Settings(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("aqua", Context.MODE_PRIVATE)
    val glass = Pref(sp, "glass", 60)
    val addressDot = Pref(sp, "addressDot", true)
    val toolbarBottom = Pref(sp, "toolbarBottom", true)
    val autoCycle = Pref(sp, "autoCycle", 30)
    val autoSmooth = Pref(sp, "autoSmooth", false)
    val engine = Pref(sp, "engine", "Google")
    val suggestions = Pref(sp, "suggestions", true)
    val blockAds = Pref(sp, "blockAds", true)
    val strictTracking = Pref(sp, "strictTracking", true)
    val offerSave = Pref(sp, "offerSave", true)
    val lockVault = Pref(sp, "lockVault", true)
    val restoreTabs = Pref(sp, "restoreTabs", true)
    val welcomed = Pref(sp, "welcomed", false)
    val profileId = Pref(sp, "profile", "home")
    val profilesJson = Pref(sp, "profiles", "")
}

data class Profile(val id: String, val name: String, val color: String)

/** Home, Work and any others. Each has its own cookies and site data (a GeckoView context), tabs, home page and colour. */
class Profiles(private val s: Settings) {
    val list = mutableStateListOf<Profile>()

    init {
        val raw = s.profilesJson.value
        if (raw.isBlank()) {
            list += Profile("home", "Home", "blue")
            list += Profile("work", "Work", "teal")
            save()
        } else {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list += Profile(o.getString("id"), o.getString("name"), o.optString("color", "blue"))
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        s.profilesJson.value = a.toString()
    }

    val current: Profile get() = list.firstOrNull { it.id == s.profileId.value } ?: list.first()

    fun byId(id: String): Profile? = list.firstOrNull { it.id == id }

    fun setColor(id: String, color: String) {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) { list[i] = list[i].copy(color = color); save() }
    }

    fun add(name: String, color: String): Profile {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "profile" }
        var id = base
        var n = 2
        while (list.any { it.id == id }) id = base + "-" + n++
        val p = Profile(id, name.trim(), color)
        list += p
        save()
        return p
    }

    fun remove(id: String) {
        if (list.size <= 1) return
        list.removeAll { it.id == id }
        save()
        if (s.profileId.value == id) s.profileId.value = list.first().id
    }
}

object Search {
    data class SearchEngine(val name: String, val url: String, val suggest: String?)

    val engines = listOf(
        SearchEngine("Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
        SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"),
        SearchEngine("Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"),
        SearchEngine("Brave", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"),
        SearchEngine("Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?q=%s&type=list"),
        SearchEngine("Startpage", "https://www.startpage.com/do/search?q=%s", null),
    )

    fun engine(name: String = Aqua.settings.engine.value): SearchEngine = engines.firstOrNull { it.name == name } ?: engines[0]

    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val hostLike = Regex("^[^\\s/]+\\.[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$")
    private val ipLike = Regex("^(\\d{1,3}(\\.\\d{1,3}){3}|localhost)(:\\d+)?([/?#].*)?$")

    /** What was typed, as an address: a URL as is, a bare site with https://, anything else as a search. */
    fun toUrl(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (!t.contains(' ')) {
            if (scheme.containsMatchIn(t) && (t.contains("://") || t.startsWith("about:") || t.startsWith("data:") || t.startsWith("mailto:") || t.startsWith("view-source:"))) return t
            if (ipLike.matches(t)) return "http://$t"
            if (hostLike.matches(t)) return "https://$t"
        }
        return engine().url.replace("%s", Uri.encode(t))
    }

    suspend fun suggest(q: String): List<String> = withContext(Dispatchers.IO) {
        val e = engine()
        if (q.isBlank() || e.suggest == null || !Aqua.settings.suggestions.value) return@withContext emptyList()
        runCatching {
            val c = URL(e.suggest.replace("%s", Uri.encode(q))).openConnection() as HttpURLConnection
            c.connectTimeout = 2500; c.readTimeout = 2500
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AquaBrowser")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(text).getJSONArray(1)
            (0 until arr.length()).map { arr.getString(it) }.take(5)
        }.getOrDefault(emptyList())
    }
}
