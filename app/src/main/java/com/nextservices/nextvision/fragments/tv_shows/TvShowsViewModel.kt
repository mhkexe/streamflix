package com.nextservices.nextvision.fragments.tv_shows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.utils.ParentalControlUtils
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.ProviderChangeNotifier
import com.nextservices.nextvision.utils.StartupPreloadStore
import com.nextservices.nextvision.providers.TmdbProvider
import com.nextservices.nextvision.utils.TmdbFilterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class TvShowsViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    
    init {
        // Listen for provider changes and reload data
        viewModelScope.launch {
            ProviderChangeNotifier.providerChangeFlow.collect {
                getTvShows()
            }
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    if (state.tvShows.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.tvShowDao().getByIds(state.tvShows.map { it.id }))
                    }
                }
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                val tvShowsById = tvShowsDb.associateBy { it.id }
                State.SuccessLoading(
                    tvShows = state.tvShows.map { tvShow ->
                        tvShowsById[tvShow.id]
                            ?.takeIf { !tvShow.isSame(it) }
                            ?.let { tvShow.copy().merge(it) }
                            ?: tvShow
                    },
                    hasMore = state.hasMore
                )

            }
            else -> state
        }
    }.flowOn(Dispatchers.IO)

    private var page = 1
    private var filterOptions = TmdbFilterOptions()
    val currentFilters: TmdbFilterOptions get() = filterOptions

    sealed class State {
        data object Loading : State()
        data object LoadingMore : State()
        data class SuccessLoading(val tvShows: List<TvShow>, val hasMore: Boolean) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getTvShows()
    }


    fun getTvShows() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val provider = UserPreferences.currentProvider!!
            val tvShows = if (filterOptions == TmdbFilterOptions() && provider !is TmdbProvider) {
                provider.getTvShows()
            } else if (provider is TmdbProvider) {
                provider.getFilteredTvShows(filterOptions)
            } else {
                provider.getTvShows()
            }
            val filteredTvShows = ParentalControlUtils.filterItems(tvShows).filterIsInstance<TvShow>()

            page = 1

            _state.emit(State.SuccessLoading(filteredTvShows, filteredTvShows.isNotEmpty()))
        } catch (e: Exception) {
            Log.e("TvShowsViewModel", "getTvShows: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun applyFilters(options: TmdbFilterOptions) {
        filterOptions = options
        getTvShows()
    }

    fun loadMoreTvShows() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.value
        if (currentState is State.SuccessLoading) {
            _state.emit(State.LoadingMore)

            try {
                val provider = UserPreferences.currentProvider!!
                val tvShows = ParentalControlUtils.filterItems(
                    if (provider is TmdbProvider) provider.getFilteredTvShows(filterOptions, page + 1)
                    else provider.getTvShows(page + 1)
                ).filterIsInstance<TvShow>()

                page += 1

                _state.emit(
                    State.SuccessLoading(
                        tvShows = currentState.tvShows + tvShows,
                        hasMore = tvShows.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("TvShowsViewModel", "loadMoreTvShows: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }
}
