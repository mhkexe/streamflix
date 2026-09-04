package com.nextservices.nextvision.fragments.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentHomeMobileBinding
import com.nextservices.nextvision.models.Category
import com.nextservices.nextvision.models.Episode
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.ui.SpacingItemDecoration
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.LoggingUtils
import com.nextservices.nextvision.utils.StartupState
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.nextservices.nextvision.utils.VoiceRecognitionHelper
import androidx.core.os.bundleOf

class HomeMobileFragment : Fragment() {
    private var skeletonAnimator: ObjectAnimator? = null

    private var hasAutoCleared409: Boolean = false

    private var _binding: FragmentHomeMobileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by lazy {
        val providerKey = UserPreferences.currentProvider?.name ?: "default"
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(AppDatabase.getInstance(requireContext())) as T
            }
        }
        ViewModelProvider(this, factory).get(providerKey, HomeViewModel::class.java)
    }

    private val appAdapter = AppAdapter()
    private lateinit var voiceHelper: VoiceRecognitionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceHelper = VoiceRecognitionHelper(
            this,
            onResult = { query ->
                if (isAdded) findNavController().navigate(
                    R.id.action_global_search_mobile,
                    bundleOf("query" to query),
                )
            },
            onError = { message ->
                if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            },
            onListeningStateChanged = {},
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeHome()
        binding.homeSearchBar.setOnClickListener {
            binding.homeSearchBar.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0.7f)
                .setDuration(120L)
                .withEndAction {
                    findNavController().navigate(R.id.action_global_search_mobile)
                    binding.homeSearchBar.scaleX = 1f
                    binding.homeSearchBar.scaleY = 1f
                    binding.homeSearchBar.alpha = 1f
                }
                .start()
        }
        binding.btnHomeMic.setOnClickListener {
            findNavController().navigate(
                R.id.action_global_search_mobile,
                bundleOf("start_voice" to true),
            )
        }
        binding.btnHomeUser.setOnClickListener {
            findNavController().navigate(R.id.action_global_guest_user)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    HomeViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.GONE
                        loadingSkeleton.visibility = View.VISIBLE
                        skeletonAnimator?.cancel()
                        skeletonAnimator = ObjectAnimator.ofFloat(loadingSkeleton, View.ALPHA, 1f, 0.52f).apply {
                            duration = 650L
                            repeatMode = ValueAnimator.REVERSE
                            repeatCount = ValueAnimator.INFINITE
                            start()
                        }
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is HomeViewModel.State.SuccessLoading -> {
                        skeletonAnimator?.cancel()
                        displayHome(state.categories)
                        binding.isLoading.root.visibility = View.GONE
                        StartupState.markHomeContentReady()
                    }
                    is HomeViewModel.State.FailedLoading -> {
                        StartupState.markHomeContentReady()
                        val code = (state.error as? retrofit2.HttpException)?.code()
                        if (code == 409 && !hasAutoCleared409) {
                            hasAutoCleared409 = true
                            CacheUtils.clearAppCache(requireContext())
                            android.widget.Toast.makeText(requireContext(), getString(com.nextservices.nextvision.R.string.clear_cache_done_409), android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.getHome()
                            return@collect
                        }
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.isLoading.apply {
                            skeletonAnimator?.cancel()
                            loadingSkeleton.visibility = View.GONE
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                            val doRetry = { viewModel.getHome() }
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
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        appAdapter.onSaveInstanceState(binding.rvHome)
        _binding = null
    }

    override fun onDestroy() {
        if (::voiceHelper.isInitialized) voiceHelper.stopRecognition()
        super.onDestroy()
    }


    private fun initializeHome() {
        binding.rvHome.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
        }

        // Ensure background image is hidden on mobile to show theme color
        binding.ivHomeBackground.visibility = View.GONE
    }

    private fun displayHome(categories: List<Category>) {
        val orderedCategories = categories.filterNot { it.name.contains("anime", ignoreCase = true) } +
            categories.filter { it.name.contains("anime", ignoreCase = true) }
        val mobileGenreSectionNames = setOf(
            "Action & Adventure",
            "Sci-Fi & Fantasy",
            "Mystery & Thriller",
            "Comedy & Romance",
            "Drama & Romance",
        )
        val featuredItems = categories
            .filter {
                it.name.contains("popular", ignoreCase = true) ||
                    it.name.contains("trending", ignoreCase = true)
            }
            .filterNot { it.name.contains("anime", ignoreCase = true) }
            .flatMap { it.list }
            .filter { it is Movie || it is TvShow }
            .distinctBy {
                when (it) {
                    is Movie -> "movie:${it.id}"
                    is TvShow -> "tv:${it.id}"
                    else -> it.hashCode().toString()
                }
            }
            .take(6)
        val featuredMovies = Category(Category.FEATURED, featuredItems).also { featured ->
            featured.itemType = AppAdapter.Type.CATEGORY_MOBILE_SWIPER
            featured.list.forEach { show ->
                when (show) {
                    is Movie -> show.itemType = AppAdapter.Type.MOVIE_SWIPER_MOBILE_ITEM
                    is TvShow -> show.itemType = AppAdapter.Type.TV_SHOW_SWIPER_MOBILE_ITEM
                }
            }
        }

        categories
            .find { it.name == Category.CONTINUE_WATCHING }
            ?.also {
                it.name = getString(R.string.home_continue_watching)
                it.list.forEach { show ->
                    when (show) {
                        is Episode -> show.itemType = AppAdapter.Type.EPISODE_CONTINUE_WATCHING_MOBILE_ITEM
                        is Movie -> show.itemType = AppAdapter.Type.MOVIE_CONTINUE_WATCHING_MOBILE_ITEM
                    }
                }
            }

        categories
            .find { it.name == Category.RECENTLY_WATCHED }
            ?.also {
                it.name = getString(R.string.home_recently_watched)
            }

        categories.forEach { category ->
            category.itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
        }

        appAdapter.submitList(
            listOf(featuredMovies) + orderedCategories
                .filter { it.name != Category.FEATURED }
                .filter {
                    (it.list.isNotEmpty() || it.name in mobileGenreSectionNames) &&
                        it.name != Category.FAVORITE_MOVIES &&
                        it.name != Category.FAVORITE_TV_SHOWS &&
                        (!it.name.contains("popular", ignoreCase = true) ||
                            it.name.contains("anime", ignoreCase = true)) &&
                        !it.name.contains("trending", ignoreCase = true)
                }
                .onEach { category ->
                    if (category.name != Category.FEATURED && category.name != getString(R.string.home_continue_watching)) {
                        category.list.onEach { show ->
                            when (show) {
                                is Episode -> show.itemType = AppAdapter.Type.EPISODE_MOBILE_ITEM
                                is Movie -> show.itemType = if (
                                    category.name.contains("trending", ignoreCase = true) ||
                                    category.name.contains("collection", ignoreCase = true) ||
                                    category.name.contains("anime", ignoreCase = true) ||
                                    category.name in mobileGenreSectionNames
                                ) AppAdapter.Type.MOVIE_POSTER_MOBILE_ITEM else AppAdapter.Type.MOVIE_MOBILE_ITEM
                                is TvShow -> show.itemType = if (
                                    category.name.contains("trending", ignoreCase = true) ||
                                    category.name.contains("collection", ignoreCase = true) ||
                                    category.name.contains("anime", ignoreCase = true) ||
                                    category.name in mobileGenreSectionNames
                                ) AppAdapter.Type.TV_SHOW_POSTER_MOBILE_ITEM else AppAdapter.Type.TV_SHOW_MOBILE_ITEM
                            }
                        }
                    }
                    category.itemSpacing = 10.dp(requireContext())
                    category.itemType = when (category.name) {
                        Category.FEATURED -> AppAdapter.Type.CATEGORY_MOBILE_SWIPER
                        else -> AppAdapter.Type.CATEGORY_MOBILE_ITEM
                    }
                }
        )
    }
}
