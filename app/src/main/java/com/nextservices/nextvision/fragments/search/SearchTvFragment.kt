package com.nextservices.nextvision.fragments.search

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
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

class SearchTvFragment : Fragment() {
	private var _binding: FragmentSearchTvBinding? = null
	private val binding get() = _binding!!
	private val resultsAdapter = AppAdapter()
	private lateinit var voiceHelper: VoiceRecognitionHelper
	private var searchJob: Job? = null
	private val query = StringBuilder()
	private var voicePulseAnimator: AnimatorSet? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		VoiceRecognitionHelper(
			this,
			onResult = { result ->
				if (_binding != null) {
					setQuery(result)
					runSearch()
				}
			},
			onError = { message ->
				if (isAdded && _binding != null) {
					Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
				}
			},
			onListeningStateChanged = { listening ->
				_binding?.let {
					it.ivSearchMic.isActivated = listening
					if (listening) startVoicePulse() else stopVoicePulse()
				}
			},
		).also { voiceHelper = it }
	}

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
		binding.root.post {
			if (_binding == null) return@post
			setupKeyboard()
			binding.searchKeyboard.getChildAt(0)?.requestFocus()
			binding.btnSearchVoice.setOnFocusChangeListener { _, hasFocus ->
				binding.ivSearchMic.isSelected = hasFocus
			}
			binding.btnSearchVoice.setOnClickListener { voiceHelper.startWithPermissionCheck() }
		}
	}

	private fun setupResults() {
		binding.vgvSearchResults.adapter = resultsAdapter
		binding.vgvSearchResults.setItemSpacing(resources.getDimensionPixelSize(R.dimen.movies_spacing))
		resultsAdapter.onMovieClickListener = { movie ->
			findNavController().navigate(R.id.action_global_movie, bundleOf("id" to movie.id))
		}
		resultsAdapter.onTvShowClickListener = { show ->
			findNavController().navigate(
				R.id.action_global_tv_show,
				bundleOf(
					"id" to show.id,
					"poster" to show.poster,
					"banner" to show.banner,
				)
			)
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
		addKey(actionRow, "Space", 0.4f) { appendQuery(" ") }
		addKey(actionRow, "Back", 0.3f) { deleteQueryCharacter() }
		addKey(actionRow, "Clear", 0.3f) { clearQuery() }
	}

	private fun rowParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 }
	private fun keyHeight() = (36 * resources.displayMetrics.density).toInt()

	private fun addKey(row: LinearLayout, label: String, weight: Float = 1f, action: () -> Unit) {
		row.addView(Button(requireContext()).apply {
			text = label
			textSize = if (label.length > 1) 12f else 18f
			setTextColor(Color.WHITE)
			isAllCaps = false
			isFocusable = true
			isFocusableInTouchMode = true
			gravity = android.view.Gravity.CENTER
			textAlignment = View.TEXT_ALIGNMENT_CENTER
			includeFontPadding = false
			minWidth = 0
			minHeight = 0
			setPadding(0, 0, 0, 0)
			background = resources.getDrawable(R.drawable.bg_search_key_tv, requireContext().theme)
			setOnFocusChangeListener { button, hasFocus ->
				(button as Button).setTextColor(if (hasFocus) Color.BLACK else Color.WHITE)
			}
			setOnClickListener { action() }
		}, LinearLayout.LayoutParams(0, keyHeight(), weight).apply { setMargins(3, 0, 3, 0) })
	}

	private fun startVoicePulse() {
		voicePulseAnimator?.cancel()
		val scaleX = ObjectAnimator.ofFloat(binding.ivSearchMic, View.SCALE_X, 1f, 1.08f).apply {
			repeatMode = android.animation.ValueAnimator.REVERSE
			repeatCount = android.animation.ValueAnimator.INFINITE
		}
		val scaleY = ObjectAnimator.ofFloat(binding.ivSearchMic, View.SCALE_Y, 1f, 1.08f).apply {
			repeatMode = android.animation.ValueAnimator.REVERSE
			repeatCount = android.animation.ValueAnimator.INFINITE
		}
		voicePulseAnimator = AnimatorSet().apply {
			playTogether(
				scaleX,
				scaleY,
			)
			duration = 520L
				startDelay = 80L
				interpolator = android.view.animation.AccelerateDecelerateInterpolator()
			start()
		}
	}

	private fun stopVoicePulse() {
		voicePulseAnimator?.cancel()
		voicePulseAnimator = null
		binding.ivSearchMic.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
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

	private fun clearQuery() {
		binding.etSearchQuery.text.clear()
	}

	private fun scheduleSearch() {
		searchJob?.cancel()
		if (query.isBlank()) {
			showEmptyState()
			return
		}
		binding.searchEmptyState.visibility = View.GONE
		binding.vgvSearchResults.visibility = View.VISIBLE
		binding.tvResultsFor.visibility = View.VISIBLE
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
		binding.tvResultsFor.visibility = View.GONE
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
		voicePulseAnimator?.cancel()
		voicePulseAnimator = null
		_binding = null
		super.onDestroyView()
	}

}
