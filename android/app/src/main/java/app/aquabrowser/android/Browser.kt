package app.aquabrowser.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse

class Tab(val id: Long, val profileId: String, val private: Boolean) {
    var session: GeckoSession? = null
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    /** Showing the Aqua home page instead of a website. */
    var home by mutableStateOf(true)
    var desktop by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var savedState: String? = null
    var parentId: Long? = null
    var lastUsed: Long = System.currentTimeMillis()

    val host: String
        get() = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("").removePrefix("www.")

    val label: String
        get() = when {
            home -> "Home"
            title.isNotBlank() -> title
            host.isNotBlank() -> host
            else -> url
        }
}

private fun GeckoSession.active(on: Boolean) { if (isOpen) setActive(on) }

class Browser(private val ctx: Context) {
    val tabs = mutableStateListOf<Tab>()
    var currentId by mutableStateOf(-1L)
    var privateMode by mutableStateOf(false)
    private var nextId = 1L
    private val sp = Aqua.settings.sp

    val current: Tab? get() = tabs.firstOrNull { it.id == currentId }
    val profileId: String get() = Aqua.profiles.current.id

    private val saver = Runnable { persist() }
    fun persistSoon() { Aqua.main.removeCallbacks(saver); Aqua.main.postDelayed(saver, 900) }

    init {
        if (Aqua.settings.restoreTabs.value) restore()
        ensureTab()
    }

    /** The tabs shown in the tab switcher: this profile's, or the private ones. */
    fun visibleTabs(): List<Tab> = tabs.filter { if (privateMode) it.private else !it.private && it.profileId == profileId }

    private fun ensureTab() {
        val mine = visibleTabs()
        val pick = mine.maxByOrNull { it.lastUsed }
        if (pick == null) newTab(select = true) else select(pick)
    }

    fun newTab(url: String? = null, select: Boolean = true, private: Boolean = privateMode, parent: Tab? = null, profile: String = profileId): Tab {
        val t = Tab(nextId++, profile, private)
        t.parentId = parent?.id
        val at = parent?.let { p -> tabs.indexOf(p) + 1 } ?: tabs.size
        tabs.add(at.coerceIn(0, tabs.size), t)
        if (select) select(t)
        if (!url.isNullOrBlank()) load(t, url)
        persistSoon()
        return t
    }

    fun select(t: Tab) {
        current?.session?.takeIf { it.isOpen }?.setActive(false)
        currentId = t.id
        privateMode = t.private
        t.lastUsed = System.currentTimeMillis()
        // (a tab a page just opened has a session the engine opens itself, a moment later)
        if (!t.home) session(t).takeIf { it.isOpen }?.setActive(true)
        Ui.vaultUnlocked = false
    }

    fun close(t: Tab) {
        val idx = tabs.indexOf(t)
        if (idx < 0) return
        t.session?.close()
        t.session = null
        tabs.removeAt(idx)
        if (t.id == currentId) {
            val parent = tabs.firstOrNull { it.id == t.parentId && it.private == t.private }
            val mine = visibleTabs()
            val next = parent ?: mine.getOrNull((idx).coerceAtMost(mine.size - 1)) ?: mine.lastOrNull()
            if (next != null) select(next) else newTab(select = true)
        }
        if (t.private && tabs.none { it.private }) Ui.toast("Private tabs closed")
        persistSoon()
    }

    fun closeAllPrivate() {
        tabs.filter { it.private }.forEach { it.session?.close(); it.session = null }
        tabs.removeAll { it.private }
        privateMode = false
        ensureTab()
    }

    fun switchProfile(id: String) {
        Aqua.settings.profileId.value = id
        privateMode = false
        Ui.vaultUnlocked = false
        ensureTab()
        persistSoon()
    }

    fun enterPrivate() {
        privateMode = true
        val existing = tabs.filter { it.private }.maxByOrNull { it.lastUsed }
        if (existing != null) select(existing) else newTab(select = true, private = true)
    }

    /** Opens what was typed: an address, or a search. */
    fun load(t: Tab, input: String) {
        val url = Search.toUrl(input)
        if (url.isEmpty()) return
        t.home = false
        t.url = url
        val s = session(t)
        s.loadUri(url)
        s.active(t.id == currentId)
    }

    fun openExternal(input: String) {
        val t = current
        if (t != null && t.home && t.url.isEmpty() && !t.private) load(t, input) else newTab(input, select = true, private = false)
    }

    fun goHome(t: Tab) { t.home = true; t.session?.active(false) }

    /** Back: leave the home page for the page under it, go back a page, or close a tab opened by another. */
    fun back(): Boolean {
        val t = current ?: return false
        if (t.fullscreen) { t.session?.exitFullScreen(); return true }
        if (t.home && t.session != null && t.url.isNotEmpty()) { t.home = false; t.session?.active(true); return true }
        if (!t.home && t.canBack) { t.session?.goBack(); return true }
        if (t.parentId != null && tabs.any { it.id == t.parentId }) { close(t); return true }
        if (!t.home) { goHome(t); return true }
        return false
    }

    fun setDesktop(t: Tab, on: Boolean) {
        t.desktop = on
        t.session?.let {
            it.settings.userAgentMode = if (on) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            it.settings.viewportMode = if (on) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            it.reload()
        }
    }

    /** The tab's engine session, made (and its saved state restored) the first time it's needed. */
    fun session(t: Tab): GeckoSession {
        t.session?.let { return it }
        val s = makeSession(t)
        s.open(Aqua.runtime)
        val state = t.savedState?.let { GeckoSession.SessionState.fromString(it) }
        if (state != null) s.restoreState(state) else if (t.url.isNotEmpty()) s.loadUri(t.url)
        return s
    }

    private fun makeSession(t: Tab): GeckoSession {
        val b = GeckoSessionSettings.Builder()
            .usePrivateMode(t.private)
            .userAgentMode(if (t.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
        // Each profile keeps its own cookies and site data, like separate browsers.
        if (!t.private) b.contextId("aqua-" + t.profileId)
        val s = GeckoSession(b.build())
        attach(t, s)
        t.session = s
        return s
    }

    private fun attach(t: Tab, s: GeckoSession) {
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (url != null && url != "about:blank") { t.url = url; t.home = false }
                persistSoon()
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { t.canBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { t.canForward = canGoForward }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val nt = Tab(nextId++, t.profileId, t.private)
                nt.parentId = t.id
                nt.home = false
                nt.url = uri
                tabs.add((tabs.indexOf(t) + 1).coerceIn(0, tabs.size), nt)
                val ns = makeSession(nt)
                select(nt)
                return GeckoResult.fromValue(ns)
            }
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val sch = uri.substringBefore(':').lowercase()
                if (sch in setOf("http", "https", "about", "data", "blob", "file", "javascript", "moz-extension", "resource", "view-source")) return null
                return if (openOutside(uri)) GeckoResult.deny() else null
            }
        }
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) { t.loading = true; t.progress = 6 }
            override fun onPageStop(session: GeckoSession, success: Boolean) { t.loading = false; t.progress = 100 }
            override fun onProgressChange(session: GeckoSession, progress: Int) { t.progress = progress }
            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) { t.secure = securityInfo.isSecure }
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                if (!t.private) { t.savedState = sessionState.toString(); persistSoon() }
            }
        }
        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                t.title = title ?: ""
                if (!t.private) Aqua.history.title(t.profileId, t.url, t.title)
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) { t.fullscreen = fullScreen }
            override fun onCloseRequest(session: GeckoSession) { close(t) }
            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) { linkMenu(t, element) }
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) { Downloads.save(ctx, response) }
            override fun onCrash(session: GeckoSession) { recover(t) }
            override fun onKill(session: GeckoSession) { recover(t) }
        }
        s.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean>? {
                if (t.private || (flags and GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL) == 0) return GeckoResult.fromValue(false)
                Aqua.history.visit(t.profileId, url, t.title)
                return GeckoResult.fromValue(true)
            }
        }
        s.promptDelegate = Prompts(t)
        s.permissionDelegate = Permissions(t)
    }

    private fun recover(t: Tab) {
        t.session?.close()
        t.session = null
        t.loading = false
        if (t.id == currentId && !t.home) Ui.toast("The page stopped working, so it was reloaded")
        if (!t.home) session(t)
    }

    private fun linkMenu(t: Tab, e: GeckoSession.ContentDelegate.ContextElement) {
        val link = e.linkUri
        val src = e.srcUri
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (link != null) {
            items += "Open in new tab" to { newTab(link, select = false, private = t.private, parent = t); Ui.toast("Opened in a new tab", "Show") { tabs.lastOrNull { it.url == link }?.let { select(it) } } }
            if (!t.private) items += "Open in private tab" to { newTab(link, select = true, private = true); Unit }
            items += "Copy link" to { copy(link) }
            items += "Share link" to { share(link) }
            items += "Add to home page" to { Aqua.home.addShortcut(t.profileId, e.title ?: e.textContent ?: Uri.parse(link).host ?: link, link); Ui.toast("Added to your home page") }
        }
        if (src != null && e.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
            items += "Open image in new tab" to { newTab(src, select = true, private = t.private, parent = t); Unit }
            items += "Copy image address" to { copy(src) }
        }
        if (items.isEmpty()) return
        Ui.show(Dialog.Pick(link ?: src, items.map { it.first }) { picked -> picked?.firstOrNull()?.let { items[it].second() } })
    }

    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Link", text))
        Ui.toast("Copied")
    }

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Links meant for other apps (mail, phone, maps, app store, intent://). */
    private fun openOutside(uri: String): Boolean {
        return try {
            val intent = if (uri.startsWith("intent:")) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Ui.toast("No app can open this link"); true
        } catch (e: Exception) {
            false
        }
    }

    /* ---------- tabs kept between launches ---------- */

    private fun persist() {
        val a = JSONArray()
        tabs.filter { !it.private }.forEach { t ->
            a.put(JSONObject().put("p", t.profileId).put("url", t.url).put("title", t.title).put("home", t.home).put("desk", t.desktop)
                .put("used", t.lastUsed).put("state", if ((t.savedState?.length ?: 0) < 200_000) t.savedState else null))
        }
        sp.edit().putString("tabs", a.toString()).apply()
    }

    private fun restore() {
        val raw = sp.getString("tabs", null) ?: return
        runCatching {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val p = o.getString("p")
                if (Aqua.profiles.byId(p) == null) continue
                val t = Tab(nextId++, p, false)
                t.url = o.optString("url")
                t.title = o.optString("title")
                t.home = o.optBoolean("home", true) || t.url.isEmpty()
                t.desktop = o.optBoolean("desk", false)
                t.lastUsed = o.optLong("used", 0L)
                t.savedState = o.optString("state").takeIf { it.isNotEmpty() && it != "null" }
                tabs += t
            }
        }
    }
}
