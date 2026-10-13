package com.synalysis.whatsweb.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synalysis.whatsweb.R
import com.synalysis.whatsweb.web.AudioEndpoint
import com.synalysis.whatsweb.web.AudioKind

@Composable
internal fun CallSoundButton(
    audio: CallAudio,
    modifier: Modifier = Modifier,
) {
    val routes = audio.routes
    if (routes.size < 2) return
    val selected = routes.firstOrNull { it.id == audio.selectedId } ?: routes.first()
    var open by remember { mutableStateOf(false) }
    val label = routeLabel(selected)
    val description = stringResource(R.string.cd_audio_route, label)
    androidx.compose.foundation.layout.Box(modifier) {
        Surface(
            modifier = Modifier
                .clickable { open = true }
                .semantics { contentDescription = description },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteIcon(selected.kind, Modifier.size(20.dp))
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(max = 180.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            routes.forEach { route ->
                val itemLabel = routeLabel(route)
                val current = route.id == selected.id
                DropdownMenuItem(
                    text = { Text(itemLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RouteIcon(route.kind, Modifier.size(24.dp)) },
                    trailingIcon = {
                        if (current) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier.semantics { this.selected = current },
                    onClick = {
                        open = false
                        audio.select(route.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun routeLabel(route: AudioEndpoint): String {
    val named = route.name.isNotEmpty()
    return when (route.kind) {
        AudioKind.EARPIECE -> stringResource(R.string.audio_earpiece)
        AudioKind.SPEAKER -> stringResource(R.string.audio_speaker)
        AudioKind.WIRED -> if (named) route.name else stringResource(R.string.audio_headset)
        AudioKind.BLUETOOTH -> if (named) route.name else stringResource(R.string.audio_bluetooth)
    }
}

@Composable
private fun RouteIcon(kind: AudioKind, modifier: Modifier) {
    when (kind) {
        AudioKind.EARPIECE -> Icon(Icons.Filled.Phone, contentDescription = null, modifier = modifier)
        AudioKind.SPEAKER -> Icon(painterResource(R.drawable.ic_audio_speaker), contentDescription = null, modifier = modifier)
        AudioKind.WIRED -> Icon(painterResource(R.drawable.ic_audio_headset), contentDescription = null, modifier = modifier)
        AudioKind.BLUETOOTH -> Icon(painterResource(R.drawable.ic_audio_bluetooth), contentDescription = null, modifier = modifier)
    }
}
