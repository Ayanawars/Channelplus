package tv.own.owntv.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

data class TypeCounts(val live: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val total: Int get() = live + movies + series
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class BackupCountsViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
) : ViewModel() {

    /** The last successful backup, or `null` if none has been taken on this device. */
    val lastBackup: StateFlow<SettingsRepository.LastBackup?> = settings.lastBackup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    /** How many playlists the active profile shows. */
    val playlistCount: StateFlow<Int> = ctx.map { it.sources.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favorites: StateFlow<TypeCounts> = counts(favorites = true)
    val history: StateFlow<TypeCounts> = counts(favorites = false)

    private fun ids(c: ActiveProfileSources, type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }

    private fun counts(favorites: Boolean): StateFlow<TypeCounts> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(TypeCounts())
            } else {
                combine(
                    if (favorites) channelDao.countFavorites(c.profileId, ids(c, MediaType.LIVE))
                    else channelDao.countHistory(c.profileId, ids(c, MediaType.LIVE)),
                    if (favorites) movieDao.countFavorites(c.profileId, ids(c, MediaType.MOVIE))
                    else movieDao.countHistory(c.profileId, ids(c, MediaType.MOVIE)),
                    if (favorites) seriesDao.countFavorites(c.profileId, ids(c, MediaType.SERIES))
                    else seriesDao.countHistory(c.profileId, ids(c, MediaType.SERIES)),
                    ::TypeCounts,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeCounts())
}
