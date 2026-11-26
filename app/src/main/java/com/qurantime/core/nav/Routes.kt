package com.qurantime.core.nav

import kotlinx.serialization.Serializable

// Tab graphs: each tab is a nested graph with its own back stack.
@Serializable data object MushafTab
@Serializable data object ListenTab
@Serializable data object LiveTab
@Serializable data object ExploreTab
@Serializable data object MoreTab

// Tab start screens
@Serializable data object MushafRoute
@Serializable data object ListenRoute
@Serializable data object LiveRoute
@Serializable data object ExploreRoute
@Serializable data object MoreRoute

// Listen
@Serializable data object RecentRoute
@Serializable data class ReciterRoute(val id: Int)
@Serializable data class MoshafRoute(val reciterId: Int, val moshafId: Int)

// Explore
@Serializable data object VideosRoute
@Serializable data object TafsirListRoute
@Serializable data class TafsirRoute(val tafsirId: Int)
@Serializable data class TafsirSurahRoute(val tafsirId: Int, val surah: Int)
@Serializable data object TadaborListRoute
@Serializable data class TadaborRoute(val surah: Int)

// More
@Serializable data object BookmarksRoute
@Serializable data object DownloadsRoute
@Serializable data object MushafReciterRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute

// Full screen
@Serializable data object PlayerRoute
@Serializable data class VideoRoute(val url: String, val title: String, val live: Boolean)
