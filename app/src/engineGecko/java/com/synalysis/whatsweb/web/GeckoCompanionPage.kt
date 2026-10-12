package com.synalysis.whatsweb.web

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.synalysis.whatsweb.ui.AppState
import com.synalysis.whatsweb.ui.WebSlot
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

private const val FIREFOX_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:149.0) Gecko/20100101 Firefox/149.0"

private const val EXTENSION_URI = "resource://android/assets/page/"
private const val EXTENSION_ID = "whatsweb@synalysis.com"
private const val NATIVE_APP = "whatsweb"

fun createCompanionPage(
    context: Context,
    state: AppState,
    host: WebHost,
    slot: WebSlot,
    phoneLayout: () -> Boolean,
    quietFrame: () -> Boolean,
    hideAi: () -> Boolean,
    textZoom: Int,
): CompanionPage {
    return GeckoCompanionPage(
        context = context,
        state = state,
        host = host,
        slot = slot,
        phoneLayout = phoneLayout,
        quietFrame = quietFrame,
        hideAi = hideAi,
        textZoom = textZoom,
    ).also { slot.bind(it) }
}

private class GeckoCompanionPage(
    context: Context,
    private val state: AppState,
    private val host: WebHost,
    private val slot: WebSlot,
    private val phoneLayout: () -> Boolean,
    private val quietFrame: () -> Boolean,
    private val hideAi: () -> Boolean,
    textZoom: Int,
) : CompanionPage {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val sessionBridge = SessionBridge(state)
    private val runtime = runtime(app)
    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .userAgentOverride(FIREFOX_DESKTOP)
            .suspendMediaWhenInactive(false)
            .build(),
    )
    private val geckoView = GeckoView(context)
    private var extensionReady = false
    private var pendingUrl: String? = null
    private var port: WebExtension.Port? = null
    private var nextId = 1
    private val pending = HashMap<Int, (String) -> Unit>()
    private var currentUrl: String? = null
    private var expectingScreen = false

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            when {
                json.optBoolean("ready") -> main.post { pushLayout(force = true) }
                json.has("host") -> {
                    val parsed = HostMessages.parse(json.optString("host")) ?: return
                    main.post { sessionBridge.onMessage(parsed) }
                }
                json.has("result") -> {
                    val callback = pending.remove(json.optInt("id")) ?: return
                    val result = json.optString("result", "null")
                    main.post { callback(result) }
                }
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            main.post {
                if (this@GeckoCompanionPage.port === port) this@GeckoCompanionPage.port = null
                val waiting = pending.values.toList()
                pending.clear()
                waiting.forEach { it("null") }
            }
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            port.setDelegate(portDelegate)
            main.post { this@GeckoCompanionPage.port = port }
        }
    }

    init {
        geckoView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // SurfaceView punches through Compose and stays blank. TextureView draws
        // in the view hierarchy, so the page is actually visible.
        geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
        setTextZoom(textZoom)
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                currentUrl = url
                if (LinkRouter.isCompanionPage(url)) {
                    state.mainFrameError = false
                    allowStoredNotifications()
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                state.progress = progress
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                state.progress = if (success) 0 else state.progress
                if (!success && LinkRouter.isCompanionPage(currentUrl)) state.mainFrameError = true
                if (success) main.post { pushLayout() }
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                state.pageTitle = title
            }

            override fun onCrash(session: GeckoSession) {
                state.rendererGone = true
            }
        }
        session.mediaDelegate = object : GeckoSession.MediaDelegate {
            override fun onRecordingStatusChanged(
                session: GeckoSession,
                devices: Array<GeckoSession.MediaDelegate.RecordingDevice>,
            ) {
                val microphone = devices.any {
                    it.type == GeckoSession.MediaDelegate.RecordingDevice.Type.MICROPHONE &&
                        it.status == GeckoSession.MediaDelegate.RecordingDevice.Status.RECORDING
                }
                val audio = app.getSystemService(AudioManager::class.java) ?: return
                audio.mode = if (microphone) {
                    AudioManager.MODE_IN_COMMUNICATION
                } else if (audio.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    AudioManager.MODE_NORMAL
                } else {
                    audio.mode
                }
            }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
                    if (!LinkRouter.isTrustedCompanionOrigin(perm.uri)) {
                        return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    }
                    val result = GeckoResult<Int>()
                    main.post {
                        host.requestNotifications(
                            { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) },
                            { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) },
                        )
                    }
                    return result
                }
                val decision = if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                    perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE
                ) {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                } else {
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                }
                return GeckoResult.fromValue(decision)
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                if (!LinkRouter.isTrustedCompanionOrigin(uri)) {
                    callback.reject()
                    return
                }
                val chosenVideo = chooseVideo(video)
                val chosenAudio = chooseAudio(audio, screen = chosenVideo?.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_SCREEN)
                main.post {
                    val grant = {
                        expectingScreen = chosenVideo?.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_SCREEN ||
                            chosenAudio?.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE
                        callback.grant(chosenVideo, chosenAudio)
                    }
                    val camera = chosenVideo?.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA
                    val microphone = chosenAudio?.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
                    if (!camera && !microphone) {
                        grant()
                        return@post
                    }
                    host.requestCapture(camera, microphone, grant, { callback.reject() })
                }
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                currentUrl = url
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                return when (val route = LinkRouter.route(request.uri, request.isRedirect)) {
                    LinkRouter.Route.Stay -> GeckoResult.allow()
                    is LinkRouter.Route.Translate -> {
                        session.loadUri(route.url)
                        GeckoResult.deny()
                    }
                    is LinkRouter.Route.External -> {
                        host.openExternal(route.url)
                        GeckoResult.deny()
                    }
                    LinkRouter.Route.Recover -> {
                        session.loadUri(LinkRouter.HOME)
                        GeckoResult.deny()
                    }
                    LinkRouter.Route.Drop -> GeckoResult.deny()
                }
            }
        }
        session.open(runtime)
        geckoView.setSession(session)
        runtime.activityDelegate = GeckoRuntime.ActivityDelegate { pending ->
            val result = GeckoResult<Intent>()
            main.post {
                host.startForResult(
                    pending,
                    { data ->
                        if (expectingScreen) {
                            expectingScreen = false
                            ScreenShareService.start(
                                app,
                                { result.complete(data) },
                                { result.completeExceptionally(IllegalStateException("screen share unavailable")) },
                            )
                        } else {
                            result.complete(data)
                        }
                    },
                    {
                        expectingScreen = false
                        result.completeExceptionally(IllegalStateException("cancelled"))
                    },
                )
            }
            result
        }
        runtime.webExtensionController.ensureBuiltIn(EXTENSION_URI, EXTENSION_ID).accept(
            { extension ->
                if (extension != null) {
                    session.webExtensionController.setMessageDelegate(extension, messageDelegate, NATIVE_APP)
                }
                extensionReady = true
                pendingUrl?.let { url ->
                    pendingUrl = null
                    session.loadUri(url)
                }
            },
            { error ->
                Log.e("WhatsWeb", "Firefox extension failed", error)
                extensionReady = true
                pendingUrl?.let { url ->
                    pendingUrl = null
                    session.loadUri(url)
                }
            },
        )
    }

    override val view: View = geckoView

    override fun loadUrl(url: String) {
        state.mainFrameError = false
        if (!extensionReady) {
            pendingUrl = url
            return
        }
        session.loadUri(url)
    }

    override fun reload() {
        state.mainFrameError = false
        session.reload()
    }

    override fun runScript(script: String, onResult: ((String) -> Unit)?) {
        val op = when (script) {
            PageCommands.setPhone(true) -> "setPhone" to true
            PageCommands.setPhone(false) -> "setPhone" to false
            PageCommands.setHideAi(true) -> "setHideAi" to true
            PageCommands.setHideAi(false) -> "setHideAi" to false
            PageCommands.BACK -> "back" to null
            else -> {
                onResult?.invoke("null")
                return
            }
        }
        val current = port
        if (current == null) {
            onResult?.invoke("null")
            return
        }
        val id = nextId++
        if (onResult != null) pending[id] = onResult
        val message = JSONObject().put("op", op.first).put("id", id)
        if (op.second != null) message.put("value", op.second)
        current.postMessage(message)
    }

    override fun goBack(onResult: (Boolean) -> Unit) {
        runScript(PageCommands.BACK) { raw -> onResult(raw == "true") }
    }

    override fun setTextZoom(zoom: Int) {
        runtime.settings.fontSizeFactor = zoom.coerceIn(80, 150) / 100f
    }

    override fun setBackgroundColor(color: Int) {
        geckoView.setBackgroundColor(color)
    }

    override fun resume() {
        session.setActive(true)
        geckoView.post { geckoView.requestLayout() }
    }

    override fun pause() {
        session.setActive(false)
    }

    override fun saveState(out: Bundle) = Unit

    override fun restoreState(saved: Bundle): Boolean = false

    override fun clearData() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept {
            loadUrl(LinkRouter.HOME)
        }
    }

    override fun destroy() {
        expectingScreen = false
        runtime.activityDelegate = null
        ScreenShareService.stop(app)
        try {
            session.close()
        } catch (_: Exception) {
        }
        slot.destroy(null)
    }

    private fun chooseVideo(
        sources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
    ): GeckoSession.PermissionDelegate.MediaSource? {
        if (sources.isNullOrEmpty()) return null
        return sources.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_SCREEN }
            ?: sources.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_CAMERA }
            ?: sources.first()
    }

    private fun chooseAudio(
        sources: Array<GeckoSession.PermissionDelegate.MediaSource>?,
        screen: Boolean,
    ): GeckoSession.PermissionDelegate.MediaSource? {
        if (sources.isNullOrEmpty()) return null
        if (screen) {
            return sources.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_AUDIOCAPTURE }
                ?: sources.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
                ?: sources.first()
        }
        return sources.firstOrNull { it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
            ?: sources.first()
    }

    private fun allowStoredNotifications() {
        runtime.storageController.getPermissions(LinkRouter.HOME).accept({ permissions ->
            val denied = permissions.orEmpty().filter {
                it.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION &&
                    it.value != GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW &&
                    LinkRouter.isTrustedCompanionOrigin(it.uri)
            }
            if (denied.isEmpty()) return@accept
            main.post {
                host.requestNotifications(
                    {
                        denied.forEach {
                            runtime.storageController.setPermission(
                                it,
                                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
                            )
                        }
                    },
                    {},
                )
            }
        }, { })
    }

    private fun pushLayout(force: Boolean = false) {
        val current = port ?: return
        if (!force && !LinkRouter.isCompanionPage(currentUrl)) return
        current.postMessage(
            JSONObject()
                .put("op", "layout")
                .put("phone", phoneLayout())
                .put("quiet", quietFrame())
                .put("hideAi", hideAi())
                .put("hideCalls", false)
                .put("tabChats", app.getString(com.synalysis.whatsweb.R.string.tab_chats))
                .put("tabUpdates", app.getString(com.synalysis.whatsweb.R.string.tab_updates))
                .put("tabCalls", app.getString(com.synalysis.whatsweb.R.string.tab_calls)),
        )
    }
}

private var runtimeRef: GeckoRuntime? = null

private fun runtime(context: Context): GeckoRuntime {
    runtimeRef?.let { return it }
    val settings = GeckoRuntimeSettings.Builder()
        .javaScriptEnabled(true)
        .fissionEnabled(true)
        .consoleOutput(false)
        .build()
    return GeckoRuntime.create(context, settings).also { created ->
        val notifications = CompanionNotifications(context)
        NotificationBridge.notifications = notifications
        created.webNotificationDelegate = notifications
        runtimeRef = created
    }
}
