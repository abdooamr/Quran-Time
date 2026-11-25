package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.nav.AppTab
import com.qurantime.kotlin.core.nav.LocalNavController
import com.qurantime.kotlin.core.nav.MushafReciterRoute
import com.qurantime.kotlin.core.nav.openTab
import com.qurantime.kotlin.core.ui.LocalSnackbar
import com.qurantime.kotlin.core.ui.say
import com.qurantime.kotlin.feature.catalog.Surah
import com.qurantime.kotlin.feature.mushaf.surahsOnPage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/** Plays the Mushaf reciter from the first ayah on this page, or pauses whatever is playing. */
@Composable
fun PlayPageButton(page: Int, suwar: List<Surah>) {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val player by c.playerController.state.collectAsStateWithLifecycle()
    val missing = stringResource(R.string.reciter_missing_surah)
    val change = stringResource(R.string.change)
    val error = stringResource(R.string.error_generic)
    if (player.playing) {
        IconButton(onClick = { scope.launch { c.playerController.togglePlay() } }) {
            Icon(Icons.Rounded.PauseCircleOutline, stringResource(R.string.pause))
        }
        return
    }
    IconButton(onClick = {
        c.mainScope.launch {
            var surah = surahsOnPage(page, suwar).firstOrNull()?.id
            var ayah = 1
            try {
                c.geometry.forPage(page).firstOrNull()?.let {
                    surah = it.surah
                    ayah = it.ayah
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Without outlines (offline), start at the first surah on the page.
            }
            val s = surah ?: return@launch
            val ok = try {
                c.follow.playFromAyah(s, ayah)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbar.say(error)
                return@launch
            }
            if (!ok && snackbar.say(missing, change)) {
                nav.openTab(AppTab.MORE)
                nav.navigate(MushafReciterRoute)
            }
        }
    }) {
        Icon(Icons.Rounded.PlayCircleOutline, stringResource(R.string.play_page))
    }
}
