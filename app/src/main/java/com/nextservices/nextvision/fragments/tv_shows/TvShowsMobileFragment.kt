package com.nextservices.nextvision.fragments.tv_shows

import android.os.Bundle
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentTvShowsMobileBinding
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.providers.Provider
import com.nextservices.nextvision.ui.SpacingItemDecoration
import com.nextservices.nextvision.ui.bindMobileTmdbCatalogFilterButtons
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.viewModelsFactory
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.TmdbFilterOptions
import kotlinx.coroutines.launch

class TvShowsMobileFragment : Fragment() {

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentTvShowsMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory {
        TvShowsViewModel(
            database,
            TmdbFilterOptions(
                keywords = arguments?.getIntArray("tv_keywords")?.toSet() ?: emptySet(),
            ),
        )
    }

    private val appAdapter = AppAdapter()
    private var skeletonAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvShowsMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTvShows()
        binding.btnCatalogBack.setOnClickListener { findNavController().navigateUp() }
        bindMobileTmdbCatalogFilterButtons(
            requireContext(), true, binding.btnFilter, binding.btnResetFilter,
            { viewModel.currentFilters }, { options ->
                viewModel.applyFilters(options)
                updateFilterSummary(options)
            },
        )
        updateFilterSummary(viewModel.currentFilters)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    TvShowsViewModel.State.Loading -> binding.isLoading.apply {
                        binding.catalogSkeleton.root.visibility = View.VISIBLE
                        skeletonAnimator?.cancel()
                        skeletonAnimator = ObjectAnimator.ofFloat(binding.catalogSkeleton.root, View.ALPHA, 1f, 0.52f).apply {
                            duration = 650L
                            repeatMode = ValueAnimator.REVERSE
                            repeatCount = ValueAnimator.INFINITE
                            start()
                        }
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.GONE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    TvShowsViewModel.State.LoadingMore -> appAdapter.isLoading = true
                    is TvShowsViewModel.State.SuccessLoading -> {
                        skeletonAnimator?.cancel()
                        displayTvShows(state.tvShows, state.hasMore)
                        appAdapter.isLoading = false
                        binding.catalogSkeleton.root.visibility = View.GONE
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is TvShowsViewModel.State.FailedLoading -> {
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getTvShows()
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        skeletonAnimator?.cancel()
                        binding.catalogSkeleton.root.visibility = View.GONE
                        if (appAdapter.isLoading) {
                            appAdapter.isLoading = false
                        } else {
                            binding.isLoading.apply {
                                pbIsLoading.visibility = View.GONE
                                gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.getTvShows() }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
                                    android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done), android.widget.Toast.LENGTH_SHORT).show()
                                    doRetry()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        super.onDestroyView()
        _binding = null
    }


    private fun initializeTvShows() {
        binding.rvTvShows.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val hiddenOffset = -binding.filterBar.height.toFloat()
                    binding.filterBar.translationY = (binding.filterBar.translationY - dy)
                        .coerceIn(hiddenOffset, 0f)
                    val summaryHiddenOffset = -(binding.filterSummaryScroll.top + binding.filterSummaryScroll.height).toFloat()
                    binding.filterSummaryScroll.translationY = (binding.filterSummaryScroll.translationY - dy)
                        .coerceIn(summaryHiddenOffset, 0f)
                }
            })
            addItemDecoration(
                SpacingItemDecoration(3.dp(requireContext()))
            )
        }
    }

    private fun displayTvShows(tvShows: List<TvShow>, hasMore: Boolean) {
        appAdapter.submitList(tvShows.onEach {
            it.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
        })

        if (hasMore) {
            appAdapter.setOnLoadMoreListener { viewModel.loadMoreTvShows() }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }
    }

    private fun updateFilterSummary(options: TmdbFilterOptions) {
        binding.filterSummary.removeAllViews()
        val hasFilters = options.year != null || options.genres.isNotEmpty() ||
            options.keywords.isNotEmpty() || options.sortBy != "popularity.desc"
        binding.filterSummaryScroll.visibility = if (hasFilters) View.VISIBLE else View.GONE
        binding.rvTvShows.setPadding(
            binding.rvTvShows.paddingLeft,
            if (hasFilters) 164.dp(requireContext()) else 124.dp(requireContext()),
            binding.rvTvShows.paddingRight,
            binding.rvTvShows.paddingBottom,
        )
        if (!hasFilters) return

        val labels = buildList {
            options.year?.let { add("Year $it") }
            if (options.genres.isNotEmpty()) add("${options.genres.size} genres")
            if (options.sortBy != "popularity.desc") {
                add(options.sortBy.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() })
            }
        }
        labels.forEach { label ->
            binding.filterSummary.addView(TextView(requireContext()).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(14.dp(requireContext()), 0, 14.dp(requireContext()), 0)
                setBackgroundResource(R.drawable.bg_filter_chip_mobile)
                layoutParams = android.widget.LinearLayout.LayoutParams(-2, 32.dp(requireContext())).apply {
                    marginEnd = 8.dp(requireContext())
                }
            })
        }
    }
}