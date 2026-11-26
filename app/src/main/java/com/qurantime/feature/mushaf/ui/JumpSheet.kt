package com.qurantime.feature.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qurantime.R
import com.qurantime.core.api.MUSHAF_PAGES
import com.qurantime.core.l10n.localDigits
import com.qurantime.core.l10n.parseLocalDigits
import com.qurantime.core.theme.BookFont
import com.qurantime.core.theme.LocalMushafPalette
import com.qurantime.feature.catalog.Surah
import com.qurantime.feature.mushaf.JUZ_START_PAGES
import com.qurantime.feature.mushaf.juzForPage
import com.qurantime.feature.mushaf.surahsOnPage
import kotlinx.coroutines.launch

/** The "go to" sheet: surahs, juz, or a page number. Calls [onJump] with the chosen page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpSheet(suwar: List<Surah>, currentPage: Int?, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Box(Modifier.fillMaxHeight(0.88f)) {
            JumpSheetContent(suwar, currentPage) { page ->
                scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
                onJump(page)
            }
        }
    }
}

@Composable
fun JumpSheetContent(suwar: List<Surah>, currentPage: Int?, onJump: (Int) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.tab_surahs, R.string.juz_short, R.string.tab_page).forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
            }
        }
        when (tab) {
            0 -> SurahList(suwar, currentPage, onJump)
            1 -> JuzList(currentPage, onJump)
            else -> PageField(onJump)
        }
    }
}

@Composable
private fun bookTitle() = MaterialTheme.typography.titleMedium.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold)

@Composable
private fun SurahList(suwar: List<Surah>, currentPage: Int?, onJump: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val current = currentPage?.let { p -> surahsOnPage(p, suwar).firstOrNull()?.let { suwar.indexOf(it) } } ?: -1
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    val selectedBg = LocalMushafPalette.current.field.copy(alpha = 0.12f)
    LazyColumn(Modifier.fillMaxSize(), state = list) {
        itemsIndexed(suwar, key = { _, s -> s.id }) { i, s ->
            val digits = localDigits(s.id, locale)
            ListItem(
                modifier = Modifier.clickable { onJump(s.startPage) }.then(if (i == current) Modifier.testTag("jump-selected") else Modifier),
                colors = ListItemDefaults.colors(containerColor = if (i == current) selectedBg else MaterialTheme.colorScheme.surface),
                leadingContent = { Roundel(numeral = digits, semanticLabel = digits, size = 44.dp) },
                headlineContent = { Text(s.name, style = bookTitle()) },
                supportingContent = {
                    Text(stringResource(R.string.surah_pages, stringResource(if (s.isMakki) R.string.makki else R.string.madani), localDigits(s.startPage, locale)))
                },
            )
        }
    }
}

@Composable
private fun JuzList(currentPage: Int?, onJump: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val current = currentPage?.let { juzForPage(it) - 1 } ?: -1
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    val selectedBg = LocalMushafPalette.current.field.copy(alpha = 0.12f)
    LazyColumn(Modifier.fillMaxSize(), state = list) {
        items(JUZ_START_PAGES.size) { i ->
            val n = localDigits(i + 1, locale)
            ListItem(
                modifier = Modifier.clickable { onJump(JUZ_START_PAGES[i]) }.then(if (i == current) Modifier.testTag("jump-selected") else Modifier),
                colors = ListItemDefaults.colors(containerColor = if (i == current) selectedBg else MaterialTheme.colorScheme.surface),
                leadingContent = { Roundel(numeral = n, semanticLabel = n, size = 44.dp) },
                headlineContent = { Text(stringResource(R.string.juz_number, n), style = bookTitle()) },
                supportingContent = { Text(stringResource(R.string.page_number, localDigits(JUZ_START_PAGES[i], locale))) },
            )
        }
    }
}

@Composable
private fun PageField(onJump: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val page = parseLocalDigits(text)
        if (page == null || page !in 1..MUSHAF_PAGES) error = true else onJump(page)
    }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Top) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = false
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(stringResource(R.string.page_field_label)) },
            isError = error,
            supportingText = if (error) ({ Text(stringResource(R.string.page_range_error)) }) else null,
            textStyle = bookTitle().copy(fontSize = 22.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go)) }
    }
}
