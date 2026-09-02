package com.nextservices.nextvision.fragments.collection

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.databinding.FragmentCollectionBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.utils.TMDb3.original
import com.nextservices.nextvision.utils.TMDb3.w500
import com.nextservices.nextvision.utils.UniverseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CollectionFragment : Fragment() {
    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!
    private val adapter = AppAdapter()
    private val collectionId get() = requireArguments().getInt("id")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.vgvCollection.apply {
            adapter = this@CollectionFragment.adapter
            clipToPadding = false
            isFocusable = true
            setItemSpacing(resources.getDimension(R.dimen.movies_spacing).toInt())
        }
        adapter.onMovieClickListener = { movie ->
            findNavController().navigate(
                R.id.movie,
                Bundle().apply { putString("id", movie.id) },
            )
        }
        adapter.onMovieKeyListener = fun(movie, event): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN ||
                event.action != KeyEvent.ACTION_DOWN ||
                event.repeatCount > 0
            ) {
                return false
            }

            val position = adapter.items.indexOfFirst { it === movie }
            if (position < 0) return false

            val targetPosition = (position + GRID_COLUMN_COUNT)
                .coerceAtMost(adapter.itemCount - 1)
            if (targetPosition == position) return false

            binding.vgvCollection.post {
                binding.vgvCollection.scrollToPosition(targetPosition)
                binding.vgvCollection.post {
                    binding.vgvCollection.findViewHolderForAdapterPosition(targetPosition)
                        ?.itemView
                        ?.requestFocus()
                }
            }
            return true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { UniverseRepository.detail(requireContext(), collectionId) }
                .onSuccess(::display)
                .onFailure { binding.tvCollectionDescription.text = getString(R.string.collection_load_failed) }
        }
    }

    private fun display(detail: UniverseRepository.CachedCollectionDetail) {
        binding.tvCollectionTitle.text = detail.name
        binding.tvCollectionDescription.text = detail.overview.orEmpty()
        binding.tvCollectionDescription.isVisible = detail.overview.orEmpty().isNotBlank()
        binding.ivCollectionPoster.load(detail.posterPath?.w500)
        binding.tvCollectionBackdrop.load(detail.backdropPath?.original)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val isTv = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_TYPE_MASK ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val movies = detail.parts
            .filter { !it.releaseDate.isNullOrBlank() && it.releaseDate!! <= today && it.posterPath != null }
            .sortedByDescending { it.releaseDate }
            .map { part ->
                Movie(
                    id = part.id.toString(),
                    title = part.title,
                    overview = part.overview,
                    released = part.releaseDate,
                    rating = part.voteAverage,
                    poster = part.posterPath?.w500,
                    banner = part.backdropPath?.original,
                ).apply {
                    itemType = if (isTv) AppAdapter.Type.MOVIE_GRID_TV_ITEM else AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                }
            }
        binding.vgvCollection.isVisible = movies.isNotEmpty()
        adapter.submitList(movies)
        binding.vgvCollection.post {
            binding.vgvCollection.getChildAt(0)?.apply {
                isFocusable = true
                requestFocus()
            }
        }
    }

    private fun android.widget.ImageView.load(url: String?) {
        com.bumptech.glide.Glide.with(this)
            .load(url)
            .centerCrop()
            .into(this)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val GRID_COLUMN_COUNT = 6
    }
}
