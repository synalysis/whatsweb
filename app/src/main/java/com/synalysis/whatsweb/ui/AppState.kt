package com.synalysis.whatsweb.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.synalysis.whatsweb.web.CompanionPage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.synalysis.whatsweb.web.DownloadHandler
import java.util.Collections
import java.util.WeakHashMap

class AppState {
    var pane by mutableStateOf("list")
    var infoOpen by mutableStateOf(false)
    var linked by mutableStateOf<Boolean?>(null)
    var onCall by mutableStateOf(false)
    var selfPhone by mutableStateOf<String?>(null)
    var progress by mutableIntStateOf(0)
    var pageTitle by mutableStateOf<String?>(null)
    var mainFrameError by mutableStateOf(false)
    var rendererGone by mutableStateOf(false)
    var sharedText by mutableStateOf<String?>(null)
    var pendingUrl by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
}

class WebSlot {
    var page: CompanionPage? = null
        private set
    var view: WebView? = null
        private set
    private var downloads: DownloadHandler? = null
    private val destroyed = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val popups = LinkedHashSet<WebView>()

    fun bind(page: CompanionPage) {
        this.page = page
    }

    fun attach(web: WebView, handler: DownloadHandler) {
        downloads?.close()
        view = web
        downloads = handler
    }

    fun detach(view: View) {
        val web = view as? WebView
        if (web != null && web in destroyed) return
        (view.parent as? ViewGroup)?.removeView(view)
    }

    fun trackPopup(popup: WebView) {
        popups.add(popup)
    }

    fun closePopup(popup: WebView) {
        if (!popups.remove(popup)) return
        (popup.parent as? ViewGroup)?.removeView(popup)
        try {
            popup.destroy()
        } catch (_: Exception) {
        }
    }

    fun destroy(web: WebView?) {
        val target = web ?: return
        if (!destroyed.add(target)) return
        if (view === target) {
            downloads?.close()
            downloads = null
            view = null
            if (page?.view === target) page = null
        }
        (target.parent as? ViewGroup)?.removeView(target)
        try {
            target.destroy()
        } catch (_: Exception) {
        }
    }

    fun destroyCurrent() {
        popups.toList().forEach { closePopup(it) }
        val current = page
        page = null
        if (current != null) current.destroy() else destroy(view)
    }
}
