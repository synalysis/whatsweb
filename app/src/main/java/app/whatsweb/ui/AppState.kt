package app.whatsweb.ui

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppState {
    var pane by mutableStateOf("list")
    var linked by mutableStateOf<Boolean?>(null)
    var progress by mutableIntStateOf(0)
    var pageTitle by mutableStateOf<String?>(null)
    var mainFrameError by mutableStateOf(false)
    var sharedText by mutableStateOf<String?>(null)
    var pendingUrl by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
}

class WebSlot {
    var view: WebView? = null
}
