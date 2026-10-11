package com.synalysis.whatsweb.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import org.json.JSONObject
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.synalysis.whatsweb.ui.AppState
import com.synalysis.whatsweb.ui.WebSlot
import java.io.ByteArrayInputStream

class WebHost(
    val openExternal: (String) -> Unit,
    val openFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    val requestWebPermissions: (PermissionRequest, Array<String>) -> Unit,
    val requestLegacyStorage: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
    val message: (String) -> Unit,
    val onRendererGone: (WebView) -> Unit,
)

private const val COMPANION_ORIGIN = "https://web.whatsapp.com"

@SuppressLint("SetJavaScriptEnabled")
fun createCompanionWebView(
    context: Context,
    state: AppState,
    host: WebHost,
    slot: WebSlot,
    phoneLayout: () -> Boolean,
    quietFrame: () -> Boolean,
    hideAi: () -> Boolean,
    textZoom: Int,
): WebView {
    val chromeVersion = DesktopIdentity.chromeVersion(
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName,
    )
    val identity = context.assets.open("desktop_identity.js").bufferedReader().use { it.readText() }
        .replace("__CHROME_VERSION__", chromeVersion)
    val layout = context.assets.open("phone_layout.js").bufferedReader().use { it.readText() }
    val downloads = DownloadHandler(context, host.message, host.requestLegacyStorage)
    val session = SessionBridge(state)
    val main = Handler(Looper.getMainLooper())

    return WebView(context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isFocusable = true
        isFocusableInTouchMode = true
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
            userAgentString = DesktopIdentity.userAgent(chromeVersion)
            this.textZoom = textZoom
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        downloads.webView = this
        slot.attach(this, downloads)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            addMessageListener("WhatswebHost") { payload, _ ->
                val message = HostMessages.parse(payload) ?: return@addMessageListener
                session.onMessage(message)
            }
            addMessageListener("WhatswebDownloads") { payload, reply ->
                downloads.onMessage(payload) { status ->
                    main.post {
                        try {
                            reply(status)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                this,
                identity,
                setOf(COMPANION_ORIGIN),
            )
        }

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.onDownloadStart(url, userAgent, contentDisposition, mimeType)
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                state.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                state.pageTitle = title
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                if (filePathCallback == null) return false
                host.openFileChooser(filePathCallback, fileChooserParams)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (!LinkRouter.isTrustedCompanionOrigin(request.origin?.toString())) {
                    request.deny()
                    return
                }
                val allowed = setOf(
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                )
                if (request.resources.isEmpty() || request.resources.any { it !in allowed }) {
                    request.deny()
                    return
                }
                host.requestWebPermissions(request, request.resources)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (!isUserGesture) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context)
                slot.trackPopup(popup)
                popup.settings.javaScriptEnabled = false
                popup.settings.allowFileAccess = false
                popup.settings.allowContentAccess = false
                var finished = false
                fun finish(url: String?) {
                    if (finished) return
                    finished = true
                    if (!url.isNullOrBlank() && url != "about:blank") {
                        if (LinkRouter.isCompanionPage(url)) view.loadUrl(url) else dispatchNavigation(view, url, host)
                    }
                    slot.closePopup(popup)
                }
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                        finish(request.url.toString())
                        return true
                    }

                    override fun onPageStarted(popupView: WebView, url: String?, favicon: Bitmap?) {
                        if (url != null && url != "about:blank") finish(url)
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                view.postDelayed({ finish(null) }, 10_000)
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (LinkRouter.isCompanionPage(url)) {
                    state.mainFrameError = false
                    state.rendererGone = false
                    view.evaluateJavascript(identity, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!LinkRouter.isCompanionPage(url)) return
                view.evaluateJavascript(
                    "window.__whatswebPhone = ${phoneLayout()};\nwindow.__whatswebQuiet = ${quietFrame()};\nwindow.__whatswebHideAi = ${hideAi()};\n${tabLabelScript(view.context)}\n$layout",
                    null,
                )
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = blockedTelemetry(request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!request.isForMainFrame) return LinkRouter.route(url) is LinkRouter.Route.Drop
                return dispatchNavigation(view, url, host)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame && LinkRouter.isCompanionPage(request.url.toString())) {
                    state.mainFrameError = true
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (
                    request.isForMainFrame &&
                    errorResponse.statusCode >= 400 &&
                    LinkRouter.isCompanionPage(request.url.toString())
                ) {
                    state.mainFrameError = true
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                state.rendererGone = true
                host.onRendererGone(view)
                return true
            }
        }
    }
}

private fun WebView.addMessageListener(
    name: String,
    onMessage: (payload: String, reply: (String) -> Unit) -> Unit,
) {
    WebViewCompat.addWebMessageListener(
        this,
        name,
        setOf(COMPANION_ORIGIN),
        object : WebViewCompat.WebMessageListener {
            override fun onPostMessage(
                view: WebView,
                message: androidx.webkit.WebMessageCompat,
                sourceOrigin: Uri,
                isMainFrame: Boolean,
                replyProxy: JavaScriptReplyProxy,
            ) {
                if (!isMainFrame || !LinkRouter.isTrustedCompanionOrigin(sourceOrigin.toString())) return
                val payload = message.data ?: return
                onMessage(payload) { status -> replyProxy.postMessage(status) }
            }
        },
    )
}

private fun blockedTelemetry(request: WebResourceRequest): WebResourceResponse? {
    if (!TelemetryBlock.blocks(request.url.toString())) return null
    val origin = request.requestHeaders["Origin"]
        ?: request.requestHeaders["origin"]
        ?: COMPANION_ORIGIN
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        200,
        "OK",
        mapOf(
            "Access-Control-Allow-Origin" to origin,
            "Access-Control-Allow-Credentials" to "true",
        ),
        ByteArrayInputStream(ByteArray(0)),
    )
}

private fun tabLabelScript(context: Context): String {
    fun quoted(id: Int) = JSONObject.quote(context.getString(id))
    return "window.__whatswebTabChats=${quoted(com.synalysis.whatsweb.R.string.tab_chats)};" +
        "window.__whatswebTabUpdates=${quoted(com.synalysis.whatsweb.R.string.tab_updates)};" +
        "window.__whatswebTabCalls=${quoted(com.synalysis.whatsweb.R.string.tab_calls)};"
}

private fun dispatchNavigation(view: WebView, url: String, host: WebHost): Boolean {
    return when (val route = LinkRouter.route(url)) {
        LinkRouter.Route.Stay -> false
        is LinkRouter.Route.Translate -> {
            view.loadUrl(route.url)
            true
        }
        is LinkRouter.Route.External -> {
            host.openExternal(route.url)
            true
        }
        LinkRouter.Route.Recover -> {
            view.loadUrl(LinkRouter.HOME)
            host.message(view.context.getString(com.synalysis.whatsweb.R.string.error_recovered))
            true
        }
        LinkRouter.Route.Drop -> true
    }
}
