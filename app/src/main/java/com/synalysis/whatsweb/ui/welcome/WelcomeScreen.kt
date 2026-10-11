package com.synalysis.whatsweb.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.synalysis.whatsweb.R

@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.welcome_kicker),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Point(Icons.Filled.Phone, R.string.welcome_point_link_title, R.string.welcome_point_link_body)
        Spacer(Modifier.height(18.dp))
        Point(Icons.AutoMirrored.Filled.List, R.string.welcome_point_layout_title, R.string.welcome_point_layout_body)
        Spacer(Modifier.height(18.dp))
        Point(Icons.Filled.Lock, R.string.welcome_point_privacy_title, R.string.welcome_point_privacy_body)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.welcome_continue))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Point(icon: ImageVector, title: Int, body: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
