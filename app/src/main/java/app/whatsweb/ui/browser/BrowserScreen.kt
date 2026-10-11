package app.whatsweb.ui.browser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.whatsweb.R
import app.whatsweb.prefs.UserPreferences
import app.whatsweb.ui.AppState
import app.whatsweb.ui.WebSlot
import app.whatsweb.web.LinkRouter
import app.whatsweb.web.WebHost
import app.whatsweb.web.createCompanionWebView
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    prefs: UserPreferences,
    state: AppState,
    host: WebHost,
    webSlot: WebSlot,
    savedState: Bundle?,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pageColor = MaterialTheme.colorScheme.background.toArgb()
    var webMissing by remember { mutableStateOf(false) }
    var hintDismissed by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.share_copied)

    LaunchedEffect(state.linked) {
        if (state.linked == true) hintDismissed = false
    }
    LaunchedEffect(state.pendingUrl) {
        val url = state.pendingUrl ?: return@LaunchedEffect
        state.pendingUrl = null
        webSlot.view?.loadUrl(url)
    }
    LaunchedEffect(prefs.phoneLayout) {
        webSlot.view?.evaluateJavascript(
            "window.whatswebSetPhone && window.whatswebSetPhone(${prefs.phoneLayout})",
            null,
        )
    }
    LaunchedEffect(prefs.hideAi) {
        webSlot.view?.evaluateJavascript(
            "window.whatswebSetHideAi && window.whatswebSetHideAi(${prefs.hideAi})",
            null,
        )
    }
    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        state.notice = null
        snackbarHostState.showSnackbar(message)
    }
    BackHandler {
        val view = webSlot.view
        if (view == null) {
            onLeave()
            return@BackHandler
        }
        view.evaluateJavascript("window.whatswebBack ? window.whatswebBack() : false") { raw ->
            if (raw != "true") onLeave()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.progress in 1..99) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.linked == false && !hintDismissed) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.link_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        TextButton(onClick = { hintDismissed = true }) {
                            Text(stringResource(R.string.link_hint_dismiss))
                        }
                    }
                }
            }
            if (state.sharedText != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.share_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.sharedText.orEmpty()))
                                state.sharedText = null
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            },
                        ) {
                            Text(stringResource(R.string.share_copy))
                        }
                        TextButton(onClick = { state.sharedText = null }) {
                            Text(stringResource(R.string.share_dismiss))
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (webMissing || state.rendererGone) {
                    MessagePane(
                        text = stringResource(
                            if (webMissing) R.string.error_webview_missing else R.string.error_page_stopped,
                        ),
                        action = if (state.rendererGone && !webMissing) {
                            stringResource(R.string.error_retry)
                        } else {
                            null
                        },
                        onAction = { state.rendererGone = false },
                    )
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val existing = webSlot.view
                            if (existing != null) {
                                (existing.parent as? ViewGroup)?.removeView(existing)
                                existing
                            } else {
                                try {
                                    createCompanionWebView(
                                        context = context,
                                        state = state,
                                        host = host,
                                        slot = webSlot,
                                        phoneLayout = { prefs.phoneLayout },
                                        quietFrame = { true },
                                        hideAi = { prefs.hideAi },
                                        textZoom = prefs.textZoom,
                                    ).also { created ->
                                        created.setBackgroundColor(pageColor)
                                        val restored = savedState?.let { created.restoreState(it) } != null
                                        val pending = state.pendingUrl
                                        state.pendingUrl = null
                                        when {
                                            pending != null -> created.loadUrl(pending)
                                            !restored -> created.loadUrl(LinkRouter.HOME)
                                        }
                                    }
                                } catch (_: Exception) {
                                    Handler(Looper.getMainLooper()).post {
                                        webMissing = true
                                    }
                                    android.view.View(context)
                                }
                            }
                        },
                        update = { view ->
                            (view as? WebView)?.settings?.textZoom = prefs.textZoom
                        },
                        onRelease = { released ->
                            if (released is WebView) webSlot.detach(released)
                        },
                    )
                    if (state.mainFrameError) {
                        MessagePane(
                            text = stringResource(R.string.error_offline),
                            action = stringResource(R.string.error_retry),
                            onAction = {
                                state.mainFrameError = false
                                webSlot.view?.loadUrl(LinkRouter.HOME)
                            },
                        )
                    }
                    if (prefs.phoneLayout && state.pane == "chat") {
                        IconButton(
                            onClick = {
                                webSlot.view?.evaluateJavascript("window.whatswebBack()", null)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.cd_back),
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 4.dp),
                ) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.cd_settings))
                }
            }
        }
    }
}

@Composable
private fun MessagePane(
    text: String,
    action: String?,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (action != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(action)
                }
            }
        }
    }
}

fun clearCompanionSession(view: WebView?) {
    val main = Handler(Looper.getMainLooper())
    CookieManager.getInstance().removeAllCookies {
        main.post {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            if (view == null) return@post
            try {
                view.clearCache(true)
                view.clearHistory()
                view.loadUrl(LinkRouter.HOME)
            } catch (_: Exception) {
            }
        }
    }
}
