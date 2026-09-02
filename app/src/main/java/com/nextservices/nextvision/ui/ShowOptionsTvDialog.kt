package com.nextservices.nextvision.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.DialogShowOptionsTvBinding
import com.nextservices.nextvision.fragments.home.HomeTvFragment
import com.nextservices.nextvision.fragments.home.HomeTvFragmentDirections
import com.nextservices.nextvision.models.Episode
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.models.Video
import com.nextservices.nextvision.utils.loadMoviePoster
import com.nextservices.nextvision.utils.loadTvShowPoster
import com.nextservices.nextvision.utils.ArtworkRepair
import com.nextservices.nextvision.utils.UserDataCache
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.format
import com.nextservices.nextvision.utils.getCurrentFragment
import com.nextservices.nextvision.utils.toActivity
import com.nextservices.nextvision.providers.Provider
import com.nextservices.nextvision.providers.IptvProvider
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShowOptionsTvDialog(
    context: Context,
    show: AppAdapter.Item,
) : Dialog(context) {

    private val binding = DialogShowOptionsTvBinding.inflate(LayoutInflater.from(context))

    private val database: AppDatabase
        get() = AppDatabase.getInstance(context)

    override fun show() {
        if (context.toActivity()?.isFinishing == true || context.toActivity()?.isDestroyed == true) return
        runCatching { super.show() }
            .onFailure { error ->
                if (error is WindowManager.BadTokenException || error is IllegalStateException) return@onFailure
                throw error
            }
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes?.also { params -> params.gravity = Gravity.END }
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.35).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private fun navController() = context.toActivity()?.getCurrentFragment()?.let {
        NavHostFragment.findNavController(it)
    } ?: (context.toActivity()?.supportFragmentManager
        ?.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment)?.navController

    private fun checkProviderAndRun(show: AppAdapter.Item, action: () -> Unit) {
        val providerName = when(show){
            is Movie -> show.providerName
            is TvShow -> show.providerName
            is Episode -> show.tvShow?.providerName
            else -> null
        }

        if (!providerName.isNullOrBlank() && providerName != UserPreferences.currentProvider?.name) {
            Provider.providers.keys.find { it.name == providerName }?.let {
                UserPreferences.currentProvider = it
            }
        }
        action()
    }

    private fun openDetails(show: AppAdapter.Item) {
        val controller = navController() ?: return
        when (show) {
            is Movie -> controller.navigate(R.id.movie, Bundle().apply { putString("id", show.id) })
            is TvShow -> controller.navigate(R.id.tv_show, Bundle().apply {
                putString("id", show.id)
                putString("poster", show.poster)
                putString("banner", show.banner)
            })
        }
        hide()
    }

    private fun playMovie(movie: Movie) {
        navController()?.navigate(
            R.id.action_global_player,
            Bundle().apply {
                putString("id", movie.id)
                putString("title", movie.title)
                putString("subtitle", movie.released?.format("yyyy") ?: "")
                putSerializable("videoType", Video.Type.Movie(
                    id = movie.id,
                    title = movie.title,
                    releaseDate = movie.released?.format("yyyy-MM-dd") ?: "",
                    poster = movie.poster ?: movie.banner ?: "",
                    imdbId = movie.imdbId,
                ))
            },
        )
        hide()
    }

    private fun playTvShow(tvShow: TvShow) {
        val controller = navController() ?: return
        val episode = tvShow.episodeToWatch
        if (episode == null || Provider.providers.keys.find {
                it.name == (tvShow.providerName ?: UserPreferences.currentProvider?.name)
            } is IptvProvider
        ) {
            val videoType = Video.Type.Episode(
                id = tvShow.id,
                number = 1,
                title = tvShow.title,
                poster = tvShow.poster,
                overview = tvShow.overview,
                tvShow = Video.Type.Episode.TvShow(
                    id = tvShow.id,
                    title = tvShow.title,
                    poster = tvShow.poster,
                    banner = tvShow.banner,
                    releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                    imdbId = tvShow.imdbId,
                ),
                season = Video.Type.Episode.Season(number = 1, title = "Live"),
            )
            controller.navigate(R.id.action_global_player, Bundle().apply {
                putString("id", tvShow.id)
                putString("title", tvShow.title)
                putString("subtitle", tvShow.title)
                putSerializable("videoType", videoType)
            })
        } else {
            val videoType = Video.Type.Episode(
                id = episode.id,
                number = episode.number,
                title = episode.title,
                poster = episode.poster,
                overview = episode.overview,
                tvShow = Video.Type.Episode.TvShow(
                    id = tvShow.id,
                    title = tvShow.title,
                    poster = tvShow.poster,
                    banner = tvShow.banner,
                    releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                    imdbId = tvShow.imdbId,
                ),
                season = Video.Type.Episode.Season(
                    number = episode.season?.number ?: 1,
                    title = episode.season?.title ?: "",
                ),
            )
            controller.navigate(R.id.action_global_player, Bundle().apply {
                putString("id", episode.id)
                putString("title", tvShow.title)
                putString("subtitle", tvShow.title)
                putSerializable("videoType", videoType)
            })
        }
        hide()
    }

    init {
        setContentView(binding.root)

        binding.btnOptionCancel.setOnClickListener {
            hide()
        }

        when (show) {
            is Episode -> displayEpisode(show)
            is Movie -> displayMovie(show)
            is TvShow -> displayTvShow(show)
        }

        binding.btnOptionShowWatched.visibility = View.GONE
        binding.btnOptionEpisodeMarkAllPreviousWatched.visibility = View.GONE
            binding.btnOptionProgramClear.visibility = View.GONE
    }


    private fun displayEpisode(episode: Episode) {
        binding.tvOptionsShowTitle.text = episode.tvShow?.title ?: ""

        binding.tvShowSubtitle.text = episode.season?.takeIf { it.number != 0 }?.let { season ->
            context.getString(
                R.string.episode_item_info,
                season.number,
                episode.number,
                episode.title ?: context.getString(
                    R.string.episode_number,
                    episode.number
                )
            )
        } ?: context.getString(
            R.string.episode_item_info_episode_only,
            episode.number,
            episode.title ?: context.getString(
                R.string.episode_number,
                episode.number
            )
        )


        binding.btnOptionEpisodeOpenTvShow.apply {
            setOnClickListener {
                when (val fragment = context.toActivity()?.getCurrentFragment()) {
                    is HomeTvFragment -> episode.tvShow?.let { tvShow ->
                        NavHostFragment.findNavController(fragment).navigate(
                            HomeTvFragmentDirections.actionHomeToTvShow(
                                id = tvShow.id,
                                poster = tvShow.poster,
                                banner = tvShow.banner,
                            )
                        )
                    }
                }
                hide()
            }

            visibility = when (context.toActivity()?.getCurrentFragment()) {
                is HomeTvFragment -> View.VISIBLE
                else -> View.GONE
            }

            requestFocus()
        }

        binding.btnOptionShowFavorite.visibility = View.GONE

        binding.btnOptionShowWatched.apply {
            setOnClickListener {
                checkProviderAndRun(episode) {
                    val currentProvider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    val updatedEpisode = episode.copy().apply {
                        merge(episode)
                        isWatched = !isWatched
                        if (isWatched) {
                            watchedDate = Calendar.getInstance()
                            watchHistory = null
                        } else {
                            watchedDate = null
                        }
                    }
                    AppDatabase.getInstance(context).episodeDao().save(updatedEpisode)
                    UserDataCache.syncEpisodeToCache(context, currentProvider, updatedEpisode)

                    // NUOVA LOGICA: Aggiorna lo stato isWatching della serie TV madre
                    episode.tvShow?.let { tvShow ->
                        val episodeDao = AppDatabase.getInstance(context).episodeDao()
                        val isStillWatching = episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)

                        // Se l'episodio è stato marcato come VISTO E non ci sono altri
                        // episodi con cronologia, impostiamo isWatching a false.
                        if (updatedEpisode.isWatched && !isStillWatching) {
                            AppDatabase.getInstance(context).tvShowDao().save(tvShow.copy().apply {
                                merge(tvShow)
                                isWatching = false
                            })
                            UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                        }
                    }
                    if (updatedEpisode.isWatched) {
                        UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                    }
                }

                hide()
            }

            text = when {
                episode.isWatched -> context.getString(R.string.option_show_unwatched)
                else -> context.getString(R.string.option_show_watched)
            }
            visibility = View.VISIBLE
        }
        binding.btnOptionEpisodeMarkAllPreviousWatched.apply {
            setOnClickListener {
                checkProviderAndRun(episode) {
                    val episodeDao = AppDatabase.getInstance(context).episodeDao()
                    val episodeNumber = episode.number
                    val tvShowId = episode.tvShow?.id ?: return@checkProviderAndRun
                    val allEpisodes = episodeDao.getEpisodesByTvShowIdAndSeason(tvShowId, episode.season?.id).filter { it.number <= episodeNumber }
                    val targetState = !episode.isWatched // If current is watched, we unwatch; else, we mark watched
                    val now = Calendar.getInstance()
                    val currentProvider = UserPreferences.currentProvider ?: return@checkProviderAndRun

                    for (ep in allEpisodes) {
                        if (ep.isWatched != targetState) {
                            val updatedEp = ep.copy().apply {
                                merge(ep)
                                isWatched = targetState
                                watchedDate = if (targetState) now else null
                                watchHistory = if (targetState) null else watchHistory
                            }
                            episodeDao.save(updatedEp)
                            UserDataCache.syncEpisodeToCache(context, currentProvider, updatedEp)
                        }
                    }

                    // Logica aggiuntiva per isWatching:
                    // Se l'obiettivo era marcare come VISTO, e non ci sono cronologie, si imposta isWatching a false.
                    if (targetState) {
                        episode.tvShow?.let { tvShow ->
                            if (!episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)) {
                                AppDatabase.getInstance(context).tvShowDao().save(tvShow.copy().apply {
                                    merge(tvShow)
                                    isWatching = false
                                })
                                UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                            }
                        }
                        UserDataCache.removeEpisodeFromContinueWatching(context, currentProvider, episode.id)
                    }
                    // Se l'obiettivo era marcare come NON VISTO, impostiamo isWatching a true per farlo riapparire.
                    if (!targetState) {
                        episode.tvShow?.let { tvShow ->
                            AppDatabase.getInstance(context).tvShowDao().save(tvShow.copy().apply {
                                merge(tvShow)
                                isWatching = true
                            })
                        }
                    }
                }

                hide()
            }

            text = when {
                episode.isWatched -> context.getString(R.string.option_show_mark_all_previous_unwatched)
                else -> context.getString(R.string.option_show_mark_all_previous_watched)
            }
            visibility = View.VISIBLE
        }

        binding.btnOptionProgramClear.apply {
            setOnClickListener {
                checkProviderAndRun(episode) {
                    val provider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    val updatedEpisode = episode.copy().apply {
                        merge(episode)
                        watchHistory = null
                    }
                    AppDatabase.getInstance(context).episodeDao().save(updatedEpisode)
                    UserDataCache.syncEpisodeToCache(context, provider, updatedEpisode)
                    
                    episode.tvShow?.let { tvShow ->
                        // Rimuoviamo isWatching solo se NON ci sono altri episodi in corso
                        val episodeDao = AppDatabase.getInstance(context).episodeDao()
                        if (!episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)) {
                            AppDatabase.getInstance(context).tvShowDao().save(tvShow.copy().apply {
                                merge(tvShow)
                                isWatching = false
                            })
                            UserDataCache.removeEpisodeFromContinueWatching(context, provider, episode.id)
                        }
                    }
                    UserDataCache.removeEpisodeFromContinueWatching(context, provider, episode.id)
                }

                hide()
            }

            visibility = when {
                episode.watchHistory != null -> View.VISIBLE
                episode.tvShow?.isWatching ?: false -> View.VISIBLE
                else -> View.GONE
            }
        }
    }

    private fun displayMovie(movie: Movie) {
        binding.tvOptionsShowTitle.text = movie.title

        binding.tvShowSubtitle.text = movie.released?.format("yyyy")

        binding.btnOptionPlay.apply {
            text = if (movie.watchHistory != null) context.getString(R.string.movie_resume)
            else context.getString(R.string.movie_watch_now)
            setOnClickListener {
                checkProviderAndRun(movie) { playMovie(movie) }
            }
            requestFocus()
        }
        binding.btnOptionViewDetails.apply {
            setOnClickListener { openDetails(movie) }
            visibility = View.VISIBLE
        }


        binding.btnOptionEpisodeOpenTvShow.visibility = View.GONE

        val freshMovie = movie

        binding.btnOptionShowFavorite.apply {
            setOnClickListener {
                checkProviderAndRun(freshMovie) {
                    val provider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    context.toActivity()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val dao = database.movieDao()
                        val current = dao.getById(movie.id)?.isFavorite ?: false
                        val newValue = !current
                        val resolvedMovie = ArtworkRepair.resolveMovieForFavorite(context, movie, newValue)
                        dao.upsertFavorite(resolvedMovie, newValue)
                        if (newValue) {
                            UserDataCache.addMovieToFavorites(context, provider, resolvedMovie.copy().apply { isFavorite = true })
                        } else {
                            UserDataCache.removeMovieFromFavorites(context, provider, freshMovie.id)
                        }
                    }
                }

                hide()
            }

            text = when {
                freshMovie.isFavorite -> context.getString(R.string.option_show_unfavorite)
                else -> context.getString(R.string.option_show_favorite)
            }
            visibility = View.VISIBLE

        }

        binding.btnOptionShowWatched.apply {
            setOnClickListener {
                checkProviderAndRun(freshMovie) {
                    val provider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    val updatedMovie = freshMovie.copy().apply {
                        merge(freshMovie)
                        isWatched = !isWatched
                        if (isWatched) {
                            watchedDate = Calendar.getInstance()
                            watchHistory = null
                        } else {
                            watchedDate = null
                        }
                    }
                    AppDatabase.getInstance(context).movieDao().save(updatedMovie)
                    UserDataCache.syncMovieToCache(context, provider, updatedMovie)
                    
                    if (updatedMovie.isWatched) {
                        UserDataCache.removeMovieFromContinueWatching(context, provider, freshMovie.id)
                    }
                }

                hide()
            }

            text = when {
                freshMovie.isWatched -> context.getString(R.string.option_show_unwatched)
                else -> context.getString(R.string.option_show_watched)
            }
            visibility = View.VISIBLE
        }

        binding.btnOptionProgramClear.apply {
            setOnClickListener {
                checkProviderAndRun(freshMovie) {
                    val provider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    val updatedMovie = freshMovie.copy().apply {
                        merge(freshMovie)
                        watchHistory = null
                    }
                    AppDatabase.getInstance(context).movieDao().save(updatedMovie)
                    UserDataCache.syncMovieToCache(context, provider, updatedMovie)
                    UserDataCache.removeMovieFromContinueWatching(context, provider, freshMovie.id)
                }

                hide()
            }

            visibility = when {
                freshMovie.watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }
    }

    private fun displayTvShow(tvShow: TvShow) {
        binding.tvOptionsShowTitle.text = tvShow.title

        binding.tvShowSubtitle.text = tvShow.released?.format("yyyy")

        binding.btnOptionPlay.apply {
            val episode = tvShow.episodeToWatch
            text = if (episode?.watchHistory != null) {
                context.getString(R.string.movie_resume)
            } else {
                context.getString(R.string.movie_watch_now)
            }
            setOnClickListener {
                checkProviderAndRun(tvShow) { playTvShow(tvShow) }
            }
            requestFocus()
        }
        binding.btnOptionViewDetails.apply {
            setOnClickListener { openDetails(tvShow) }
            visibility = View.VISIBLE
        }


        binding.btnOptionEpisodeOpenTvShow.visibility = View.GONE

        val freshTvShow = tvShow

        binding.btnOptionShowFavorite.apply {
            setOnClickListener {
                checkProviderAndRun(freshTvShow) {
                    val provider = UserPreferences.currentProvider ?: return@checkProviderAndRun
                    context.toActivity()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val dao = database.tvShowDao()
                        val current = dao.getById(tvShow.id)?.isFavorite ?: false
                        val newValue = !current
                        val resolvedTvShow = ArtworkRepair.resolveTvShowForFavorite(context, tvShow, newValue)

                        dao.upsertFavorite(resolvedTvShow, newValue)
                        if (newValue) {
                            UserDataCache.syncTvShowToCache(
                                context,
                                provider,
                                resolvedTvShow.copy().apply { isFavorite = true })
                        } else {
                            UserDataCache.removeTvShowFromFavorites(context, provider, freshTvShow.id)
                        }
                    }
                }

                hide()
            }

            text = when {
                freshTvShow.isFavorite -> context.getString(R.string.option_show_unfavorite)
                else -> context.getString(R.string.option_show_favorite)
            }
            visibility = View.VISIBLE

        }

        binding.btnOptionShowWatched.visibility = View.GONE

        binding.btnOptionProgramClear.visibility = View.GONE
    }
}
