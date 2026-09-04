package com.nextservices.nextvision.fragments.season

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.models.Episode
import com.nextservices.nextvision.models.Season
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.utils.EpisodeManager
import com.nextservices.nextvision.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class SeasonViewModel(
    seasonId: String,
    private val tvShowId: String,
    private val database: AppDatabase,
    private val mobilePaging: Boolean = false,
) : ViewModel() {
    private val pageSize = 20
    private var allEpisodes: List<Episode> = emptyList()
    private var loadedEpisodeCount = 0
    var seasonNumber = 0
    var tvShowTitle = ""
    private val _state = MutableStateFlow<State>(State.LoadingEpisodes)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoadingEpisodes -> {
                    database.episodeDao()
                        .getBySeasonIdAsFlow(seasonId)
                        .collect { emit(it) }
                }
                else -> emit(emptyList())
            }
        },
        database.tvShowDao().getByIdAsFlow(tvShowId),
        database.seasonDao().getByIdAsFlow(seasonId),
    ) { state, episodesDb, tvShow, season ->
        season?.number?.let { seasonNumber = it }
        tvShow?.title?.let { tvShowTitle = it }

        when (state) {
            is State.SuccessLoadingEpisodes -> {
                State.SuccessLoadingEpisodes(
                    episodes = state.episodes.map { episode ->
                        episodesDb.find { it.id == episode.id }
                            ?.takeIf { !episode.isSame(it) }
                            ?.let { episode.copy().merge(it) }
                            ?: episode
                    }.sortedBy { it.number }.onEach {
                        it.tvShow = tvShow
                        it.season = season
                    }
                )
            }
            else -> state
        }
    }

    sealed class State {
        data object LoadingEpisodes : State()
        data class SuccessLoadingEpisodes(
            val episodes: List<Episode>,
            val hasMore: Boolean = false,
        ) : State()
        data object LoadingMoreEpisodes : State()
        data class FailedLoadingEpisodes(val error: Exception) : State()
    }

    init {
        getSeasonEpisodes(seasonId)
    }


    fun getSeasonEpisodes(seasonId: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.LoadingEpisodes)

        try {
            val episodes = UserPreferences.currentProvider!!
                .getEpisodesBySeason(seasonId)
                .sortedBy { it.number }
            allEpisodes = episodes
            val visibleEpisodes = if (mobilePaging) episodes.take(pageSize) else episodes
            loadedEpisodeCount = visibleEpisodes.size
            val ids = visibleEpisodes.map { it.id }
            val episodeMap = visibleEpisodes.associateBy { it.id }

            ids.chunked(400).forEach { chunk ->
                database.episodeDao()
                    .getByIds(chunk)
                    .forEach { episodeDb ->
                        episodeMap[episodeDb.id]?.merge(episodeDb)
                    }
            }

            val storedTvShow = database.tvShowDao().getById(tvShowId)
            val hasValidMobileRuntime = storedTvShow?.runtime != null && storedTvShow.runtime!! > 0
            val tvShow = if (mobilePaging && !hasValidMobileRuntime) {
                runCatching {
                    UserPreferences.currentProvider!!.getTvShow(tvShowId)
                }.getOrNull()?.also(database.tvShowDao()::insert)
                    ?: storedTvShow
                    ?: TvShow(tvShowId)
            } else {
                storedTvShow ?: TvShow(tvShowId)
            }
            val season = Season(seasonId)
            visibleEpisodes.forEach { episode ->
                episode.tvShow = tvShow
                episode.season = season
                if (mobilePaging && (episode.runtime == null || episode.runtime!! <= 0)) {
                    episode.runtime = tvShow.runtime?.takeIf { runtime -> runtime > 0 }
                }
            }

            database.episodeDao().insertAll(visibleEpisodes)

            EpisodeManager.addEpisodes(EpisodeManager.convertToVideoTypeEpisodes(visibleEpisodes, database, seasonNumber))
            _state.emit(
                State.SuccessLoadingEpisodes(
                    visibleEpisodes,
                    hasMore = mobilePaging && loadedEpisodeCount < allEpisodes.size,
                )
            )
        } catch (e: Exception) {
            Log.e("SeasonViewModel", "getSeasonEpisodes: ", e)
            _state.emit(State.FailedLoadingEpisodes(e))
        }
    }

    fun loadMoreEpisodes() = viewModelScope.launch(Dispatchers.IO) {
        if (!mobilePaging || loadedEpisodeCount >= allEpisodes.size) return@launch

        _state.emit(State.LoadingMoreEpisodes)
        try {
            val nextEpisodes = allEpisodes
                .drop(loadedEpisodeCount)
                .take(pageSize)
            loadedEpisodeCount += nextEpisodes.size

            database.episodeDao().insertAll(nextEpisodes)
            EpisodeManager.addEpisodes(
                EpisodeManager.convertToVideoTypeEpisodes(nextEpisodes, database, seasonNumber)
            )
            _state.emit(
                State.SuccessLoadingEpisodes(
                    episodes = allEpisodes.take(loadedEpisodeCount),
                    hasMore = loadedEpisodeCount < allEpisodes.size,
                )
            )
        } catch (e: Exception) {
            Log.e("SeasonViewModel", "loadMoreEpisodes: ", e)
            _state.emit(State.FailedLoadingEpisodes(e))
        }
    }

}
