package com.qurantime.kotlin.feature.mushaf.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qurantime.kotlin.R
import com.qurantime.kotlin.core.l10n.localDigits
import com.qurantime.kotlin.core.theme.BookFont
import com.qurantime.kotlin.feature.mushaf.AyahShape

/** What can be done with a tapped ayah. Each action closes the sheet first. */
class AyahActions(
    val playFromHere: (AyahShape) -> Unit,
    val repeat: (AyahShape) -> Unit,
    val bookmark: (AyahShape) -> Unit,
    val tafsir: (AyahShape) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyahActionsSheet(shape: AyahShape, surahName: String, actions: AyahActions, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.ayah_title, surahName, localDigits(shape.ayah, locale)),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = BookFont, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            Row(Icons.Rounded.PlayArrow, stringResource(R.string.play_from_here)) { onDismiss(); actions.playFromHere(shape) }
            Row(Icons.Rounded.RepeatOne, stringResource(R.string.repeat_this_ayah)) { onDismiss(); actions.repeat(shape) }
            Row(Icons.Outlined.BookmarkAdd, stringResource(R.string.bookmark_this_ayah)) { onDismiss(); actions.bookmark(shape) }
            Row(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.tafsir_of_surah)) { onDismiss(); actions.tafsir(shape) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Row(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(label) },
    )
}
