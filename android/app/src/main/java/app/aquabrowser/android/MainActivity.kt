package app.aquabrowser.android

import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.aquabrowser.android.ui.AquaRoot
import java.lang.ref.WeakReference

class MainActivity : FragmentActivity() {
    private var fileDone: ((List<Uri>) -> Unit)? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uris = mutableListOf<Uri>()
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
            else data.data?.let { uris += it }
        }
        fileDone?.invoke(uris)
        fileDone = null
    }

    private var permDone: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        permDone?.invoke(res.values.all { it })
        permDone = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Aqua.start(this)
        Ui.activity = WeakReference(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!Aqua.browser.back()) moveTaskToBack(true) }
        })
        handle(intent)
        setContent { AquaRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        val text = when (i.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> i.getStringExtra(SearchManager.QUERY)
            else -> null
        }
        if (!text.isNullOrBlank()) Aqua.browser.openExternal(text)
        // Used by the automated screenshots.
        if (i.getBooleanExtra("aqua_welcomed", false)) Aqua.settings.welcomed.value = true
        i.getStringExtra("aqua_color")?.let { Aqua.profiles.setColor(Aqua.profiles.current.id, it) }
        i.getStringExtra("aqua_profile")?.let { Aqua.browser.switchProfile(it) }
        i.getStringExtra("aqua_screen")?.let { Ui.open(if (it.isBlank() || it == "none") null else it) }
        if (i.getBooleanExtra("aqua_unlock", false)) Ui.vaultUnlocked = true
    }

    fun pickFiles(mimeTypes: Array<String>?, multiple: Boolean, done: (List<Uri>) -> Unit) {
        fileDone = done
        val types = mimeTypes?.filter { it.contains('/') }?.distinct().orEmpty()
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (types.size == 1) types[0] else "*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        if (types.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        try { fileLauncher.launch(Intent.createChooser(intent, null)) } catch (e: Exception) { fileDone = null; done(emptyList()) }
    }

    fun requestPerms(perms: Array<String>, done: (Boolean) -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { done(true); return }
        permDone = done
        permLauncher.launch(missing.toTypedArray())
    }

    fun unlock(reason: String, done: (Boolean) -> Unit) {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) { done(true); return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { done(true) }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { done(false) }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(reason).setSubtitle("Aqua Browser").setAllowedAuthenticators(auth).build())
    }
}
