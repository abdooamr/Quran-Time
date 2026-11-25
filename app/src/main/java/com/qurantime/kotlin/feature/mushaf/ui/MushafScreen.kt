package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.kotlin.LocalAppContainer
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.api.ApiLanguage
import com.qurantime.kotlin.core.api.MUSHAF_PAGES
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.theme.LocalMushafPalette
import com.qurantime.kotlin.core.ui.Async
import com.qurantime.kotlin.feature.catalog.Surah
import com.qurantime.kotlin.feature.mushaf.juzForPage
import com.qurantime.kotlin.feature.mushaf.surahsOnPage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * The Mushaf tab: a header (juz, surah, page actions) over the swipeable
 * pages. [actions] fills the header's end slot for the page on screen.
 * [onOpenJump] is called when the surah cartouche is tapped.
 */
@Composable
fun MushafScreen(
    actions: @Composable RowScope.(page: Int, suwar: List<Surah>) -> Unit = { _, _ -> },
    onOpenJump: ((suwar: List<Surah>) -> Unit)? = null,
    pageOverlay: @Composable (page: Int) -> Unit = { MushafPageView(it) },
    belowPages: @Composable () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val lang = ApiLanguage.fromTag(settings.locale)
    val online by container.online.online.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    var suwar by remember(lang) { mutableStateOf<Async<List<Surah>>>(Async.Loading) }
    LaunchedEffect(lang, attempt) {
        suwar = try {
            Async.Data(container.catalog.suwar(lang))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Async.Failed(e)
        }
    }
    // After an offline first launch, try again as soon as the connection returns.
    LaunchedEffect(online) { if (online && suwar is Async.Failed) attempt++ }

    val palette = LocalMushafPalette.current
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        val list = (suwar as? Async.Data)?.value.orEmpty()
        Header(
            suwar = list,
            onRetry = if (suwar is Async.Failed) ({ attempt++ }) else null,
            actions = actions,
            onOpenJump = onOpenJump,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MushafPager(pageOverlay)
            belowPages()
        }
    }
}

private val SIDE = 104.dp

@Composable
private fun Header(
    suwar: List<Surah>,
    onRetry: (() -> Unit)?,
    actions: @Composable RowScope.(page: Int, suwar: List<Surah>) -> Unit,
    onOpenJump: ((List<Surah>) -> Unit)?,
) {
    val container = LocalAppContainer.current
    val page by container.mushaf.currentPage.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val juz = juzForPage(page)
    val onPage = surahsOnPage(page, suwar)
    Row(
        Modifier.fillMaxWidth().background(LocalMushafPalette.current.background).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SIDE), contentAlignment = Alignment.CenterStart) {
            AnimatedContent(juz, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() }, label = "juz") { j ->
                val n = localDigits(j, locale)
                Roundel(numeral = n, semanticLabel = stringResource(R.string.juz_number, n), caption = stringResource(R.string.juz_short))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val first = onPage.firstOrNull()
            AnimatedContent(first, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut() }, label = "surah") { s ->
                if (s == null) {
                    Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (onRetry != null) {
                            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.retry)) }
                        }
                    }
                } else {
                    SurahCartouche(
                        name = s.name,
                        onTap = onOpenJump?.let { open -> { open(suwar) } },
                        onTapLabel = stringResource(R.string.jump_to),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.width(SIDE), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions(page, suwar)
        }
    }
}

/**
 * The swipeable pages. The next page is always to the left, as in a printed
 * Mushaf, whatever the UI language.
 */
@Composable
private fun MushafPager(pageContent: @Composable (page: Int) -> Unit) {
    val mushaf = LocalAppContainer.current.mushaf
    val pager = rememberPagerState(initialPage = mushaf.currentPage.value - 1) { MUSHAF_PAGES }
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    // The page a jump is heading to; reaching it is not the reader swiping away.
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i ->
            val page = i + 1
            val byJump = jumpTarget == i
            if (byJump) jumpTarget = null
            if (page != mushaf.currentPage.value && !byJump) mushaf.pauseFollow()
            mushaf.onPageShown(page)
        }
    }
    LaunchedEffect(pager) {
        mushaf.jumpRequests.collect { page ->
            val target = page - 1
            if (target == pager.currentPage) return@collect
            jumpTarget = target
            if (abs(target - pager.currentPage) <= 3) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    HorizontalPager(
        state = pager,
        reverseLayout = ltr,
        beyondViewportPageCount = 1,
        key = { it },
        modifier = Modifier.fillMaxSize().testTag("mushaf-pager"),
    ) { i ->
        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) { pageContent(i + 1) }
    }
}
