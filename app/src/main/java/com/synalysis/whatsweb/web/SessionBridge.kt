package com.synalysis.whatsweb.web

import com.synalysis.whatsweb.ui.AppState

class SessionBridge(
    private val state: AppState,
) {
    internal fun onMessage(message: HostMessages.Message) {
        when (message) {
            is HostMessages.Message.Pane -> {
                if (message.pane == "list" || message.pane == "chat") state.pane = message.pane
            }
            is HostMessages.Message.Cover -> state.infoOpen = message.open
            is HostMessages.Message.Session -> state.linked = message.linked
            else -> Unit
        }
    }
}
