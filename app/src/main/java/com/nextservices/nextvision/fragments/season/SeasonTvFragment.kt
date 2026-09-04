package com.nextservices.nextvision.fragments.season

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentSeasonTvBinding
import com.nextservices.nextvision.models.Episode
import com.nextservices.nextvision.models.Season
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.LoggingUtils
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.viewModelsFactory
import kotlinx.coroutines.launch
import java.util.Calendar

class SeasonTvFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentSeasonTvBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<SeasonTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        SeasonViewModel(
            args.seasonId,
            args.tvShowId,
            database,
        )
    }

    private val appAdapter = AppAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeasonTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSeason()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    SeasonViewModel.State.LoadingEpisodes -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }

                    SeasonViewModel.State.LoadingMoreEpisodes -> Unit

                    is SeasonViewModel.State.SuccessLoadingEpisodes -> {
                        displaySeason(state.episodes)
                        binding.isLoading.root.visibility = View.GONE
                    }

                    is SeasonViewModel.State.FailedLoadingEpisodes -> {
                        // Auto clear cache on HTTP 409 and retry
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getSeasonEpisodes(args.seasonId)
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
                            btnIsLoadingRetry.setOnClickListener { viewModel.getSeasonEpisodes(args.seasonId) }
                            btnIsLoadingClearCache.setOnClickListener {
                                CacheUtils.clearAppCache(requireContext())
                                android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                viewModel.getSeasonEpisodes(args.seasonId)
                            }
                            btnIsLoadingErrorDetails.setOnClickListener {
                                LoggingUtils.showErrorDialog(requireContext(), state.error)
                            }
                            btnIsLoadingRetry.requestFocus()
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


    private fun initializeSeason() {
        binding.tvSeasonTitle.text = args.seasonTitle

        binding.btnPreviousSeason.setOnClickListener {
            navigateToSeason(binding.btnPreviousSeason.tag as? Season ?: return@setOnClickListener)
        }
        binding.btnNextSeason.setOnClickListener {
            navigateToSeason(binding.btnNextSeason.tag as? Season ?: return@setOnClickListener)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val seasons = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                database.seasonDao().getByTvShowId(args.tvShowId)
                    .filter { it.number > 0 }
                    .sortedBy { it.number }
            }
            val currentIndex = seasons.indexOfFirst { it.id == args.seasonId }
            val previous = seasons.getOrNull(currentIndex - 1)
            val next = seasons.getOrNull(currentIndex + 1)
            binding.btnPreviousSeason.apply {
                tag = previous
                visibility = if (previous == null) View.GONE else View.VISIBLE
            }
            binding.btnNextSeason.apply {
                tag = next
                visibility = if (next == null) View.GONE else View.VISIBLE
            }
        }

        binding.hgvEpisodes.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setItemSpacing(resources.getDimension(R.dimen.season_episodes_spacing).toInt())
        }
    }

    private fun navigateToSeason(season: Season) {
        findNavController().navigate(
            R.id.season,
            bundleOf(
                "tvShowId" to args.tvShowId,
                "tvShowTitle" to args.tvShowTitle,
                "tvShowPoster" to args.tvShowPoster,
                "tvShowBanner" to args.tvShowBanner,
                "seasonId" to season.id,
                "seasonNumber" to season.number,
                "seasonTitle" to (season.title ?: getString(R.string.season_number, season.number)),
            )
        )
    }

    private var focusedEpisodeIndex: Int? = null

    private fun displaySeason(episodes: List<Episode>) {
        val releasedEpisodes = episodes.filter { episode ->
            episode.released?.after(Calendar.getInstance()) != true
        }
        val preparedEpisodes = releasedEpisodes.onEach { episode ->
            episode.itemType = AppAdapter.Type.EPISODE_TV_ITEM
        }

        val lastWatchedIndex = releasedEpisodes
            .filter { it.watchHistory != null }
            .sortedByDescending { it.watchHistory?.lastEngagementTimeUtcMillis }
            .firstOrNull()
            ?.let { releasedEpisodes.indexOf(it) }
            ?: releasedEpisodes.indexOfLast { it.isWatched }
        val selectedEpisodeIndex = args.selectedEpisodeId?.let { selectedId ->
            releasedEpisodes.indexOfFirst { it.id == selectedId }
        } ?: -1

        appAdapter.submitList(preparedEpisodes)

        if (focusedEpisodeIndex == null) {
            val scrollIndex = when {
                selectedEpisodeIndex >= 0 -> selectedEpisodeIndex
                lastWatchedIndex == -1 -> 0
                lastWatchedIndex < releasedEpisodes.lastIndex -> lastWatchedIndex + 1
                else -> lastWatchedIndex
            }
            binding.hgvEpisodes.scrollAndFocus(scrollIndex)
            focusedEpisodeIndex = scrollIndex
        }
    }

    private fun RecyclerView.scrollAndFocus(position: Int) {
        scrollToPosition(position)
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        })
    }



}