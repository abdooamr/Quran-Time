package com.qurantime.feature.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.LocalAppContainer
import com.qurantime.R
import com.qurantime.core.api.ApiLanguage
import com.qurantime.core.nav.LocalNavController
import com.qurantime.core.ui.AsyncView
import com.qurantime.feature.audio.AudioClip
import com.qurantime.feature.explore.TadaborClip
import com.qurantime.feature.live.ui.openVideo
import kotlinx.coroutines.launch

/** A surah's reflections: video clips open the video player, audio clips play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TadaborScreen(surah: Int) {
    val c = LocalAppContainer.current
    val lang = ApiLanguage.fromTag(c.settings.state.collectAsStateWithLifecycle().value.locale)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.reflections)) }) }) { padding ->
        Box(Modifier.padding(padding)) {
            AsyncView(load = { c.explore.tadabor(surah, lang) }, lang, surah, isEmpty = { it.isEmpty() }, emptyText = stringResource(R.string.no_reflections)) { clips ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(clips, key = { it.id }) { ClipCard(it) }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(clip: TadaborClip) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val title = clip.title.ifEmpty { clip.surahName }
    fun play() {
        val video = clip.videoUrl
        val audio = clip.audioUrl
        if (video != null) {
            openVideo(c, nav, video, title)
        } else if (audio != null) {
            c.mainScope.launch {
                c.playerController.playItems(
                    listOf(AudioClip("tadabor-${clip.id}", title, clip.reciterName ?: clip.surahName, audio, clip.imageUrl.ifEmpty { null })),
                )
            }
        }
    }
    val meta = listOfNotNull(clip.surahName, clip.reciterName, clip.riwayaName).filter { it.isNotEmpty() }.joinToString(" · ")
    OutlinedCard(Modifier.fillMaxWidth()) {
        if (clip.imageUrl.isNotEmpty()) {
            Thumbnail(
                clip.imageUrl,
                Icons.Outlined.Lightbulb,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (clip.title.isNotEmpty()) Text(clip.title, style = MaterialTheme.typography.titleMedium)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (clip.text.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(clip.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            FilledTonalIconButton(onClick = ::play) {
                Icon(if (clip.isVideo) Icons.Rounded.SmartDisplay else Icons.Rounded.PlayArrow, stringResource(R.string.play))
            }
        }
    }
}
