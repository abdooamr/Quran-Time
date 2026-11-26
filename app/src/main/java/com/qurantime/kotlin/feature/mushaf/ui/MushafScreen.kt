package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.animation.core.tween
import androidx.activity.compose.ReportDrawnWhen
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
import androidx.compose.runtime.rememberCoroutineScope
import com.qurantime.kotlin.core.nav.AppTab
import com.qurantime.kotlin.core.nav.LocalNavController
import com.qurantime.kotlin.core.nav.MushafReciterRoute
import com.qurantime.kotlin.core.nav.TafsirSurahRoute
import com.qurantime.kotlin.core.nav.openTab
import com.qurantime.kotlin.core.ui.LocalSnackbar
import com.qurantime.kotlin.core.ui.say
import com.qurantime.kotlin.feature.followalong.ui.RepeatRequest
import com.qurantime.kotlin.feature.followalong.ui.RepeatSheetHost
import com.qurantime.kotlin.feature.mushaf.AyahShape
import kotlinx.coroutines.launch
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
 * Tapping the surah cartouche opens the jump sheet.
 */
@Composable
fun MushafScreen(
    headerActions: @Composable RowScope.(page: Int, suwar: List<Surah>) -> Unit = { _, _ -> },
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

    var jumpSuwar by remember { mutableStateOf<List<Surah>?>(null) }
    jumpSuwar?.let { list ->
        // The page when the sheet opens; collecting it here would recompose the
        // whole screen on every swipe.
        @Suppress("StateFlowValueCalledInComposition")
        val openedOn = container.mushaf.currentPage.value
        JumpSheet(
            suwar = list,
            currentPage = openedOn,
            onJump = { container.mushaf.goTo(it) },
            onDismiss = { jumpSuwar = null },
        )
    }

    // Follow-along: highlight, page turns, the actions of a tapped ayah, ayah repeats.
    FollowEffect()
    var tapped by remember { mutableStateOf<AyahShape?>(null) }
    var repeat by remember { mutableStateOf<RepeatRequest?>(null) }
    val names = (suwar as? Async.Data)?.value.orEmpty().associate { it.id to it.name }
    val actions = rememberAyahActions(names) { repeat = it }
    tapped?.let { shape ->
        AyahActionsSheet(shape, names[shape.surah] ?: "${shape.surah}", actions, onDismiss = { tapped = null })
    }
    repeat?.let { RepeatSheetHost(it, onDone = { repeat = null }) }

    // Start-up is "fully drawn" once the opening page is on screen.
    val drawn by container.mushaf.firstPageDrawn.collectAsStateWithLifecycle()
    ReportDrawnWhen { drawn }

    val palette = LocalMushafPalette.current
    Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding()) {
        val list = (suwar as? Async.Data)?.value.orEmpty()
        Header(
            suwar = list,
            onRetry = if (suwar is Async.Failed) ({ attempt++ }) else null,
            actions = headerActions,
            onOpenJump = { jumpSuwar = it },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MushafPager { page -> HighlightedPage(page, selected = tapped, onAyah = { tapped = it }) }
            BackToRecitation()
        }
    }
}

/** The actions of a tapped ayah: play from it, repeat it, bookmark it, open the surah's tafsir. */
@Composable
private fun rememberAyahActions(names: Map<Int, String>, onRepeat: (RepeatRequest) -> Unit): AyahActions {
    val c = LocalAppContainer.current
    val nav = LocalNavController.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val missing = stringResource(R.string.reciter_missing_surah)
    val change = stringResource(R.string.change)
    val error = stringResource(R.string.error_generic)
    val bookmarked = stringResource(R.string.ayah_bookmarked)
    return AyahActions(
        playFromHere = { shape ->
            scope.launch {
                val ok = try {
                    c.follow.playFromAyah(shape.surah, shape.ayah)
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
        },
        repeat = { shape -> onRepeat(RepeatRequest(shape.surah, names[shape.surah] ?: "${shape.surah}", shape.ayah, shape.ayah)) },
        bookmark = { shape ->
            scope.launch {
                c.bookmarks.addAyah(shape.surah, shape.ayah, shape.page)
                snackbar.say(bookmarked)
            }
        },
        tafsir = { shape ->
            nav.openTab(AppTab.EXPLORE)
            nav.navigate(TafsirSurahRoute(tafsirId = 1, surah = shape.surah))
        },
    )
}

private val SIDE = 104.dp

/**
 * The header's fade-through on a page turn (260 ms); with animations off, an
 * instant cut (no transition at all, so the old value never shares a frame).
 */
@Composable
private fun <T> HeaderSwap(value: T, reduceMotion: Boolean, label: String, content: @Composable (T) -> Unit) {
    if (reduceMotion) {
        content(value)
    } else {
        AnimatedContent(
            value,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.92f)) togetherWith fadeOut(tween(260)) },
            label = label,
        ) { content(it) }
    }
}

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
    val reduceMotion = rememberReduceMotion()
    val onPage = surahsOnPage(page, suwar)
    Row(
        Modifier.fillMaxWidth().background(LocalMushafPalette.current.background).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(SIDE), contentAlignment = Alignment.CenterStart) {
            HeaderSwap(juz, reduceMotion, "juz") { j ->
                val n = localDigits(j, locale)
                Roundel(numeral = n, semanticLabel = stringResource(R.string.juz_number, n), caption = stringResource(R.string.juz_short))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val first = onPage.firstOrNull()
            HeaderSwap(first, reduceMotion, "surah") { s ->
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
                        modifier = Modifier.fillMaxWidth().testTag("surah-cartouche"),
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
    // Only the starting page; after that the pager owns the position.
    @Suppress("StateFlowValueCalledInComposition")
    val startPage = mushaf.currentPage.value - 1
    val pager = rememberPagerState(initialPage = startPage) { MUSHAF_PAGES }
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    // The page a jump is heading to; reaching it is not the reader swiping away.
    var jumpTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pager) {
        // Coming back to the tab restores the pager's old page; a jump made
        // meanwhile (from Bookmarks, say) wins.
        val want = mushaf.currentPage.value - 1
        if (pager.currentPage != want) {
            jumpTarget = want
            pager.scrollToPage(want)
        }
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
