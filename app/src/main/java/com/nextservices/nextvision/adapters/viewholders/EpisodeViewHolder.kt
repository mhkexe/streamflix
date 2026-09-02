package com.nextservices.nextvision.adapters.viewholders

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nextservices.nextvision.R
import com.nextservices.nextvision.databinding.ItemEpisodeContinueWatchingMobileBinding
import com.nextservices.nextvision.databinding.ItemEpisodeContinueWatchingTvBinding
import com.nextservices.nextvision.databinding.ItemEpisodeMobileBinding
import com.nextservices.nextvision.databinding.ItemEpisodeTvBinding
import com.nextservices.nextvision.fragments.home.HomeMobileFragmentDirections
import com.nextservices.nextvision.fragments.home.HomeTvFragmentDirections
import com.nextservices.nextvision.fragments.season.SeasonMobileFragmentDirections
import com.nextservices.nextvision.fragments.season.SeasonTvFragmentDirections
import com.nextservices.nextvision.models.Episode
import com.nextservices.nextvision.models.Video
import com.nextservices.nextvision.ui.ShowOptionsMobileDialog
import com.nextservices.nextvision.ui.ShowOptionsTvDialog
import com.nextservices.nextvision.utils.EpisodeManager
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.format
import com.nextservices.nextvision.utils.getCurrentFragment
import com.nextservices.nextvision.utils.loadTvShowBanner
import com.nextservices.nextvision.utils.toActivity

class EpisodeViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private fun formatRuntime(runtime: Int?): String {
        if (runtime == null || runtime <= 0) return "--"
        val hours = runtime / 60
        val minutes = runtime % 60
        return if (hours > 0) {
            context.getString(R.string.tv_show_runtime_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.tv_show_runtime_minutes, minutes)
        }
    }

    private val context = itemView.context
    private lateinit var episode: Episode

    private fun formatWatchedTime(positionMillis: Long, durationMillis: Long): String {
        val totalSeconds = (positionMillis / 1000).coerceAtLeast(0)
        val durationSeconds = (durationMillis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val durationHours = durationSeconds / 3600
        val durationMinutes = (durationSeconds % 3600) / 60
        val durationRemainderSeconds = durationSeconds % 60
        val watched = if (hours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
        else "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
        val total = if (durationHours > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, durationHours, durationMinutes, durationRemainderSeconds)
        else "%d:%02d".format(java.util.Locale.ROOT, durationMinutes, durationRemainderSeconds)
        return "$watched / $total"
    }

    fun bind(episode: Episode) {
        this.episode = episode

        when (_binding) {
            is ItemEpisodeMobileBinding -> displayMobileItem(_binding)
            is ItemEpisodeTvBinding -> displayTvItem(_binding)
            is ItemEpisodeContinueWatchingMobileBinding -> displayContinueWatchingMobileItem(_binding)
            is ItemEpisodeContinueWatchingTvBinding -> displayContinueWatchingTvItem(_binding)
        }
    }


    private fun displayMobileItem(binding: ItemEpisodeMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    SeasonMobileFragmentDirections.actionSeasonToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episode.id,
                            number = episode.number,
                            title = episode.title,
                            poster = episode.poster,
                            overview = episode.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = episode.tvShow?.id ?: "",
                                title = episode.tvShow?.title ?: "",
                                poster = episode.tvShow?.poster,
                                banner = episode.tvShow?.banner,
                                releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
                                imdbId = episode.tvShow?.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, episode)
                    .show()
                true
            }
        }

        binding.ivEpisodePoster.apply {
            clipToOutline = true
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }
        binding.ivEpisodeWatchedRibbon.visibility = if (episode.isWatched) View.VISIBLE else View.GONE

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeInfo.text = context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeRuntime.text = formatRuntime(episode.runtime ?: episode.tvShow?.runtime)

        binding.tvEpisodeTitle.text = episode.title ?: context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeReleased.apply {
            text = episode.released?.let { " • ${it.format("yyyy-MM-dd")}" }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.tvEpisodeOverview.text = episode.overview ?: ""
    }

    private fun displayTvItem(binding: ItemEpisodeTvBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    SeasonTvFragmentDirections.actionSeasonToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episode.id,
                            number = episode.number,
                            title = episode.title,
                            poster = episode.poster,
                            overview = episode.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = episode.tvShow?.id ?: "",
                                title = episode.tvShow?.title ?: "",
                                poster = episode.tvShow?.poster,
                                banner = episode.tvShow?.banner,
                                releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
                                imdbId = episode.tvShow?.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, episode)
                    .show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
                binding.ivEpisodePlay.visibility = if (hasFocus) View.VISIBLE else View.GONE
                binding.tvEpisodeTitle.visibility = if (hasFocus) View.VISIBLE else View.GONE
                binding.tvEpisodeOverview.visibility = if (hasFocus) View.VISIBLE else View.GONE
            }
        }

        binding.ivEpisodePoster.apply {
            clipToOutline = true
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .fallback(R.drawable.glide_fallback_cover)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }
        val isFocused = binding.root.hasFocus()
        binding.ivEpisodePlay.visibility = if (isFocused) View.VISIBLE else View.GONE
        binding.tvEpisodeTitle.visibility = if (isFocused) View.VISIBLE else View.GONE
        binding.tvEpisodeOverview.visibility = if (isFocused) View.VISIBLE else View.GONE
        binding.ivEpisodeWatchedRibbon.visibility = if (episode.isWatched) View.VISIBLE else View.GONE

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeInfo.text = context.getString(
            R.string.episode_number,
            episode.number
        )

        binding.tvEpisodeRuntime.text = formatRuntime(episode.runtime ?: episode.tvShow?.runtime)

        binding.tvEpisodeTitle.text = episode.title ?: context.getString(
            R.string.episode_number,
            episode.number
        )
        binding.tvEpisodeOverview.text = episode.overview ?: ""
    }

    private fun displayContinueWatchingMobileItem(binding: ItemEpisodeContinueWatchingMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    HomeMobileFragmentDirections.actionHomeToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episode.id,
                            number = episode.number,
                            title = episode.title,
                            poster = episode.poster,
                            overview = episode.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = episode.tvShow?.id ?: "",
                                title = episode.tvShow?.title ?: "",
                                poster = episode.tvShow?.poster,
                                banner = episode.tvShow?.banner,
                                releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
                                imdbId = episode.tvShow?.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, episode)
                    .show()
                true
            }
        }

        binding.ivEpisodeTvShowPoster.apply {
            clipToOutline = true
            loadContinueWatchingArtwork()
        }

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeTvShowTitle.text = episode.tvShow?.title ?: ""

        binding.tvEpisodeInfo.text = episode.season?.takeIf { it.number != 0 }?.let { season ->
            context.getString(R.string.home_continue_watching_episode, season.number, episode.number)
        } ?: context.getString(R.string.home_continue_watching_episode_only, episode.number)
    }

    private fun displayContinueWatchingTvItem(binding: ItemEpisodeContinueWatchingTvBinding) {
        binding.root.apply {
            setOnClickListener {
                findNavController().navigate(
                    HomeTvFragmentDirections.actionHomeToPlayer(
                        id = episode.id,
                        title = episode.tvShow?.title ?: "",
                        subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episode.number,
                                episode.title ?: context.getString(
                                    R.string.episode_number,
                                    episode.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episode.number,
                            episode.title ?: context.getString(
                                R.string.episode_number,
                                episode.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episode.id,
                            number = episode.number,
                            title = episode.title,
                            poster = episode.poster,
                            overview = episode.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = episode.tvShow?.id ?: "",
                                title = episode.tvShow?.title ?: "",
                                poster = episode.tvShow?.poster,
                                banner = episode.tvShow?.banner,
                                releaseDate = episode.tvShow?.released?.format("yyyy-MM-dd"),
                                imdbId = episode.tvShow?.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episode.season?.number ?: 0,
                                title = episode.season?.title,
                            ),
                        ),
                    )
                )
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, episode)
                    .show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true

                // Continue Watching keeps the rotating Home background stable.
            }
        }

        binding.ivEpisodeTvShowPoster.apply {
            clipToOutline = true
            loadContinueWatchingArtwork(withFallback = true)
        }

        binding.pbEpisodeProgress.apply {
            val watchHistory = episode.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                episode.isWatched -> 100
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                episode.isWatched -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.tvEpisodeTvShowTitle.text = episode.tvShow?.title ?: ""

        binding.tvEpisodeInfo.text = episode.season?.takeIf { it.number != 0 }?.let { season ->
            context.getString(R.string.home_continue_watching_episode, season.number, episode.number)
        } ?: context.getString(R.string.home_continue_watching_episode_only, episode.number)
        binding.tvEpisodeWatched.apply {
            episode.watchHistory?.let {
                text = formatWatchedTime(it.lastPlaybackPositionMillis, it.durationMillis)
                visibility = View.VISIBLE
            } ?: run { visibility = View.GONE }
        }
    }

    private fun ImageView.loadContinueWatchingArtwork(withFallback: Boolean = false) {
        val tvShow = episode.tvShow
        if (tvShow == null) {
            Glide.with(context)
                .load(episode.poster)
                .error(R.drawable.glide_fallback_cover)
                .apply {
                    if (withFallback) fallback(R.drawable.glide_fallback_cover)
                }
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
            return
        }

        loadTvShowBanner(tvShow) {
            error(R.drawable.glide_fallback_cover)
            apply {
                if (withFallback) fallback(R.drawable.glide_fallback_cover)
            }
            centerCrop()
            transition(DrawableTransitionOptions.withCrossFade())
        }
    }
}
