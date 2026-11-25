package com.qurantime.kotlin.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.net.OnlineStatus

/** A thin strip shown while offline. */
@Composable
fun OfflineBanner(status: OnlineStatus) {
    val online by status.online.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = !online) {
        val scheme = MaterialTheme.colorScheme
        Surface(color = scheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.offline_banner), color = scheme.onSecondaryContainer)
            }
        }
    }
}
