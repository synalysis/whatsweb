package com.synalysis.whatsweb.web

import android.os.Bundle
import android.view.View

/**
 * The page surface the rest of the app talks to. The usual build wraps the
 * system WebView. The separate Gecko build wraps the Firefox engine.
 */
interface CompanionPage {
    val view: View

    fun loadUrl(url: String)
    fun reload()
    fun runScript(script: String, onResult: ((String) -> Unit)? = null)
    fun goBack(onResult: (Boolean) -> Unit)
    fun setTextZoom(zoom: Int)
    fun setBackgroundColor(color: Int)
    fun resume()
    fun pause()
    fun saveState(out: Bundle)
    fun restoreState(saved: Bundle): Boolean
    fun clearData(onDone: (() -> Unit)? = null)
    fun prepareToExit(onDone: () -> Unit)
    fun destroy()
}
