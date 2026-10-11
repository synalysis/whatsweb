package app.whatsweb

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.whatsweb.prefs.ThemeMode
import app.whatsweb.prefs.UserPreferences
import app.whatsweb.ui.AppState
import app.whatsweb.ui.WebSlot
import app.whatsweb.ui.WhatswebApp
import app.whatsweb.ui.browser.clearCompanionSession
import app.whatsweb.ui.theme.WhatswebTheme
import app.whatsweb.web.LinkRouter
import app.whatsweb.web.MediaStoreSaver
import app.whatsweb.web.WebHost
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { UserPreferences(this) }
    private val state = AppState()
    private val webSlot = WebSlot()
    private var locked by mutableStateOf(false)
    private var lastLeaveAttempt = 0L

    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private var pendingGrant: Array<String>? = null

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        uploadCallback?.onReceiveValue(uris)
        uploadCallback = null
    }

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val request = pendingPermission
        val resources = pendingGrant
        pendingPermission = null
        pendingGrant = null
        if (request == null || resources == null) return@registerForActivityResult
        if (granted.values.all { it }) request.grant(resources) else request.deny()
    }

    private lateinit var host: WebHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        applyTheme(prefs.themeMode)
        locked = prefs.appLock && prefs.welcomeDone
        host = WebHost(
            openExternal = ::openExternal,
            openFileChooser = ::openFileChooser,
            requestWebPermissions = ::requestWebPermissions,
            message = { text -> state.notice = text },
        )
        consumeIntent(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (prefs.appLock && prefs.welcomeDone) locked = true
            }
        })
        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            WhatswebTheme(dynamicColor = prefs.dynamicColor) {
                WhatswebApp(
                    prefs = prefs,
                    state = state,
                    webSlot = webSlot,
                    host = host,
                    savedState = savedInstanceState,
                    locked = locked,
                    snackbarHostState = snackbarHostState,
                    onUnlock = ::promptUnlock,
                    onLeave = {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastLeaveAttempt < 2_000) {
                            finish()
                        } else {
                            lastLeaveAttempt = now
                            state.notice = getString(R.string.exit_again)
                        }
                    },
                    onTheme = ::changeTheme,
                    onClearSession = {
                        state.linked = null
                        state.pane = "list"
                        state.pageTitle = null
                        clearCompanionSession(webSlot.view)
                    },
                    canUseLock = ::canUseLock,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!locked) webSlot.view?.onResume()
    }

    override fun onPause() {
        webSlot.view?.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webSlot.view?.saveState(outState)
    }

    private fun changeTheme(mode: ThemeMode) {
        prefs.updateThemeMode(mode)
        applyTheme(mode)
    }

    private fun applyTheme(mode: ThemeMode) {
        val desired = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != desired) {
            AppCompatDelegate.setDefaultNightMode(desired)
        }
    }

    private fun canUseLock(): Boolean {
        return BiometricManager.from(this).canAuthenticate(lockAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptUnlock() {
        if (!canUseLock()) {
            prefs.updateAppLock(false)
            locked = false
            state.notice = getString(R.string.lock_unavailable)
            return
        }
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(lockAuthenticators())
        if (Build.VERSION.SDK_INT < 30) {
            prompt.setNegativeButtonText(getString(R.string.cancel))
        }
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                    webSlot.view?.onResume()
                }
            },
        ).authenticate(prompt.build())
    }

    private fun lockAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
    ) {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = callback
        val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            fileChooser.launch(intent)
        } catch (_: ActivityNotFoundException) {
            uploadCallback?.onReceiveValue(null)
            uploadCallback = null
        }
    }

    private fun requestWebPermissions(request: PermissionRequest, grant: Array<String>) {
        val needed = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in grant) add(Manifest.permission.CAMERA)
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in grant) add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            request.grant(grant)
            return
        }
        pendingPermission?.deny()
        pendingPermission = request
        pendingGrant = grant
        runtimePermissions.launch(needed.toTypedArray())
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            state.notice = getString(R.string.no_browser)
        }
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.dataString
                if (data != null) {
                    state.pendingUrl = when (val route = LinkRouter.route(data)) {
                        is LinkRouter.Route.Translate -> route.url
                        LinkRouter.Route.Stay -> data
                        else -> state.pendingUrl
                    }
                }
            }
            Intent.ACTION_SEND -> consumeShare(intent)
        }
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    private fun consumeShare(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
            state.sharedText = it
        }
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        val name = displayName(uri)
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SHARE_BYTES) {
                        state.notice = getString(R.string.download_too_large)
                        return
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
        if (bytes == null) {
            state.notice = getString(R.string.share_failed)
            return
        }
        val result = MediaStoreSaver.save(this, name, mime, bytes)
        state.notice = getString(
            when (result) {
                MediaStoreSaver.Result.PublicDownloads -> R.string.share_saved
                MediaStoreSaver.Result.AppFolder -> R.string.share_saved_private
                MediaStoreSaver.Result.Failed -> R.string.share_failed
            },
        )
    }

    private fun displayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index) ?: "shared"
            }
        }
        return "shared"
    }

    private companion object {
        const val MAX_SHARE_BYTES = 60 * 1024 * 1024
    }
}
