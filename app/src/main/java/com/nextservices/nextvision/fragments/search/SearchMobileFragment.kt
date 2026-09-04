package com.nextservices.nextvision.fragments.search

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.Typeface
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.databinding.FragmentSearchMobileBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.ui.SpacingItemDecoration
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.VoiceRecognitionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchMobileFragment : Fragment() {

    private companion object {
        const val SEARCH_HISTORY_KEY = "mobile_search_history"
        const val MAX_HISTORY_ITEMS = 5
    }

    private var _binding: FragmentSearchMobileBinding? = null
    private val binding get() = _binding!!
    private val resultsAdapter = AppAdapter()
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var activeQuery = ""
    private var nextPage = 1
    private var canLoadMore = false
    private var isLoadingMore = false
    private lateinit var voiceHelper: VoiceRecognitionHelper
    private var voicePulseAnimator: ObjectAnimator? = null
    private var searchLoadingAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchMobileBinding.inflate(inflater, container, false)
        voiceHelper = VoiceRecognitionHelper(
            this,
            onResult = { result ->
                _binding?.let {
                    it.etSearchQuery.setText(result)
                    it.etSearchQuery.setSelection(it.etSearchQuery.length())
                }
            },
            onError = { message ->
                if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            },
            onListeningStateChanged = { listening ->
                _binding?.btnSearchVoice?.isActivated = listening
                if (listening) startVoicePulse() else stopVoicePulse()
            },
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.alpha = 0f
        binding.root.translationY = 18f
        binding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        binding.btnSearchBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnSearchVoice.setOnClickListener { voiceHelper.startWithPermissionCheck() }
        binding.rvSearchResults.apply {
            adapter = resultsAdapter
            addItemDecoration(SpacingItemDecoration(6.dp(requireContext())))
            resultsAdapter.loadMoreEnabled = false
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    if (dy > 0) resultsAdapter.loadMoreEnabled = true
                }
            })
        }
        resultsAdapter.onMovieClickListener = { movie ->
            findNavController().navigate(R.id.action_global_movie, bundleOf("id" to movie.id))
        }
        resultsAdapter.onTvShowClickListener = { show ->
            findNavController().navigate(
                R.id.action_global_tv_show,
                bundleOf("id" to show.id, "poster" to show.poster, "banner" to show.banner),
            )
        }

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleSearch(text?.toString().orEmpty())
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        })
        arguments?.getString("query")?.takeIf { it.isNotBlank() }?.let {
            binding.etSearchQuery.setText(it)
            binding.etSearchQuery.setSelection(binding.etSearchQuery.length())
        }
        binding.etSearchQuery.requestFocus()
        binding.etSearchQuery.post {
            val inputMethodManager = requireContext().getSystemService(InputMethodManager::class.java)
            inputMethodManager?.showSoftInput(binding.etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        }
        renderSearchHistory()
        if (arguments?.getBoolean("start_voice") == true) {
            binding.root.post { voiceHelper.startWithPermissionCheck() }
        }
    }

    private fun scheduleSearch(value: String) {
        searchJob?.cancel()
        val query = value.trim()
        if (query.isEmpty()) {
            activeQuery = ""
            nextPage = 1
            canLoadMore = false
            isLoadingMore = false
            loadMoreJob?.cancel()
            resultsAdapter.setOnLoadMoreListener(null)
            resultsAdapter.loadMoreEnabled = false
            resultsAdapter.submitList(emptyList())
            binding.tvResultsFor.visibility = View.GONE
            binding.rvSearchResults.visibility = View.GONE
            binding.searchEmptyState.visibility = View.VISIBLE
            binding.searchLoading.visibility = View.GONE
            stopSearchLoadingAnimation()
            return
        }

        binding.tvResultsFor.text = "Results for \"$query\""
        binding.tvResultsFor.visibility = View.VISIBLE
        binding.searchEmptyState.visibility = View.GONE
        binding.searchLoading.visibility = View.VISIBLE
        startSearchLoadingAnimation()
        binding.rvSearchResults.visibility = View.GONE
        activeQuery = query
        nextPage = 1
        canLoadMore = false
        isLoadingMore = false
        loadMoreJob?.cancel()
        resultsAdapter.setOnLoadMoreListener(null)
        resultsAdapter.loadMoreEnabled = false
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(160)
            val results = try {
                withContext(Dispatchers.IO) {
                    UserPreferences.currentProvider?.search(query, page = 1).orEmpty()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (query == activeQuery) {
                    binding.searchLoading.visibility = View.GONE
                    stopSearchLoadingAnimation()
                    binding.searchEmptyState.visibility = View.VISIBLE
                }
                return@launch
            }
            if (query != activeQuery) return@launch
            results.forEach { item ->
                when (item) {
                    is Movie -> item.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                    is TvShow -> item.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
                }
            }
            nextPage = 2
            canLoadMore = results.isNotEmpty()
            resultsAdapter.setOnLoadMoreListener {
                if (canLoadMore && !isLoadingMore) {
                    resultsAdapter.loadMoreEnabled = false
                    loadMoreResults()
                }
            }
            resultsAdapter.loadMoreEnabled = false
            resultsAdapter.submitList(results)
            rememberSearch(query)
            binding.searchLoading.visibility = View.GONE
            stopSearchLoadingAnimation()
            binding.rvSearchResults.visibility = View.VISIBLE
            binding.rvSearchResults.alpha = 0f
            binding.rvSearchResults.translationY = 12f
            binding.rvSearchResults.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun loadMoreResults() {
        val query = activeQuery
        if (query.isBlank() || isLoadingMore) return

        isLoadingMore = true
        loadMoreJob?.cancel()
        loadMoreJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    UserPreferences.currentProvider?.search(query, page = nextPage).orEmpty()
                }
                if (query != activeQuery) return@launch

                results.forEach { item ->
                    when (item) {
                        is Movie -> item.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                        is TvShow -> item.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
                    }
                }
                if (results.isEmpty()) {
                    canLoadMore = false
                    resultsAdapter.setOnLoadMoreListener(null)
                } else {
                    resultsAdapter.submitList(resultsAdapter.items + results)
                    nextPage++
                    resultsAdapter.loadMoreEnabled = false
                }
            } catch (_: Exception) {
                if (query == activeQuery) canLoadMore = false
            } finally {
                isLoadingMore = false
                resultsAdapter.isLoading = false
            }
        }
    }

    private fun searchHistory(): List<String> = requireContext()
        .getSharedPreferences("search", 0)
        .getString(SEARCH_HISTORY_KEY, "")
        .orEmpty()
        .split("\u001F")
        .filter(String::isNotBlank)

    private fun rememberSearch(value: String) {
        val updated = buildList {
            add(value)
            addAll(searchHistory().filterNot { it.equals(value, ignoreCase = true) })
        }.take(MAX_HISTORY_ITEMS)
        requireContext().getSharedPreferences("search", 0).edit()
            .putString(SEARCH_HISTORY_KEY, updated.joinToString("\u001F"))
            .apply()
        renderSearchHistory()
    }

    private fun renderSearchHistory() {
        if (_binding == null) return
        val history = searchHistory()
        val hasHistory = history.isNotEmpty()
        binding.searchHistoryHeader.visibility = if (hasHistory) View.VISIBLE else View.GONE
        binding.searchHistoryRows.visibility = if (hasHistory) View.VISIBLE else View.GONE
        binding.searchEmptyIcon.visibility = if (hasHistory) View.GONE else View.VISIBLE
        binding.tvSearchEmptyPrompt.visibility = if (hasHistory) View.GONE else View.VISIBLE
        binding.searchHistoryRows.removeAllViews()
        history.forEach { value ->
            val row = TextView(requireContext()).apply {
                text = value
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.NORMAL)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16.dp(requireContext()), 0, 16.dp(requireContext()), 0)
                background = resources.getDrawable(R.drawable.bg_search_glass, requireContext().theme)
                minHeight = 48.dp(requireContext())
                isClickable = true
                setOnClickListener {
                    binding.etSearchQuery.setText(value)
                    binding.etSearchQuery.setSelection(binding.etSearchQuery.length())
                }
            }
            binding.searchHistoryRows.addView(row, LinearLayout.LayoutParams(-1, 48.dp(requireContext())).apply {
                bottomMargin = 8.dp(requireContext())
            })
        }
    }

    private fun startVoicePulse() {
        voicePulseAnimator?.cancel()
        voicePulseAnimator = ObjectAnimator.ofFloat(
            binding.btnSearchVoice,
            View.SCALE_X,
            1f,
            1.14f,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            duration = 360L
            start()
        }
        binding.btnSearchVoice.animate().scaleY(1.14f).setDuration(180L).start()
    }

    private fun stopVoicePulse() {
        voicePulseAnimator?.cancel()
        voicePulseAnimator = null
        binding.btnSearchVoice.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        if (::voiceHelper.isInitialized) voiceHelper.stopRecognition()
        voicePulseAnimator?.cancel()
        searchLoadingAnimator?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun startSearchLoadingAnimation() {
        searchLoadingAnimator?.cancel()
        searchLoadingAnimator = ObjectAnimator.ofFloat(
            binding.searchLoading,
            View.ALPHA,
            0.45f,
            1f,
        ).apply {
            duration = 520L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopSearchLoadingAnimation() {
        searchLoadingAnimator?.cancel()
        searchLoadingAnimator = null
        binding.searchLoading.alpha = 1f
    }
}
