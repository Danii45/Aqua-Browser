package app.aquabrowser.android

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.net.URLDecoder

/** A website's alerts, drop-downs, file uploads and sign-in boxes, and the password manager's prompts, as Aqua dialogs. */
class Prompts(private val tab: Tab) : PromptDelegate {
    private val site get() = tab.host.ifBlank { "This page" }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Message(prompt.title ?: site, prompt.message ?: "") { r.complete(prompt.dismiss()) })
        return r
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Message(prompt.title ?: site, prompt.message ?: "", "OK", "Cancel") { ok ->
            r.complete(prompt.confirm(if (ok) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE))
        })
        return r
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Input(prompt.title ?: site, prompt.message, prompt.defaultValue ?: "") { text ->
            r.complete(if (text == null) prompt.dismiss() else prompt.confirm(text))
        })
        return r
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.SignIn(prompt.title ?: "Sign in to $site", prompt.message) { creds ->
            r.complete(if (creds == null) prompt.dismiss() else prompt.confirm(creds.first, creds.second))
        })
        return r
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        val flat = mutableListOf<PromptDelegate.ChoicePrompt.Choice>()
        fun walk(list: Array<PromptDelegate.ChoicePrompt.Choice>?) {
            list?.forEach { ch -> if (ch.items != null) walk(ch.items) else if (!ch.separator && !ch.disabled) flat += ch }
        }
        walk(prompt.choices)
        val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Pick(prompt.title, flat.map { it.label }, null, flat.indices.filter { flat[it].selected }.toSet(), multiple) { picked ->
            r.complete(
                when {
                    picked == null -> prompt.dismiss()
                    multiple -> prompt.confirm(picked.map { flat[it] }.toTypedArray())
                    picked.isEmpty() -> prompt.dismiss()
                    else -> prompt.confirm(flat[picked[0]])
                }
            )
        })
        return r
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
        Ui.pickFiles(prompt.mimeTypes, multiple) { uris ->
            r.complete(
                when {
                    uris.isEmpty() -> prompt.dismiss()
                    multiple -> prompt.confirm(Aqua.app, uris.toTypedArray())
                    else -> prompt.confirm(Aqua.app, uris[0])
                }
            )
        }
        return r
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse>? {
        val r = GeckoResult<PromptResponse>()
        val hint = when (prompt.type) {
            PromptDelegate.DateTimePrompt.Type.DATE -> "Date, like 2026-10-28"
            PromptDelegate.DateTimePrompt.Type.TIME -> "Time, like 14:30"
            PromptDelegate.DateTimePrompt.Type.MONTH -> "Month, like 2026-10"
            PromptDelegate.DateTimePrompt.Type.WEEK -> "Week, like 2026-W44"
            else -> "Date and time, like 2026-10-28T14:30"
        }
        Ui.show(Dialog.Input(prompt.title ?: "Pick a date", hint, prompt.defaultValue ?: "") { text ->
            r.complete(if (text == null) prompt.dismiss() else prompt.confirm(text.trim()))
        })
        return r
    }

    /* ---------- the password and card manager ---------- */
    override fun onLoginSave(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>): GeckoResult<PromptResponse>? {
        val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
        if (tab.private || !Aqua.settings.offerSave.value) return GeckoResult.fromValue(request.dismiss())
        val login = option.value
        val existing = Aqua.vault.find(login)
        val r = GeckoResult<PromptResponse>()
        val what = if (existing != null) "Update the saved password" else "Save the password"
        Ui.show(Dialog.Message("$what for $site?", "User name: ${login.username.ifBlank { "(none)" }}\nKept in Aqua Browser on this phone, encrypted.", if (existing != null) "Update" else "Save", "Not now") { ok ->
            r.complete(if (ok) request.confirm(option) else request.dismiss())
            if (ok) Ui.toast("Password saved")
        })
        return r
    }

    override fun onLoginSelect(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>): GeckoResult<PromptResponse>? {
        val options = request.options
        if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Pick("Sign in to $site", options.map { it.value.username.ifBlank { "(no user name)" } }, options.map { "Saved password" }) { picked ->
            val i = picked?.firstOrNull()
            if (i == null) r.complete(request.dismiss())
            else Ui.unlock("Fill your saved password") { ok -> r.complete(if (ok) request.confirm(options[i]) else request.dismiss()) }
        })
        return r
    }

    override fun onCreditCardSave(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<Autocomplete.CreditCardSaveOption>): GeckoResult<PromptResponse>? {
        val option = request.options.firstOrNull() ?: return GeckoResult.fromValue(request.dismiss())
        if (tab.private) return GeckoResult.fromValue(request.dismiss())
        val card = option.value
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Message("Save this card?", "Card ending ${card.number.takeLast(4)}, ${card.name}\nKept in Aqua Browser on this phone, encrypted. The security code is never saved.", "Save", "Not now") { ok ->
            r.complete(if (ok) request.confirm(option) else request.dismiss())
        })
        return r
    }

    override fun onCreditCardSelect(session: GeckoSession, request: PromptDelegate.AutocompleteRequest<Autocomplete.CreditCardSelectOption>): GeckoResult<PromptResponse>? {
        val options = request.options
        if (options.isEmpty()) return GeckoResult.fromValue(request.dismiss())
        val r = GeckoResult<PromptResponse>()
        Ui.show(Dialog.Pick("Fill a card", options.map { "•••• " + it.value.number.takeLast(4) }, options.map { it.value.name + "  " + it.value.expirationMonth + "/" + it.value.expirationYear }) { picked ->
            val i = picked?.firstOrNull()
            if (i == null) r.complete(request.dismiss())
            else Ui.unlock("Fill your saved card") { ok -> r.complete(if (ok) request.confirm(options[i]) else request.dismiss()) }
        })
        return r
    }
}

/** Location, camera, microphone and notifications: Aqua asks first, then Android. */
class Permissions(private val tab: Tab) : GeckoSession.PermissionDelegate {
    private val site get() = tab.host.ifBlank { "This page" }

    override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<out String>?, callback: GeckoSession.PermissionDelegate.Callback) {
        if (permissions.isNullOrEmpty()) { callback.grant(); return }
        Ui.requestPermissions(permissions.map { it }.toTypedArray()) { ok -> if (ok) callback.grant() else callback.reject() }
    }

    override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int>? {
        val what = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "send you notifications"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store data on this phone"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
            else -> return null
        }
        val r = GeckoResult<Int>()
        Ui.show(Dialog.Message("Allow $site to $what?", "You can change this later by clearing the site's data.", "Allow", "Block") { ok ->
            r.complete(if (ok) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
        })
        return r
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession, uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        val v = video?.firstOrNull()
        val a = audio?.firstOrNull()
        val what = listOfNotNull(if (v != null) "camera" else null, if (a != null) "microphone" else null).joinToString(" and ")
        Ui.show(Dialog.Message("Allow $site to use your $what?", "Only while this page is open.", "Allow", "Block") { ok ->
            if (!ok) callback.reject()
            else {
                val need = listOfNotNull(if (v != null) Manifest.permission.CAMERA else null, if (a != null) Manifest.permission.RECORD_AUDIO else null)
                Ui.requestPermissions(need.toTypedArray()) { granted -> if (granted) callback.grant(v, a) else callback.reject() }
            }
        })
    }
}

/** Files a page hands over for download, saved to the phone's Downloads. */
object Downloads {
    private fun header(r: WebResponse, name: String): String? = r.headers.entries.firstOrNull { it.key.equals(name, true) }?.value

    private fun fileName(r: WebResponse, mime: String): String {
        val cd = header(r, "Content-Disposition")
        var name = cd?.let {
            Regex("filename\\*=(?:UTF-8'')?\"?([^\";]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.let { n -> runCatching { URLDecoder.decode(n, "UTF-8") }.getOrDefault(n) }
                ?: Regex("filename=\"?([^\";]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)
        }
        if (name.isNullOrBlank()) name = Uri.parse(r.uri).lastPathSegment
        if (name.isNullOrBlank()) name = "download"
        name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
        var result: String = name ?: "download"
        if (!result.contains('.')) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { result += ".$it" }
        return result
    }

    fun save(ctx: Context, r: WebResponse) {
        val mime = header(r, "Content-Type")?.substringBefore(';')?.trim()?.ifBlank { null } ?: "application/octet-stream"
        val name = fileName(r, mime)
        Ui.toast("Downloading $name…")
        Thread {
            try {
                val body = r.body ?: throw IllegalStateException("The download was empty")
                val uri: Uri
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val res = ctx.contentResolver
                    uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Downloads isn't available")
                    res.openOutputStream(uri)?.use { out -> body.use { it.copyTo(out) } }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    res.update(uri, values, null, null)
                } else {
                    val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
                    val f = File(dir, name)
                    f.outputStream().use { out -> body.use { it.copyTo(out) } }
                    uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                }
                Ui.toast("Downloaded $name", "Open") {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { Ui.toast("No app can open $name") }
                }
            } catch (e: Exception) {
                Ui.toast("Download failed: ${e.message ?: "unknown problem"}")
            }
        }.start()
    }
}
