package com.nextservices.nextvision.fragments.favorites

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.databinding.FragmentFavoritesMobileBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.models.Category
import com.nextservices.nextvision.ui.SpacingItemDecoration
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.viewModelsFactory
import com.nextservices.nextvision.utils.UniverseRepository
import com.nextservices.nextvision.utils.UniverseCollection
import com.nextservices.nextvision.utils.TMDb3
import kotlinx.coroutines.launch

class FavoritesMobileFragment : Fragment() {

    private var _binding: FragmentFavoritesMobileBinding? = null
    private val binding get() = _binding!!
    private val appAdapter = AppAdapter()
    private var rearrangeMode = false
    private val selectedItems = mutableSetOf<String>()
    private var universeCollections: List<UniverseCollection> = emptyList()
    private var favoriteSections: List<FavoritesViewModel.FavoriteSection> = emptyList()
    private val providerName get() = UserPreferences.currentProvider?.name.orEmpty()
    private val viewModel by viewModelsFactory {
        FavoritesViewModel(AppDatabase.getInstance(requireContext()), providerName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFavoritesMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvFavorites.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(SpacingItemDecoration(4.dp(requireContext())))
        }
        createDragHelper().attachToRecyclerView(binding.rvFavorites)
        setRearrangeMode(false)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sections.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect {
                favoriteSections = it
                display(it)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            universeCollections = runCatching { UniverseRepository.load(requireContext()) }.getOrDefault(emptyList())
            if (!isAdded || _binding == null) return@launch
            display(favoriteSections)
        }
    }

    private fun createDragHelper() = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0,
    ) {
        private var draggedSection: FavoritesViewModel.Section? = null

        override fun isLongPressDragEnabled(): Boolean = rearrangeMode

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val position = viewHolder.bindingAdapterPosition
            return if (!rearrangeMode || appAdapter.items.getOrNull(position) is FavoriteSectionHeader) {
                makeMovementFlags(0, 0)
            } else {
                super.getMovementFlags(recyclerView, viewHolder)
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            val fromSection = sectionAt(from) ?: return false
            if (sectionAt(to) != fromSection || appAdapter.items.getOrNull(to) is FavoriteSectionHeader) {
                return false
            }
            draggedSection = fromSection
            return reorderForDrag(fromSection, from, to)
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            draggedSection?.let(::persistSectionOrder)
            draggedSection = null
        }
    })

    private fun setRearrangeMode(enabled: Boolean) {
        rearrangeMode = enabled
        if (!enabled) selectedItems.clear()
        configureAdapterInteractions()
        appAdapter.notifyDataSetChanged()
    }

    private fun configureAdapterInteractions() {
        appAdapter.isItemSelectedListener = { itemKey(it) in selectedItems }
        if (rearrangeMode) {
            appAdapter.onMovieClickListener = { toggleSelection(FavoritesViewModel.Section.MOVIES, it.id) }
            appAdapter.onTvShowClickListener = { toggleSelection(FavoritesViewModel.Section.TV_SHOWS, it.id) }
            appAdapter.onMovieLongClickListener = { }
            appAdapter.onTvShowLongClickListener = { }
        } else {
            appAdapter.onMovieClickListener = { movie ->
                if (movie.id.startsWith(COLLECTION_ID_PREFIX)) {
                    findNavController().navigate(
                        R.id.collection,
                        Bundle().apply { putInt("id", movie.id.removePrefix(COLLECTION_ID_PREFIX).toInt()) },
                    )
                } else {
                    findNavController().navigate(FavoritesMobileFragmentDirections.actionFavoritesToMovie(id = movie.id))
                }
            }
            appAdapter.onTvShowClickListener = null
            appAdapter.onMovieLongClickListener = null
            appAdapter.onTvShowLongClickListener = null
        }
    }

    private fun toggleSelection(section: FavoritesViewModel.Section, id: String) {
        val key = selectionKey(section, id)
        if (!selectedItems.add(key)) selectedItems.remove(key)
        appAdapter.items.indexOfFirst { itemKey(it) == key }
            .takeIf { it >= 0 }
            ?.let(appAdapter::notifyItemSelectionChanged)
    }

    private fun reorderForDrag(
        section: FavoritesViewModel.Section,
        fromPosition: Int,
        toPosition: Int,
    ): Boolean {
        val source = appAdapter.items.getOrNull(fromPosition) ?: return false
        val sourceKey = itemKey(source) ?: return false
        val movingKeys = selectedItems
            .filterTo(mutableSetOf()) { it.startsWith("${section.key}:") }
            .takeIf { sourceKey in it && it.isNotEmpty() }
            ?: mutableSetOf(sourceKey)
        val sectionItems = itemsInSection(section)
        val moving = sectionItems.filter { itemKey(it) in movingKeys }
        val target = appAdapter.items.getOrNull(toPosition) ?: return false
        if (moving.isEmpty()) return false
        if (target in moving) return true
        val remaining = sectionItems.filterNot { it in moving }.toMutableList()
        val targetIndex = remaining.indexOf(target).takeIf { it >= 0 } ?: return false
        val insertAt = (targetIndex + if (toPosition > fromPosition) 1 else 0).coerceIn(0, remaining.size)
        remaining.addAll(insertAt, moving)
        replaceSectionItems(section, remaining)
        return true
    }

    private fun sectionAt(position: Int): FavoritesViewModel.Section? {
        if (position !in appAdapter.items.indices) return null
        return (position downTo 0)
            .asSequence()
            .mapNotNull { appAdapter.items[it] as? FavoriteSectionHeader }
            .firstOrNull()
            ?.section
    }

    private fun persistSectionOrder(section: FavoritesViewModel.Section) {
        val ids = itemsInSection(section)
            .mapNotNull {
                when (it) {
                    is Movie -> it.id
                    is TvShow -> it.id
                    else -> null
                }
            }
        viewModel.setManualItemOrder(section, ids)
    }

    private fun itemsInSection(section: FavoritesViewModel.Section): List<AppAdapter.Item> = appAdapter.items
        .dropWhile { it !is FavoriteSectionHeader || it.section != section }
        .drop(1)
        .takeWhile { it !is FavoriteSectionHeader }

    private fun replaceSectionItems(section: FavoritesViewModel.Section, newSectionItems: List<AppAdapter.Item>) {
        val headerIndex = appAdapter.items.indexOfFirst {
            it is FavoriteSectionHeader && it.section == section
        }
        if (headerIndex < 0) return
        val nextHeaderOffset = appAdapter.items
            .drop(headerIndex + 1)
            .indexOfFirst { it is FavoriteSectionHeader }
        val endIndex = if (nextHeaderOffset >= 0) {
            headerIndex + 1 + nextHeaderOffset
        } else {
            appAdapter.items.size
        }
        val reordered = appAdapter.items.toMutableList().apply {
            subList(headerIndex + 1, endIndex).clear()
            addAll(headerIndex + 1, newSectionItems)
        }
        appAdapter.replaceItemOrder(reordered)
    }

    private fun itemKey(item: AppAdapter.Item): String? = when (item) {
        is Movie -> selectionKey(FavoritesViewModel.Section.MOVIES, item.id)
        is TvShow -> selectionKey(FavoritesViewModel.Section.TV_SHOWS, item.id)
        else -> null
    }

    private fun selectionKey(section: FavoritesViewModel.Section, id: String) = "${section.key}:$id"

    private fun display(sections: List<FavoritesViewModel.FavoriteSection>) {
        if (!isAdded || _binding == null) return
        val categoryItems = sections.mapNotNull { favoriteSection ->
            if (favoriteSection.items.isEmpty()) return@mapNotNull null
            val title = when (favoriteSection.section) {
                FavoritesViewModel.Section.MOVIES -> getString(R.string.home_favorite_movies)
                FavoritesViewModel.Section.TV_SHOWS -> getString(R.string.home_favorite_tv_shows)
            }
            Category(title, favoriteSection.items.onEach { item ->
                item.itemType = when (item) {
                    is Movie -> AppAdapter.Type.MOVIE_MOBILE_ITEM
                    is TvShow -> AppAdapter.Type.TV_SHOW_MOBILE_ITEM
                    else -> item.itemType
                }
            }).apply {
                itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
                itemSpacing = 10.dp(requireContext())
            }
        }
        val collections = universeCollections.map { collection ->
            Movie(
                id = "$COLLECTION_ID_PREFIX${collection.id}",
                title = collection.title,
                poster = collection.posterUrl,
                banner = collection.backdropUrl,
            ).apply { itemType = AppAdapter.Type.MOVIE_MOBILE_ITEM }
        }
        val allItems = categoryItems + listOfNotNull(
            Category(getString(R.string.explore_collections), collections).apply {
                itemType = AppAdapter.Type.CATEGORY_MOBILE_ITEM
                itemSpacing = 10.dp(requireContext())
            }.takeIf { collections.isNotEmpty() },
        )
        binding.tvFavoritesEmpty.isVisible = allItems.isEmpty()
        binding.rvFavorites.isVisible = allItems.isNotEmpty()
        appAdapter.submitList(allItems)
    }

    private fun showSortDialog() {
        val modes = FavoritesViewModel.SortMode.entries
        val labels = arrayOf(
            getString(R.string.favorites_sort_manual),
            getString(R.string.favorites_sort_recent),
            getString(R.string.favorites_sort_title_ascending),
            getString(R.string.favorites_sort_title_descending),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.favorites_sort_title)
            .setSingleChoiceItems(labels, modes.indexOf(viewModel.currentSortMode())) { dialog, which ->
                if (modes[which] != FavoritesViewModel.SortMode.MANUAL) setRearrangeMode(false)
                viewModel.setSortMode(modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.option_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        setRearrangeMode(false)
        appAdapter.onSaveInstanceState(binding.rvFavorites)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val COLLECTION_ID_PREFIX = "collection:"
    }
}
