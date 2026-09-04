package com.nextservices.nextvision.fragments.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.databinding.FragmentCollectionMobileBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.utils.TMDb3.original
import com.nextservices.nextvision.utils.TMDb3.w500
import com.nextservices.nextvision.utils.UniverseRepository
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.navigateMobileDetail
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CollectionMobileFragment : Fragment() {

    private var _binding: FragmentCollectionMobileBinding? = null
    private val binding get() = _binding!!
    private val adapter = AppAdapter()
    private val collectionId get() = requireArguments().getInt("id")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCollectionMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvCollection.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvCollection.adapter = adapter
        binding.rvCollection.addItemDecoration(
            com.nextservices.nextvision.ui.SpacingItemDecoration(10.dp(requireContext()))
        )
        adapter.onMovieClickListener = { movie ->
            findNavController().navigateMobileDetail(R.id.movie, Bundle().apply { putString("id", movie.id) })
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
        binding.tvCollectionDescription.visibility =
            if (detail.overview.orEmpty().isBlank()) View.GONE else View.VISIBLE
        load(binding.ivCollectionPoster, detail.posterPath?.w500)
        load(binding.tvCollectionBackdrop, detail.backdropPath?.original)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
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
                ).apply { itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM }
            }
        binding.rvCollection.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        adapter.submitList(movies)
    }

    private fun load(view: android.widget.ImageView, url: String?) {
        Glide.with(this).load(url).centerCrop().into(view)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
