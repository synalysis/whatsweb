package app.whatsweb.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.whatsweb.R
import app.whatsweb.prefs.ThemeMode
import app.whatsweb.prefs.UserPreferences
import app.whatsweb.ui.browser.BrowserScreen
import app.whatsweb.ui.settings.SettingsSheet
import app.whatsweb.ui.welcome.WelcomeScreen
import app.whatsweb.web.WebHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatswebApp(
    prefs: UserPreferences,
    state: AppState,
    webSlot: WebSlot,
    host: WebHost,
    savedState: android.os.Bundle?,
    locked: Boolean,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onUnlock: () -> Unit,
    onLeave: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onClearSession: () -> Unit,
    canUseLock: () -> Boolean,
) {
    val context = LocalContext.current
    val lockUnavailable = stringResource(R.string.lock_unavailable)
    var showSettings by rememberSaveable { mutableStateOf(false) }

    SideEffect {
        val window = (context as Activity).window
        if (prefs.hideRecents || locked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (state.linked == false && !locked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(Modifier.fillMaxSize()) {
    if (prefs.welcomeDone) {
        BrowserScreen(
            prefs = prefs,
            state = state,
            host = host,
            webSlot = webSlot,
            savedState = savedState,
            snackbarHostState = snackbarHostState,
            onOpenSettings = { showSettings = true },
            onLeave = onLeave,
        )
    } else {
        WelcomeScreen(onContinue = prefs::completeWelcome)
    }

    if (showSettings && prefs.welcomeDone) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
        ) {
            SettingsSheet(
                prefs = prefs,
                onTheme = onTheme,
                onReload = {
                    showSettings = false
                    state.mainFrameError = false
                    webSlot.view?.reload()
                },
                onClearSession = {
                    showSettings = false
                    onClearSession()
                },
                onLockRejected = { state.notice = lockUnavailable },
                canUseLock = canUseLock,
            )
        }
    }

    if (locked && prefs.welcomeDone) {
        LaunchedEffect(Unit) { onUnlock() }
        BackHandler { }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.cd_unlock),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onUnlock) {
                    Text(stringResource(R.string.lock_button))
                }
            }
        }
    }
    }
}
