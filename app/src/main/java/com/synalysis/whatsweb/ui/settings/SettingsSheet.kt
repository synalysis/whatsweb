package com.synalysis.whatsweb.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.synalysis.whatsweb.BuildConfig
import com.synalysis.whatsweb.R
import com.synalysis.whatsweb.prefs.ThemeMode
import com.synalysis.whatsweb.prefs.UserPreferences

@Composable
fun SettingsSheet(
    prefs: UserPreferences,
    onTheme: (ThemeMode) -> Unit,
    onReload: () -> Unit,
    onClearSession: () -> Unit,
    onLockRejected: () -> Unit,
    canUseLock: () -> Boolean,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.cd_reload)) },
            modifier = Modifier.clickable(onClick = onReload),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_text_size)) },
            supportingContent = {
                Column {
                    Text(stringResource(R.string.pref_text_size_value, prefs.textZoom))
                    Slider(
                        value = prefs.textZoom.toFloat(),
                        onValueChange = { prefs.updateTextZoom(it.toInt()) },
                        valueRange = 80f..150f,
                        steps = 6,
                    )
                }
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_phone_layout)) },
            supportingContent = { Text(stringResource(R.string.pref_phone_layout_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.phoneLayout,
                    onCheckedChange = prefs::updatePhoneLayout,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_hide_ai)) },
            supportingContent = { Text(stringResource(R.string.pref_hide_ai_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.hideAi,
                    onCheckedChange = prefs::updateHideAi,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_theme)) },
            supportingContent = {
                Row {
                    ThemeChip(R.string.theme_system, prefs.themeMode == ThemeMode.SYSTEM) {
                        onTheme(ThemeMode.SYSTEM)
                    }
                    ThemeChip(R.string.theme_light, prefs.themeMode == ThemeMode.LIGHT) {
                        onTheme(ThemeMode.LIGHT)
                    }
                    ThemeChip(R.string.theme_dark, prefs.themeMode == ThemeMode.DARK) {
                        onTheme(ThemeMode.DARK)
                    }
                }
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_dynamic)) },
            supportingContent = { Text(stringResource(R.string.pref_dynamic_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.dynamicColor,
                    onCheckedChange = prefs::updateDynamicColor,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_lock)) },
            supportingContent = { Text(stringResource(R.string.pref_lock_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.appLock,
                    onCheckedChange = { enabled ->
                        if (enabled && !canUseLock()) {
                            onLockRejected()
                        } else {
                            prefs.updateAppLock(enabled)
                        }
                    },
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_secure)) },
            supportingContent = { Text(stringResource(R.string.pref_secure_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.hideRecents,
                    onCheckedChange = prefs::updateHideRecents,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_links)) },
            supportingContent = { Text(stringResource(R.string.pref_links_hint)) },
            trailingContent = {
                Switch(
                    checked = prefs.openLinks,
                    onCheckedChange = prefs::updateOpenLinks,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.session_clear)) },
            trailingContent = {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.session_clear))
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.about_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.about_license),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.about_notifications),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.session_clear_title)) },
            text = { Text(stringResource(R.string.session_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearSession()
                    },
                ) {
                    Text(stringResource(R.string.session_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ThemeChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.padding(end = 8.dp),
    )
}
