package com.qurantime.core.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qurantime.core.l10n.LocalizedContent
import com.qurantime.core.theme.AppThemeMode
import com.qurantime.core.theme.QuranTimeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class Settings(
    val locale: String = "ar",
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val mushafReadId: Int = 5,
    val autoFollow: Boolean = true,
    val lastPage: Int = 1,
    val lastPlaybackJson: String? = null,
)

/** The user's settings, kept in Preferences DataStore. Call [load] once at start. */
class SettingsStore(private val store: DataStore<Preferences>) {
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    suspend fun load() {
        _state.value = store.data.first().toSettings()
    }

    suspend fun setLocale(tag: String) = set { it[LOCALE] = tag }
    suspend fun setThemeMode(mode: AppThemeMode) = set { it[THEME] = mode.name }
    suspend fun setMushafReadId(id: Int) = set { it[READ_ID] = id }
    suspend fun setAutoFollow(v: Boolean) = set { it[AUTO_FOLLOW] = v }
    suspend fun setLastPage(page: Int) = set { it[LAST_PAGE] = page }
    suspend fun setLastPlaybackJson(json: String?) = set { if (json == null) it.remove(LAST_PLAYBACK) else it[LAST_PLAYBACK] = json }

    private suspend fun set(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        _state.value = store.edit { block(it) }.toSettings()
    }

    private fun Preferences.toSettings() = Settings(
        locale = this[LOCALE] ?: "ar",
        themeMode = this[THEME]?.let { n -> AppThemeMode.entries.firstOrNull { it.name == n } } ?: AppThemeMode.SYSTEM,
        mushafReadId = this[READ_ID] ?: 5,
        autoFollow = this[AUTO_FOLLOW] ?: true,
        lastPage = this[LAST_PAGE] ?: 1,
        lastPlaybackJson = this[LAST_PLAYBACK],
    )

    private companion object {
        val LOCALE = stringPreferencesKey("locale")
        val THEME = stringPreferencesKey("theme_mode")
        val READ_ID = intPreferencesKey("mushaf_read_id")
        val AUTO_FOLLOW = booleanPreferencesKey("auto_follow")
        val LAST_PAGE = intPreferencesKey("last_page")
        val LAST_PLAYBACK = stringPreferencesKey("last_playback")
    }
}

/** Shows [content] in the language and theme the settings ask for, following changes live. */
@Composable
fun SettingsDrivenApp(settings: SettingsStore, content: @Composable () -> Unit) {
    val s by settings.state.collectAsStateWithLifecycle()
    LocalizedContent(s.locale) {
        QuranTimeTheme(s.themeMode, content)
    }
}
