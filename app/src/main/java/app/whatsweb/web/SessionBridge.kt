package app.whatsweb.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import app.whatsweb.ui.AppState

class SessionBridge(
    private val state: AppState,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPane(pane: String) {
        if (pane != "list" && pane != "chat") return
        main.post { state.pane = pane }
    }

    @JavascriptInterface
    fun onSession(linked: Boolean) {
        main.post { state.linked = linked }
    }
}
