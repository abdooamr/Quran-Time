package com.qurantime.kotlin.feature.settings.ui

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.BuildConfig
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.nav.DownloadsRoute
import com.qurantime.kotlin.core.nav.LocalNavController
import com.qurantime.kotlin.core.nav.MushafReciterRoute
import com.qurantime.kotlin.core.theme.AppThemeMode
import com.qurantime.kotlin.core.theme.BookFont
import kotlinx.coroutines.launch

/**
 * Language, theme, the Mushaf reciter and auto-follow. A language change is
 * saved here; MainActivity sees it and recreates itself in that language.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val s by c.settings.state.collectAsStateWithLifecycle()
    fun save(block: suspend () -> Unit) = c.mainScope.launch { block() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.language))
            Segments(
                // Each language is named in itself.
                options = listOf("ar" to "العربية", "en" to "English"),
                selected = s.locale,
                onSelect = { tag -> save { c.settings.setLocale(tag) } },
            )
            Section(stringResource(R.string.theme))
            Segments(
                options = listOf(
                    AppThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    AppThemeMode.LIGHT to stringResource(R.string.theme_light),
                    AppThemeMode.SEPIA to stringResource(R.string.theme_sepia),
                    AppThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = s.themeMode,
                onSelect = { mode -> save { c.settings.setThemeMode(mode) } },
            )
            Section(stringResource(R.string.tab_mushaf))
            val reciter by produceState("", s.mushafReadId) {
                value = runCatching { c.timing.reads() }.getOrDefault(emptyList())
                    .firstOrNull { it.id == s.mushafReadId }?.let { "${it.name} · ${it.riwaya}" }.orEmpty()
            }
            ListItem(
                modifier = Modifier.clickable { nav.navigate(MushafReciterRoute) },
                leadingContent = { Icon(Icons.Outlined.RecordVoiceOver, null) },
                headlineContent = { Text(stringResource(R.string.mushaf_reciter)) },
                supportingContent = { if (reciter.isNotEmpty()) Text(reciter) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
            ListItem(
                modifier = Modifier.toggleable(value = s.autoFollow, role = Role.Switch) { v -> save { c.settings.setAutoFollow(v) } },
                leadingContent = { Icon(Icons.Outlined.AutoStories, null) },
                headlineContent = { Text(stringResource(R.string.auto_follow)) },
                supportingContent = { Text(stringResource(R.string.auto_follow_hint)) },
                trailingContent = { Switch(checked = s.autoFollow, onCheckedChange = null) },
            )
            Section(stringResource(R.string.storage))
            ListItem(
                modifier = Modifier.clickable { nav.navigate(DownloadsRoute) },
                leadingContent = { Icon(Icons.Outlined.DownloadForOffline, null) },
                headlineContent = { Text(stringResource(R.string.downloads)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.settings_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { if (value != selected) onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** The libraries the app ships, all under the Apache License 2.0, and the Amiri font (SIL OFL 1.1). */
private val LICENSES = listOf(
    "AndroidX, Jetpack Compose, Media3, Room, WorkManager, DataStore" to "Apache License 2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache License 2.0",
    "OkHttp, Okio" to "Apache License 2.0",
    "Coil" to "Apache License 2.0",
    "AndroidSVG" to "Apache License 2.0",
    "Amiri font" to "SIL Open Font License 1.1",
)

private const val MP3QURAN = "https://mp3quran.net"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.about)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineMedium, fontFamily = BookFont, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.source_credit), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, MP3QURAN.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    // No browser: nothing to open.
                }
            }) {
                Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("mp3quran.net")
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium, color = muted)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { licenses = true }) { Text(stringResource(R.string.licenses)) }
        }
    }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.licenses)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((what, license) in LICENSES) {
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                        Text(license, style = MaterialTheme.typography.bodySmall, color = muted)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
