package com.nextservices.nextvision.fragments.movie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import android.graphics.drawable.Drawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentMovieMobileBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.Show
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.LoggingUtils
import com.nextservices.nextvision.utils.format
import com.nextservices.nextvision.utils.loadMovieBanner
import com.nextservices.nextvision.utils.viewModelsFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MovieMobileFragment : Fragment() {

    private var _binding: FragmentMovieMobileBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<MovieMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnMovieBack.setOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MovieViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        loadingSkeleton.visibility = View.VISIBLE
                        loadingSkeleton.startAnimation(AnimationUtils.loadAnimation(requireContext(), com.nextservices.nextvision.R.anim.skeleton_pulse))
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is MovieViewModel.State.SuccessLoading -> {
                        displayMovie(state.movie)
                        binding.isLoading.loadingSkeleton.clearAnimation()
                        binding.isLoading.loadingSkeleton.visibility = View.GONE
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is MovieViewModel.State.FailedLoading -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                            binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                                val doRetry = { viewModel.getMovie(args.id) }
                                btnIsLoadingRetry.setOnClickListener { doRetry() }
                                btnIsLoadingClearCache.setOnClickListener {
                                    CacheUtils.clearAppCache(requireContext())
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

    private fun displayMovie(movie: Movie) {
        binding.ivMovieBanner.loadMovieBanner(movie) {
            transition(DrawableTransitionOptions.withCrossFade())
        }
        binding.tvMovieTitle.apply {
            text = movie.title
            visibility = if (movie.logo.isNullOrBlank()) View.VISIBLE else View.GONE
        }
        binding.ivMovieLogo.apply {
            if (movie.logo.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                Glide.with(this)
                    .load(movie.logo)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean,
                        ): Boolean {
                            visibility = View.GONE
                            binding.tvMovieTitle.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean,
                        ): Boolean {
                            binding.tvMovieTitle.visibility = View.GONE
                            return false
                        }
                    })
                    .into(this)
            }
        }
        binding.tvMovieAgeRating.visibility = View.GONE
        binding.tvMovieMetadata.text = listOfNotNull(
            movie.genres.firstOrNull()?.name,
            movie.released?.format("yyyy"),
            movie.runtime?.takeIf { it > 0 }?.let { runtime ->
                val hours = runtime / 60
                val minutes = runtime % 60
                when {
                    hours > 0 && minutes > 0 -> "${hours}h ${minutes} min"
                    hours > 0 -> "${hours}h"
                    else -> "$minutes min"
                }
            },
            movie.ageRating?.takeIf { it.isNotBlank() } ?: "NR",
            movie.rating?.let { String.format(java.util.Locale.ROOT, "%.1f", it) },
        ).joinToString("  •  ")
        binding.tvMovieOverview.text = movie.overview
        val castSlots = listOf(
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_cast_preview_1),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_cast_preview_2),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_cast_preview_3),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_cast_preview_4),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_cast_preview_5),
        )
        castSlots.forEach { it.visibility = View.GONE }
        movie.cast.take(5).forEachIndexed { index, person ->
            castSlots[index].apply {
                visibility = View.VISIBLE
                findViewById<android.widget.TextView>(com.nextservices.nextvision.R.id.tv_cast_preview_name).text = person.name
                findViewById<android.widget.TextView>(com.nextservices.nextvision.R.id.tv_cast_preview_role).text = person.role
                Glide.with(this@MovieMobileFragment)
                    .load(person.image)
                    .placeholder(com.nextservices.nextvision.R.drawable.ic_person_placeholder)
                    .centerCrop()
                    .into(findViewById(com.nextservices.nextvision.R.id.iv_cast_preview_image))
                setOnClickListener {
                    findNavController().navigate(MovieMobileFragmentDirections.actionMovieToPeople(
                        id = person.id,
                        name = person.name,
                        image = person.image,
                    ))
                }
            }
        }
        val recommendationSlots = listOf(
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_recommendation_1),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_recommendation_2),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_recommendation_3),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_recommendation_4),
            binding.root.findViewById<View>(com.nextservices.nextvision.R.id.movie_recommendation_5),
        )
        recommendationSlots.forEach { it.visibility = View.GONE }
        movie.recommendations.take(5).forEachIndexed { index, recommendation ->
            recommendationSlots[index].apply {
                visibility = View.VISIBLE
                val poster = when (recommendation) {
                    is Movie -> recommendation.poster
                    is TvShow -> recommendation.poster
                }
                Glide.with(this@MovieMobileFragment)
                    .load(poster)
                    .placeholder(com.nextservices.nextvision.R.drawable.detail_preview_recommendation)
                    .centerCrop()
                    .into(findViewById(com.nextservices.nextvision.R.id.iv_detail_recommendation_image))
                setOnClickListener {
                    when (recommendation) {
                        is Movie -> findNavController().navigate(
                            MovieMobileFragmentDirections.actionMovieToMovie(recommendation.id)
                        )
                        is TvShow -> findNavController().navigate(
                            MovieMobileFragmentDirections.actionMovieToTvShow(
                                recommendation.id,
                                recommendation.poster,
                                recommendation.banner,
                            )
                        )
                    }
                }
            }
        }
        binding.btnMovieWatchNow.apply {
            text = if (movie.watchHistory != null) getString(com.nextservices.nextvision.R.string.movie_resume)
            else getString(com.nextservices.nextvision.R.string.movie_watch_now)
            setOnClickListener {
                findNavController().navigate(MovieMobileFragmentDirections.actionMovieToPlayer(
                    id = movie.id,
                    title = movie.title,
                    subtitle = movie.released?.format("yyyy") ?: "",
                    videoType = com.nextservices.nextvision.models.Video.Type.Movie(
                        id = movie.id,
                        title = movie.title,
                        releaseDate = movie.released?.format("yyyy-MM-dd") ?: "",
                        poster = movie.poster ?: movie.banner ?: "",
                        imdbId = movie.imdbId,
                    ),
                ))
            }
        }
        binding.btnMovieFavorite.apply {
            isSelected = movie.isFavorite
            setOnClickListener {
                val favorite = !movie.isFavorite
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    database.movieDao().upsertFavorite(movie, favorite)
                    movie.isFavorite = favorite
                    launch(Dispatchers.Main) { binding.btnMovieFavorite.isSelected = favorite }
                }
            }
        }
    }
}
