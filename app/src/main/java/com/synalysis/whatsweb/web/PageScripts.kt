package com.synalysis.whatsweb.web

import android.content.Context
import org.json.JSONObject

/**
 * The page script strings shared by the system WebView and the Gecko build.
 * Both engines read the same files under assets/page.
 */
internal class PageScripts(context: Context) {
    private val app = context.applicationContext
    private val identitySource = readAsset("page/desktop_identity.js")
    private val layoutSource = readAsset("page/phone_layout.js")

    fun identity(chromeVersion: String): String {
        return "window.__whatswebChromeVersion=${JSONObject.quote(chromeVersion)};\n$identitySource"
    }

    fun layout(phone: Boolean, quiet: Boolean, hideAi: Boolean, hideCalls: Boolean): String {
        return "window.__whatswebPhone=$phone;\n" +
            "window.__whatswebQuiet=$quiet;\n" +
            "window.__whatswebHideAi=$hideAi;\n" +
            "window.__whatswebHideCalls=$hideCalls;\n" +
            tabLabels() +
            "\n$layoutSource"
    }

    private fun tabLabels(): String {
        fun quoted(id: Int) = JSONObject.quote(app.getString(id))
        return "window.__whatswebTabChats=${quoted(com.synalysis.whatsweb.R.string.tab_chats)};" +
            "window.__whatswebTabUpdates=${quoted(com.synalysis.whatsweb.R.string.tab_updates)};" +
            "window.__whatswebTabCalls=${quoted(com.synalysis.whatsweb.R.string.tab_calls)};"
    }

    private fun readAsset(path: String): String {
        return app.assets.open(path).bufferedReader().use { it.readText() }
    }
}

internal object PageCommands {
    fun setPhone(enabled: Boolean) = "window.whatswebSetPhone && window.whatswebSetPhone($enabled)"

    fun setHideAi(hide: Boolean) = "window.whatswebSetHideAi && window.whatswebSetHideAi($hide)"

    const val BACK = "window.whatswebBack ? window.whatswebBack() : false"
}
