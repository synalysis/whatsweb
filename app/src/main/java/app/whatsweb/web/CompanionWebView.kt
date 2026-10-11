package app.whatsweb.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.whatsweb.ui.AppState

class WebHost(
    val openExternal: (String) -> Unit,
    val openFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    val requestWebPermissions: (PermissionRequest, Array<String>) -> Unit,
    val message: (String) -> Unit,
)

@SuppressLint("SetJavaScriptEnabled")
fun createCompanionWebView(
    context: Context,
    state: AppState,
    host: WebHost,
    phoneLayout: () -> Boolean,
    quietFrame: () -> Boolean,
    textZoom: Int,
): WebView {
    val chromeVersion = DesktopIdentity.chromeVersion(
        WebViewCompat.getCurrentWebViewPackage(context)?.versionName,
    )
    val identity = context.assets.open("desktop_identity.js").bufferedReader().use { it.readText() }
        .replace("__CHROME_VERSION__", chromeVersion)
    val layout = context.assets.open("phone_layout.js").bufferedReader().use { it.readText() }
    val downloads = DownloadHandler(context, host.message)
    val session = SessionBridge(state)

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
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
            allowFileAccess = true
            allowContentAccess = true
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
            userAgentString = DesktopIdentity.userAgent(chromeVersion)
            this.textZoom = textZoom
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(session, "WhatswebBridge")
        addJavascriptInterface(downloads, "WhatswebDownloads")
        downloads.webView = this

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                this,
                identity,
                setOf("https://web.whatsapp.com"),
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
                val allowed = setOf(
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                )
                val grant = request.resources.filter { it in allowed }.toTypedArray()
                if (grant.isEmpty()) {
                    request.deny()
                    return
                }
                if (grant.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
                    request.grant(grant)
                    return
                }
                host.requestWebPermissions(request, grant)
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
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        popupView: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = blockedTelemetry(request)

                    override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                        dispatchNavigation(view, request.url.toString(), host)
                        popupView.destroy()
                        return true
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (LinkRouter.isCompanionPage(url)) {
                    state.mainFrameError = false
                    view.evaluateJavascript(identity, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!LinkRouter.isCompanionPage(url)) return
                view.evaluateJavascript(
                    "window.__whatswebPhone = ${phoneLayout()};\nwindow.__whatswebQuiet = ${quietFrame()};\n$layout",
                    null,
                )
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = blockedTelemetry(request)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                return dispatchNavigation(view, request.url.toString(), host)
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
        }
    }
}

private fun blockedTelemetry(request: WebResourceRequest): WebResourceResponse? {
    if (!TelemetryBlock.blocks(request.url.toString())) return null
    val origin = request.requestHeaders["Origin"]
        ?: request.requestHeaders["origin"]
        ?: "https://web.whatsapp.com"
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
            host.message(view.context.getString(app.whatsweb.R.string.error_recovered))
            true
        }
        LinkRouter.Route.Drop -> true
    }
}
