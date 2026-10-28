package app.aquabrowser.android

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/** A question or choice shown as an Aqua glass dialog. */
sealed class Dialog {
    /** A message with up to two buttons. onResult(true) for the main button. */
    class Message(val title: String, val message: String, val ok: String = "OK", val cancel: String? = null, val onResult: (Boolean) -> Unit) : Dialog()

    /** Asks for one line of text (or a password). onResult(null) when cancelled. */
    class Input(val title: String, val message: String?, val initial: String, val password: Boolean = false, val ok: String = "OK", val onResult: (String?) -> Unit) : Dialog()

    /** Asks for a user name and password (a site's sign-in box). */
    class SignIn(val title: String, val message: String?, val onResult: (Pair<String, String>?) -> Unit) : Dialog()

    /** A list to pick from: a page's drop-down, saved logins, a link's menu. */
    class Pick(val title: String?, val items: List<String>, val subtitles: List<String>? = null, val selected: Set<Int> = emptySet(), val multiple: Boolean = false, val onResult: (List<Int>?) -> Unit) : Dialog()

    /** Fields to fill (adding or editing a password or card). */
    class Form(val title: String, val fields: List<Field>, val ok: String = "Save", val onResult: (List<String>?) -> Unit) : Dialog()
    data class Field(val label: String, val value: String, val password: Boolean = false, val number: Boolean = false)
}

class Toast(val message: String, val action: String?, val onAction: (() -> Unit)?, val stamp: Long = System.nanoTime())

object Ui {
    /** "tabs", "settings", "vault", or null for the browser. */
    var screen by mutableStateOf<String?>(null)
    var menu by mutableStateOf(false)
    var profileMenu by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var editText by mutableStateOf("")
    var homeEdit by mutableStateOf(false)
    var vaultUnlocked by mutableStateOf(false)
    val dialogs = mutableStateListOf<Dialog>()
    var toast by mutableStateOf<Toast?>(null)
    var activity: WeakReference<MainActivity>? = null

    fun show(d: Dialog) = Aqua.onMain { dialogs += d }
    fun close(d: Dialog) = Aqua.onMain { dialogs.remove(d) }
    fun toast(message: String, action: String? = null, onAction: (() -> Unit)? = null) = Aqua.onMain { toast = Toast(message, action, onAction) }

    fun open(screenName: String?) {
        menu = false; profileMenu = false; editing = false
        if (screenName == "vault") vaultUnlocked = false
        screen = screenName
    }

    fun startEditing(text: String) { editText = text; editing = true; menu = false }

    fun pickFiles(mimeTypes: Array<String>?, multiple: Boolean, done: (List<Uri>) -> Unit) {
        val a = activity?.get()
        if (a == null) done(emptyList()) else a.pickFiles(mimeTypes, multiple, done)
    }

    fun requestPermissions(perms: Array<String>, done: (Boolean) -> Unit) {
        val a = activity?.get()
        if (a == null) done(false) else a.requestPerms(perms, done)
    }

    /** Fingerprint, face or screen lock before showing saved passwords (skipped if the device has none set up). */
    fun unlock(reason: String, done: (Boolean) -> Unit) {
        if (!Aqua.settings.lockVault.value) { done(true); return }
        val a = activity?.get()
        if (a == null) done(false) else a.unlock(reason, done)
    }
}
