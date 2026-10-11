package app.whatsweb

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import app.whatsweb.ui.theme.WhatswebTheme
import app.whatsweb.web.LinkRouter
import app.whatsweb.web.MediaStoreSaver
import app.whatsweb.web.WebHost
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { UserPreferences(this) }
    private val state = AppState()
    private val webSlot = WebSlot()
    private var locked by mutableStateOf(false)
    private var lastLeaveAttempt = 0L
    private val io = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())

    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var permissionsOpen = true
    private val permissionQueue = ArrayDeque<PermissionWait>()

    private val lockObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (prefs.appLock && prefs.welcomeDone) locked = true
        }
    }

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
        if (!permissionsOpen) return@registerForActivityResult
        val wait = permissionQueue.pollFirst() ?: return@registerForActivityResult
        if (granted.isNotEmpty() && granted.values.all { it }) wait.onGranted() else wait.onDenied()
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
            requestLegacyStorage = ::requestLegacyStorage,
            message = { text -> state.notice = text },
            onRendererGone = webSlot::destroy,
        )
        consumeIntent(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockObserver)
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
                        app.whatsweb.ui.browser.clearCompanionSession(webSlot.view)
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

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lockObserver)
        permissionsOpen = false
        val pending = permissionQueue.toList()
        permissionQueue.clear()
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        webSlot.destroyCurrent()
        pending.forEach { wait ->
            try {
                wait.onDenied()
            } catch (_: Exception) {
            }
        }
        io.shutdown()
        super.onDestroy()
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
        requestPermissions(
            needed.toTypedArray(),
            onGranted = { request.grant(grant) },
            onDenied = { request.deny() },
        )
    }

    private fun requestLegacyStorage(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (!needsLegacyStoragePermission()) {
            onGranted()
            return
        }
        requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            onGranted,
            onDenied,
        )
    }

    private fun needsLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions(
        permissions: Array<String>,
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        if (!permissionsOpen) {
            onDenied()
            return
        }
        val wait = PermissionWait(onGranted, onDenied)
        permissionQueue.addLast(wait)
        try {
            runtimePermissions.launch(permissions)
        } catch (_: Exception) {
            permissionQueue.remove(wait)
            onDenied()
        }
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
                    when (val route = LinkRouter.route(data, external = true)) {
                        is LinkRouter.Route.Translate -> state.pendingUrl = route.url
                        LinkRouter.Route.Stay -> state.pendingUrl = data
                        else -> Unit
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
        val appContext = applicationContext
        val save = {
            try {
                io.execute { saveShared(appContext, uri) }
            } catch (_: RejectedExecutionException) {
            }
        }
        requestLegacyStorage(onGranted = save, onDenied = save)
    }

    private fun saveShared(context: Context, uri: Uri) {
        val name = displayName(context, uri)
        val mime = try {
            context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (_: Exception) {
            "application/octet-stream"
        }
        val result = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                MediaStoreSaver.save(context, name, mime, input, MAX_SHARE_BYTES)
            } ?: MediaStoreSaver.Result.Failed
        } catch (_: Exception) {
            MediaStoreSaver.Result.Failed
        }
        val message = context.getString(
            when (result) {
                MediaStoreSaver.Result.PublicDownloads -> R.string.share_saved
                MediaStoreSaver.Result.AppFolder -> R.string.share_saved_private
                MediaStoreSaver.Result.TooLarge -> R.string.download_too_large
                MediaStoreSaver.Result.Failed -> R.string.share_failed
            },
        )
        mainThread.post {
            if (!isDestroyed) state.notice = message
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return it.getString(index) ?: "shared"
                }
            }
            "shared"
        } catch (_: Exception) {
            "shared"
        }
    }

    private data class PermissionWait(
        val onGranted: () -> Unit,
        val onDenied: () -> Unit,
    )

    private companion object {
        const val MAX_SHARE_BYTES = 60L * 1024 * 1024
    }
}
