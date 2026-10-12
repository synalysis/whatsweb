package com.synalysis.whatsweb.web

import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import com.synalysis.whatsweb.ui.AppState
import com.synalysis.whatsweb.ui.WebSlot
import com.synalysis.whatsweb.ui.browser.clearWebViewSession

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
    val web = createCompanionWebView(
        context = context,
        state = state,
        host = host,
        slot = slot,
        phoneLayout = phoneLayout,
        quietFrame = quietFrame,
        hideAi = hideAi,
        textZoom = textZoom,
    )
    return WebViewCompanionPage(web, slot).also { slot.bind(it) }
}

private class WebViewCompanionPage(
    private val web: WebView,
    private val slot: WebSlot,
) : CompanionPage {
    override val view: View = web

    override fun loadUrl(url: String) {
        web.loadUrl(url)
    }

    override fun reload() {
        web.reload()
    }

    override fun runScript(script: String, onResult: ((String) -> Unit)?) {
        web.evaluateJavascript(script, onResult)
    }

    override fun goBack(onResult: (Boolean) -> Unit) {
        web.evaluateJavascript(PageCommands.BACK) { raw ->
            onResult(raw == "true")
        }
    }

    override fun setTextZoom(zoom: Int) {
        web.settings.textZoom = zoom
    }

    override fun setBackgroundColor(color: Int) {
        web.setBackgroundColor(color)
    }

    override fun resume() {
        web.onResume()
        web.resumeTimers()
        web.post { web.requestLayout() }
    }

    override fun pause() {
        web.onPause()
    }

    override fun saveState(out: Bundle) {
        web.saveState(out)
    }

    override fun restoreState(saved: Bundle): Boolean {
        return web.restoreState(saved) != null
    }

    override fun clearData() {
        clearWebViewSession(web)
    }

    override fun destroy() {
        slot.destroy(web)
    }
}
