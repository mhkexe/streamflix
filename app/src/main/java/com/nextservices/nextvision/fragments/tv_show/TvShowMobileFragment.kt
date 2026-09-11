package com.nextservices.nextvision.fragments.tv_show

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentTvShowMobileBinding
import com.nextservices.nextvision.databinding.DialogSeasonSelectorMobileBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.LoggingUtils
import com.nextservices.nextvision.utils.format
import com.nextservices.nextvision.utils.loadTvShowBanner
import com.nextservices.nextvision.utils.viewModelsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TvShowMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false
    private var selectedSeasonId: String? = null
    private var currentTvShow: TvShow? = null
    private var episodeSeasonId: String? = null
    private var renderedEpisodeCount = 0
    private var episodeLoadMoreSentinel: View? = null

    private var _binding: FragmentTvShowMobileBinding? = null
    private val binding get() = _binding!!

    private companion object {
        private const val EPISODE_PAGE_SIZE = 10
    }

    private val args by navArgs<TvShowMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        TvShowViewModel(
            id = args.id,
            database = database,
            fallbackPoster = args.poster,
            fallbackBanner = args.banner,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvShowMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTvShowBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvShowScrollView.viewTreeObserver.addOnScrollChangedListener {
            loadMoreEpisodesIfSentinelVisible()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    TvShowViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        loadingSkeleton.visibility = View.VISIBLE
                        loadingSkeleton.startAnimation(AnimationUtils.loadAnimation(requireContext(), com.nextservices.nextvision.R.anim.skeleton_pulse))
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is TvShowViewModel.State.SuccessLoading -> {
                        displayTvShow(state.tvShow)
                        binding.isLoading.loadingSkeleton.clearAnimation()
                        binding.isLoading.loadingSkeleton.visibility = View.GONE
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is TvShowViewModel.State.FailedLoading -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getTvShow(args.id)
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                            binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.getTvShow(args.id) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                    doRetry()
                                }
                                btnIsLoadingErrorDetails.setOnClickListener {
                                    LoggingUtils.showErrorDialog(requireContext(), state.error)
                                }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openEpisode(selectedEpisode: com.nextservices.nextvision.models.Episode, selectedSeason: com.nextservices.nextvision.models.Season) {
        val tvShow = currentTvShow ?: return
        findNavController().navigate(TvShowMobileFragmentDirections.actionTvShowToPlayer(
            id = selectedEpisode.id,
            title = tvShow.title,
            subtitle = "S${selectedSeason.number} E${selectedEpisode.number}  •  ${selectedEpisode.title}",
            videoType = com.nextservices.nextvision.models.Video.Type.Episode(
                id = selectedEpisode.id,
                number = selectedEpisode.number,
                title = selectedEpisode.title,
                poster = selectedEpisode.poster,
                overview = selectedEpisode.overview,
                tvShow = com.nextservices.nextvision.models.Video.Type.Episode.TvShow(
                    id = tvShow.id,
                    title = tvShow.title,
                    poster = tvShow.poster,
                    banner = tvShow.banner,
                    releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                    imdbId = tvShow.imdbId,
                ),
                season = com.nextservices.nextvision.models.Video.Type.Episode.Season(
                    number = selectedSeason.number,
                    title = selectedSeason.title ?: "",
                ),
            ),
        ))
    }

    private fun bindEpisodeView(
        selectedEpisode: com.nextservices.nextvision.models.Episode,
        currentSeason: com.nextservices.nextvision.models.Season,
    ): View {
        val episodeView = layoutInflater.inflate(
            com.nextservices.nextvision.R.layout.item_detail_episode_preview,
            binding.tvShowEpisodeList,
            false,
        )
        episodeView.findViewById<TextView>(com.nextservices.nextvision.R.id.tv_detail_episode_title).text =
            "${selectedEpisode.number}. ${selectedEpisode.title.orEmpty()}"
        episodeView.findViewById<TextView>(com.nextservices.nextvision.R.id.tv_detail_episode_description).text =
            selectedEpisode.overview.orEmpty()
        episodeView.findViewById<TextView>(com.nextservices.nextvision.R.id.tv_detail_episode_duration).text =
            selectedEpisode.runtime?.let { minutes -> "${minutes / 60}h ${minutes % 60}min" }.orEmpty()
        Glide.with(this@TvShowMobileFragment)
            .load(selectedEpisode.poster)
            .placeholder(com.nextservices.nextvision.R.drawable.detail_preview_episode)
            .centerCrop()
            .into(episodeView.findViewById(com.nextservices.nextvision.R.id.iv_detail_episode_image))
        val history = selectedEpisode.watchHistory
        val progress = episodeView.findViewById<View>(com.nextservices.nextvision.R.id.view_detail_episode_progress)
        progress.visibility = if (history != null && history.durationMillis > 0) View.VISIBLE else View.GONE
        progress.scaleX = if (history != null && history.durationMillis > 0) {
            (history.lastPlaybackPositionMillis.toFloat() / history.durationMillis).coerceIn(0f, 1f)
        } else 0f
        progress.pivotX = 0f
        episodeView.setOnClickListener { openEpisode(selectedEpisode, currentSeason) }
        episodeView.findViewById<View>(com.nextservices.nextvision.R.id.btn_detail_episode_play)
            .setOnClickListener { openEpisode(selectedEpisode, currentSeason) }
        return episodeView
    }

    // Renders episodes up to upToCount, keeping already-rendered views intact (called on initial page and on scroll-triggered loads).
    private fun appendEpisodePage(currentSeason: com.nextservices.nextvision.models.Season, upToCount: Int) {
        val sortedEpisodes = currentSeason.episodes.sortedBy { it.number }
        episodeLoadMoreSentinel?.let { binding.tvShowEpisodeList.removeView(it) }
        val targetCount = upToCount.coerceAtMost(sortedEpisodes.size)
        sortedEpisodes.subList(renderedEpisodeCount, targetCount).forEach { selectedEpisode ->
            binding.tvShowEpisodeList.addView(bindEpisodeView(selectedEpisode, currentSeason))
        }
        renderedEpisodeCount = targetCount
        episodeLoadMoreSentinel = if (renderedEpisodeCount < sortedEpisodes.size) {
            View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            }.also { binding.tvShowEpisodeList.addView(it) }
        } else null
    }

    // Loads the next page once the sentinel placed after the last rendered episode enters the viewport.
    private fun loadMoreEpisodesIfSentinelVisible() {
        val sentinel = episodeLoadMoreSentinel ?: return
        val currentSeason = currentTvShow?.seasons?.firstOrNull { it.id == episodeSeasonId } ?: return
        if (sentinel.getLocalVisibleRect(android.graphics.Rect())) {
            appendEpisodePage(currentSeason, renderedEpisodeCount + EPISODE_PAGE_SIZE)
        }
    }

    private fun renderEpisodes(currentSeason: com.nextservices.nextvision.models.Season?) {
        binding.tvShowEpisodeList.removeAllViews()
        episodeLoadMoreSentinel = null
        if (currentSeason == null) {
            episodeSeasonId = null
            renderedEpisodeCount = 0
            return
        }
        val isNewSeason = currentSeason.id != episodeSeasonId
        episodeSeasonId = currentSeason.id
        val initialCount = if (isNewSeason) EPISODE_PAGE_SIZE else renderedEpisodeCount.coerceAtLeast(EPISODE_PAGE_SIZE)
        renderedEpisodeCount = 0
        appendEpisodePage(currentSeason, initialCount)
        binding.tvShowScrollView.post { loadMoreEpisodesIfSentinelVisible() }
    }

    private fun displayTvShow(tvShow: TvShow) {
        currentTvShow = tvShow
        binding.ivTvShowBanner.loadTvShowBanner(tvShow) {
            transition(DrawableTransitionOptions.withCrossFade())
        }
        binding.tvTvShowTitle.apply {
            text = tvShow.title
            visibility = if (tvShow.logo.isNullOrBlank()) View.VISIBLE else View.GONE
        }
        binding.ivTvShowLogo.apply {
            if (tvShow.logo.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                Glide.with(this)
                    .load(tvShow.logo)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            visibility = View.GONE
                            binding.tvTvShowTitle.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            binding.tvTvShowTitle.visibility = View.GONE
                            return false
                        }
                    })
                    .into(this)
            }
        }
        val episode = tvShow.episodeToWatch ?: tvShow.seasons
            .firstOrNull { it.episodes.isNotEmpty() }
            ?.episodes
            ?.firstOrNull()
        val season = tvShow.seasons.firstOrNull { it.episodes.any { item -> item.id == episode?.id } }
        binding.tvTvShowAgeRating.visibility = View.GONE
        binding.tvTvShowMetadata.text = listOfNotNull(
            tvShow.released?.format("yyyy"),
            tvShow.genres.firstOrNull()?.name,
            tvShow.ageRating?.takeIf { it.isNotBlank() } ?: "NR",
            tvShow.rating?.let { String.format(java.util.Locale.ROOT, "%.1f", it) },
        ).joinToString("  •  ")
        binding.tvTvShowOverview.text = tvShow.overview
        val recommendationSlots = listOf(
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.tv_recommendation_1),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.tv_recommendation_2),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.tv_recommendation_3),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.tv_recommendation_4),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.tv_recommendation_5),
        )
        recommendationSlots.forEach { it.visibility = View.GONE }
        tvShow.recommendations.take(5).forEachIndexed { index, recommendation ->
            recommendationSlots[index].apply {
                visibility = View.VISIBLE
                val poster = when (recommendation) {
                    is Movie -> recommendation.poster
                    is TvShow -> recommendation.poster
                }
                Glide.with(this@TvShowMobileFragment)
                    .load(poster)
                    .placeholder(com.nextservices.nextvision.R.drawable.detail_preview_recommendation)
                    .centerCrop()
                    .into(findViewById(com.nextservices.nextvision.R.id.iv_detail_recommendation_image))
                setOnClickListener {
                    when (recommendation) {
                        is Movie -> findNavController().navigate(
                            TvShowMobileFragmentDirections.actionTvShowToMovie(recommendation.id)
                        )
                        is TvShow -> findNavController().navigate(
                            TvShowMobileFragmentDirections.actionTvShowToTvShow(
                                recommendation.id,
                                recommendation.poster,
                                recommendation.banner,
                            )
                        )
                    }
                }
            }
        }
        binding.btnTvShowWatchNow.apply {
            if (episode != null) {
                isEnabled = true
                alpha = 1f
                text = if (episode.watchHistory != null) getString(com.nextservices.nextvision.R.string.tv_show_resume_season_episode, season?.number ?: 1, episode.number)
                else getString(com.nextservices.nextvision.R.string.tv_show_watch_season_episode, season?.number ?: 1, episode.number)
                setOnClickListener {
                    findNavController().navigate(TvShowMobileFragmentDirections.actionTvShowToPlayer(
                        id = episode.id,
                        title = tvShow.title,
                        subtitle = "S${season?.number ?: 1} E${episode.number}  •  ${episode.title}",
                        videoType = com.nextservices.nextvision.models.Video.Type.Episode(
                            id = episode.id,
                            number = episode.number,
                            title = episode.title,
                            poster = episode.poster,
                            overview = episode.overview,
                            tvShow = com.nextservices.nextvision.models.Video.Type.Episode.TvShow(
                                id = tvShow.id,
                                title = tvShow.title,
                                poster = tvShow.poster,
                                banner = tvShow.banner,
                                releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                                imdbId = tvShow.imdbId,
                            ),
                            season = com.nextservices.nextvision.models.Video.Type.Episode.Season(
                                number = season?.number ?: 1,
                                title = season?.title ?: "",
                            ),
                        ),
                    ))
                }
            } else {
                isEnabled = false
                alpha = 0.6f
                text = getString(com.nextservices.nextvision.R.string.tv_show_loading_episodes)
                setOnClickListener(null)
            }
        }
        var selectedSeason = selectedSeasonId?.let { id -> tvShow.seasons.firstOrNull { it.id == id } }
            ?: season
            ?: tvShow.seasons.firstOrNull()
        selectedSeasonId = selectedSeason?.id
        binding.btnTvShowSeasonSelector.text = selectedSeason?.let {
            "Season ${it.number}    ${it.episodes.size} episodes"
        } ?: "Season 1    0 episodes"
        renderEpisodes(selectedSeason)
        binding.btnTvShowSeasonSelector.setOnClickListener {
            val dialog = BottomSheetDialog(requireContext())
            val sheet = DialogSeasonSelectorMobileBinding.inflate(layoutInflater)
            dialog.setContentView(sheet.root)
            val density = resources.displayMetrics.density
            fun Int.dp() = (this * density).toInt()
            val selectedNumber = selectedSeason?.number
            sheet.btnClose.setOnClickListener { dialog.dismiss() }
            tvShow.seasons.forEach { availableSeason ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 10.dp(), 0, 10.dp())
                    setOnClickListener {
                        selectedSeason = availableSeason
                        selectedSeasonId = availableSeason.id
                        viewModel.loadSeason(tvShow, availableSeason)
                        binding.btnTvShowSeasonSelector.text = "Season ${availableSeason.number}    ${availableSeason.episodes.size} episodes"
                        renderEpisodes(selectedSeason)
                        dialog.dismiss()
                    }
                }
                val labels = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                TextView(requireContext()).apply {
                    text = "Season ${availableSeason.number}"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    labels.addView(this)
                }
                TextView(requireContext()).apply {
                    text = "${availableSeason.episodes.size} episodes"
                    setTextColor(Color.rgb(165, 165, 170))
                    textSize = 12f
                    labels.addView(this)
                }
                row.addView(labels)
                if (availableSeason.number == selectedNumber) {
                    TextView(requireContext()).apply {
                        text = "✓"
                        setTextColor(Color.WHITE)
                        textSize = 24f
                        row.addView(this, LinearLayout.LayoutParams(48.dp(), -2))
                    }
                }
                sheet.seasonOptions.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            dialog.show()
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        binding.btnTvShowFavorite.apply {
            isSelected = tvShow.isFavorite
            setOnClickListener {
                val favorite = !tvShow.isFavorite
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    database.tvShowDao().upsertFavorite(tvShow, favorite)
                    tvShow.isFavorite = favorite
                    launch(Dispatchers.Main) { binding.btnTvShowFavorite.isSelected = favorite }
                }
            }
        }
        val castSlots = listOf(
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.cast_preview_1),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.cast_preview_2),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.cast_preview_3),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.cast_preview_4),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.cast_preview_5),
        )
        castSlots.forEach { it.visibility = View.GONE }
        tvShow.cast.take(5).forEachIndexed { index, person ->
            castSlots[index].apply {
                visibility = View.VISIBLE
                findViewById<android.widget.TextView>(com.nextservices.nextvision.R.id.tv_cast_preview_name).text = person.name
                findViewById<android.widget.TextView>(com.nextservices.nextvision.R.id.tv_cast_preview_role).text = person.role
                Glide.with(this@TvShowMobileFragment)
                    .load(person.image)
                    .placeholder(com.nextservices.nextvision.R.drawable.ic_person_placeholder)
                    .centerCrop()
                    .into(findViewById(com.nextservices.nextvision.R.id.iv_cast_preview_image))
                setOnClickListener {
                    findNavController().navigate(TvShowMobileFragmentDirections.actionTvShowToPeople(
                        id = person.id,
                        name = person.name,
                        image = person.image,
                    ))
                }
            }
        }
    }
}
