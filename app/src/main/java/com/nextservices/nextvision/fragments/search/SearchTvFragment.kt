package com.nextservices.nextvision.fragments.search

import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.nextservices.nextvision.R
import com.nextservices.nextvision.adapters.AppAdapter
import com.nextservices.nextvision.databinding.FragmentSearchTvBinding
import com.nextservices.nextvision.models.Movie
import com.nextservices.nextvision.models.TvShow
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.VoiceRecognitionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SearchTvFragment : Fragment() {
	private var _binding: FragmentSearchTvBinding? = null
	private val binding get() = _binding!!
	private val resultsAdapter = AppAdapter()
	private lateinit var voiceHelper: VoiceRecognitionHelper
	private var searchJob: Job? = null
	private val query = StringBuilder()
	private val searchHistory = mutableListOf<SearchHistoryItem>()
	private val historyPreferences by lazy {
		requireContext().getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE)
	}

	private data class SearchHistoryItem(val id: String, val title: String, val isTvShow: Boolean)

	private val keyboardRows = listOf(
		listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"),
		listOf("j", "k", "l", "m", "n", "o", "p", "q", "r"),
		listOf("s", "t", "u", "v", "w", "x", "y", "z", "1"),
		listOf("2", "3", "4", "5", "6", "7", "8", "9", "0"),
	)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
		_binding = FragmentSearchTvBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, state: Bundle?) {
		super.onViewCreated(view, state)
		binding.keyboardPanel.post {
			binding.keyboardPanel.translationX = -binding.keyboardPanel.width.toFloat()
			binding.keyboardPanel.animate()
				.translationX(0f)
				.setDuration(420L)
				.setInterpolator(android.view.animation.DecelerateInterpolator())
				.start()
		}
		binding.etSearchQuery.showSoftInputOnFocus = false
		binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
			override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
				query.clear()
				query.append(text?.toString() ?: "")
				scheduleSearch()
			}
			override fun afterTextChanged(editable: Editable?) = Unit
		})
		setupResults()
		loadSearchHistory()
		setupKeyboard()
		binding.searchKeyboard.getChildAt(0)?.requestFocus()
		voiceHelper = VoiceRecognitionHelper(
			this,
			onResult = { result -> setQuery(result); runSearch() },
			onError = { message -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show() },
			onListeningStateChanged = { listening ->
				binding.ivSearchMic.isActivated = listening
				binding.ivSearchMic.alpha = 1f
			},
		)
		binding.btnSearchVoice.setOnFocusChangeListener { _, hasFocus ->
			binding.ivSearchMic.isSelected = hasFocus
		}
		binding.btnSearchVoice.setOnClickListener { voiceHelper.startWithPermissionCheck() }
	}

	private fun setupResults() {
		binding.vgvSearchResults.adapter = resultsAdapter
		binding.vgvSearchResults.setItemSpacing(resources.getDimensionPixelSize(R.dimen.movies_spacing))
		resultsAdapter.onMovieClickListener = { movie ->
			addToSearchHistory(SearchHistoryItem(movie.id, movie.title, false))
			findNavController().navigate(R.id.action_global_movie, bundleOf("id" to movie.id))
		}
		resultsAdapter.onTvShowClickListener = { show ->
			addToSearchHistory(SearchHistoryItem(show.id, show.title, true))
			findNavController().navigate(R.id.action_global_tv_show, bundleOf("id" to show.id))
		}
		showEmptyState()
	}

	private fun setupKeyboard() {
		keyboardRows.forEach { row ->
			val rowView = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
			binding.searchKeyboard.addView(rowView, rowParams())
			row.forEach { key -> addKey(rowView, key) { appendQuery(key) } }
		}
		val actionRow = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
		binding.searchKeyboard.addView(actionRow, rowParams())
		addKey(actionRow, "Space", 0.6f) { appendQuery(" ") }
		addIconKey(actionRow, R.drawable.ic_backspace_tv, 0.4f) { deleteQueryCharacter() }
	}

	private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 6 }

	private fun addKey(row: LinearLayout, label: String, weight: Float = 1f, action: () -> Unit) {
		row.addView(Button(requireContext()).apply {
			text = label
			textSize = if (label.length > 1) 12f else 18f
			setTextColor(Color.WHITE)
			isAllCaps = false
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = android.view.Gravity.CENTER
			setPadding(0, 0, 0, 0)
			background = resources.getDrawable(R.drawable.bg_search_key_tv, requireContext().theme)
			setOnFocusChangeListener { button, hasFocus ->
				(button as Button).setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
			}
			setOnClickListener { action() }
		}, LinearLayout.LayoutParams(0, 56, weight).apply { setMargins(3, 0, 3, 0) })
	}

	private fun addIconKey(row: LinearLayout, icon: Int, weight: Float, action: () -> Unit) {
		row.addView(Button(requireContext()).apply {
			contentDescription = "Delete"
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = android.view.Gravity.CENTER
			setPadding(14, 14, 14, 14)
			background = resources.getDrawable(R.drawable.bg_search_key_tv, requireContext().theme)
			setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
			setOnFocusChangeListener { button, hasFocus ->
				(button as Button).compoundDrawableTintList = ColorStateList.valueOf(
					if (hasFocus) Color.BLACK else Color.WHITE
				)
			}
			setOnClickListener { action() }
		}, LinearLayout.LayoutParams(0, 56, weight).apply { setMargins(3, 0, 3, 0) })
	}

	private fun setQuery(value: String) {
		binding.etSearchQuery.setText(value)
		binding.etSearchQuery.setSelection(binding.etSearchQuery.length())
	}

	private fun appendQuery(value: String) {
		binding.etSearchQuery.append(value)
	}

	private fun deleteQueryCharacter() {
		val text = binding.etSearchQuery.text
		if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
	}

	private fun scheduleSearch() {
		searchJob?.cancel()
		if (query.isBlank()) {
			showEmptyState()
			return
		}
		binding.searchEmptyState.visibility = View.GONE
		binding.vgvSearchResults.visibility = View.VISIBLE
		binding.tvResultsFor.text = "Results for: ${query.trim()}"
		searchJob = viewLifecycleOwner.lifecycleScope.launch {
			delay(300)
			runSearch()
		}
	}

	private fun showEmptyState() {
		resultsAdapter.submitList(emptyList())
		binding.vgvSearchResults.visibility = View.GONE
		binding.searchEmptyState.visibility = View.VISIBLE
		binding.searchHistorySection.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
		binding.tvResultsFor.text = "Results for"
	}

	private fun loadSearchHistory() {
		searchHistory.clear()
		val stored = historyPreferences.getString(HISTORY_KEY, null) ?: return
		try {
			val array = JSONArray(stored)
			for (index in 0 until array.length()) {
				val item = array.getJSONObject(index)
				searchHistory += SearchHistoryItem(
					id = item.getString("id"),
					title = item.getString("title"),
					isTvShow = item.getBoolean("isTvShow"),
				)
			}
		} catch (_: Exception) {
			historyPreferences.edit().remove(HISTORY_KEY).apply()
		}
		renderSearchHistory()
	}

	private fun addToSearchHistory(item: SearchHistoryItem) {
		searchHistory.removeAll { it.id == item.id && it.isTvShow == item.isTvShow }
		searchHistory.add(0, item)
		while (searchHistory.size > MAX_HISTORY_ITEMS) searchHistory.removeLast()
		val array = JSONArray().apply {
			searchHistory.forEach { historyItem ->
				put(JSONObject().apply {
					put("id", historyItem.id)
					put("title", historyItem.title)
					put("isTvShow", historyItem.isTvShow)
				})
			}
		}
		historyPreferences.edit().putString(HISTORY_KEY, array.toString()).apply()
		renderSearchHistory()
	}

	private fun renderSearchHistory() {
		if (_binding == null) return
		binding.searchHistoryList.removeAllViews()
		searchHistory.forEach { item ->
			binding.searchHistoryList.addView(TextView(requireContext()).apply {
				text = item.title
				setTextColor(Color.WHITE)
				textSize = 16f
				isFocusable = true
				isFocusableInTouchMode = true
				setPadding(12, 8, 12, 8)
				setOnClickListener {
					if (item.isTvShow) {
						findNavController().navigate(R.id.action_global_tv_show, bundleOf("id" to item.id))
					} else {
						findNavController().navigate(R.id.action_global_movie, bundleOf("id" to item.id))
					}
				}
			})
		}
	}

	private fun runSearch() {
		val text = query.toString().trim()
		if (text.isEmpty()) return
		searchJob?.cancel()
		searchJob = viewLifecycleOwner.lifecycleScope.launch {
			binding.searchLoading.visibility = View.VISIBLE
			binding.vgvSearchResults.visibility = View.GONE
			val results = withContext(Dispatchers.IO) { UserPreferences.currentProvider?.search(text).orEmpty() }
			results.forEach { item ->
				when (item) {
					is Movie -> item.itemType = AppAdapter.Type.MOVIE_GRID_TV_ITEM
					is TvShow -> item.itemType = AppAdapter.Type.TV_SHOW_GRID_TV_ITEM
				}
			}
			resultsAdapter.submitList(results)
			binding.searchLoading.visibility = View.GONE
			binding.vgvSearchResults.visibility = View.VISIBLE
		}
	}

	override fun onDestroyView() {
		searchJob?.cancel()
		if (::voiceHelper.isInitialized) voiceHelper.stopRecognition()
		_binding = null
		super.onDestroyView()
	}

	private companion object {
		const val HISTORY_KEY = "clicked_titles"
		const val MAX_HISTORY_ITEMS = 5
	}
}
