package com.nextservices.nextvision.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nextservices.nextvision.R
import com.nextservices.nextvision.utils.TMDb3
import com.nextservices.nextvision.utils.TmdbFilterOptions
import java.util.Calendar

class TmdbFilterDialog(
    context: Context,
    private val isTv: Boolean,
    private val section: Section,
    private val initial: TmdbFilterOptions = TmdbFilterOptions(),
    private val onApply: (TmdbFilterOptions) -> Unit,
) : Dialog(context) {

    enum class Section { GENRE, YEAR, SORT }

    private var options = initial
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24.dp, 8.dp, 16.dp, 12.dp)
    }
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.getDrawable(R.drawable.bg_dialog_show_options_tv)
    }
    private val genreBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val yearOptions = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
    private val sortOptions = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }

    init {
        setContentView(root)
        root.addView(title(context.getString(section.titleRes)))
        root.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        when (section) {
            Section.GENRE -> body.addView(genreBox)
            Section.YEAR -> body.addView(yearOptions)
            Section.SORT -> body.addView(sortOptions)
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(24.dp, 8.dp, 24.dp, 20.dp)
        }
        actions.addView(button(R.string.tmdb_filter_reset) { resetFields() })
        actions.addView(button(R.string.tmdb_filter_back) { dismiss() })
        root.addView(actions)

        val sortValues = if (isTv) tvSortValues else movieSortValues
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (section == Section.YEAR) addYearOptions(currentYear)
        if (section == Section.SORT) addSortOptions(sortValues)

        if (section == Section.GENRE) addGenres(staticGenres)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            attributes = attributes?.also { it.gravity = Gravity.END }
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.38f).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
        root.alpha = 0f
        root.translationX = 32.dp.toFloat()
        root.animate().alpha(1f).translationX(0f).setDuration(220L).start()
        when (section) {
            Section.GENRE -> genreBox.getChildAt(0)?.requestFocus()
            Section.YEAR -> yearOptions.getChildAt(0)?.requestFocus()
            Section.SORT -> sortOptions.getChildAt(0)?.requestFocus()
        }
    }

    private fun addYearOptions(currentYear: Int) {
        addRadioOption(yearOptions, context.getString(R.string.tmdb_filter_any_year), initial.year == null) {
            options = options.copy(year = null)
            onApply(options)
        }
        (currentYear downTo 1980).forEach { year ->
            addRadioOption(yearOptions, year.toString(), initial.year == year) {
                options = options.copy(year = year)
                onApply(options)
            }
        }
    }

    private fun addSortOptions(sortValues: List<String>) {
        val labels = if (isTv) tvSortLabels else movieSortLabels
        labels.forEachIndexed { index, label ->
            addRadioOption(sortOptions, label, sortValues[index] == initial.sortBy) {
                options = options.copy(sortBy = sortValues[index])
                onApply(options)
            }
        }
    }

    private fun addRadioOption(group: RadioGroup, text: String, checked: Boolean, click: () -> Unit) {
        group.addView(RadioButton(context).apply {
            id = View.generateViewId()
            this.text = text
            isChecked = checked
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(ContextCompat.getColorStateList(context, R.color.filter_option_text))
            buttonTintList = ContextCompat.getColorStateList(context, R.color.filter_option_text)
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            setBackgroundResource(R.drawable.bg_item_option)
            layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp
            }
            setOnClickListener { click() }
        })
    }

    private fun addGenres(genres: List<TMDb3.Genre>) {
        genres.forEach { genre ->
            genreBox.addView(CheckBox(context).apply {
                text = genre.name
                tag = genre.id
                isChecked = genre.id in options.genres
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(ContextCompat.getColorStateList(context, R.color.filter_option_text))
                buttonTintList = ContextCompat.getColorStateList(context, R.color.filter_option_text)
                setPadding(12.dp, 12.dp, 12.dp, 12.dp)
                setBackgroundResource(R.drawable.bg_item_option)
                layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8.dp
                }
                setOnCheckedChangeListener { _, _ ->
                    options = options.copy(genres = (0 until genreBox.childCount)
                        .mapNotNull { genreBox.getChildAt(it) as? CheckBox }
                        .filter { it.isChecked }
                        .mapNotNull { it.tag as? Int }
                        .toSet())
                    onApply(options)
                }
            })
        }
    }

    private fun resetFields() {
        options = when (section) {
            Section.GENRE -> options.copy(genres = emptySet())
            Section.YEAR -> options.copy(year = null)
            Section.SORT -> options.copy(sortBy = "popularity.desc")
        }
        (0 until genreBox.childCount).forEach { (genreBox.getChildAt(it) as? CheckBox)?.isChecked = false }
        if (section == Section.YEAR) yearOptions.check(yearOptions.getChildAt(0).id)
        if (section == Section.SORT) sortOptions.check(sortOptions.getChildAt(0).id)
        onApply(options)
    }

    private fun title(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 23f
        setPadding(24.dp, 28.dp, 24.dp, 8.dp)
    }

    private fun button(textRes: Int, click: () -> Unit) = TextView(context).apply {
        setText(textRes)
        setTextColor(ContextCompat.getColorStateList(context, R.color.filter_option_text))
        setPadding(18.dp, 14.dp, 18.dp, 14.dp)
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundResource(R.drawable.bg_item_option)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = 8.dp
        }
        setOnClickListener { click() }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        val Section.titleRes: Int
            get() = when (this) {
                Section.GENRE -> R.string.tmdb_filter_genres
                Section.YEAR -> R.string.tmdb_filter_year
                Section.SORT -> R.string.tmdb_filter_sort
            }
        private val movieSortLabels = listOf("Most Popular", "Least Popular", "Top Rated", "Lowest Rated", "Most Voted", "Newest", "Oldest", "Title A-Z", "Title Z-A", "Highest Revenue", "Lowest Revenue")
        private val movieSortValues = listOf("popularity.desc", "popularity.asc", "vote_average.desc", "vote_average.asc", "vote_count.desc", "primary_release_date.desc", "primary_release_date.asc", "original_title.asc", "original_title.desc", "revenue.desc", "revenue.asc")
        private val tvSortLabels = listOf("Most Popular", "Least Popular", "Top Rated", "Lowest Rated", "Most Voted", "Newest", "Oldest", "Name A-Z", "Name Z-A")
        private val tvSortValues = listOf("popularity.desc", "popularity.asc", "vote_average.desc", "vote_average.asc", "vote_count.desc", "first_air_date.desc", "first_air_date.asc", "name.asc", "name.desc")
        private val staticGenres = listOf(
            TMDb3.Genre(28, "Action"),
            TMDb3.Genre(12, "Adventure"),
            TMDb3.Genre(16, "Animation"),
            TMDb3.Genre(35, "Comedy"),
            TMDb3.Genre(80, "Crime"),
            TMDb3.Genre(99, "Documentary"),
            TMDb3.Genre(18, "Drama"),
            TMDb3.Genre(10751, "Family"),
            TMDb3.Genre(14, "Fantasy"),
            TMDb3.Genre(36, "History"),
            TMDb3.Genre(27, "Horror"),
            TMDb3.Genre(10402, "Music"),
            TMDb3.Genre(9648, "Mystery"),
            TMDb3.Genre(10749, "Romance"),
            TMDb3.Genre(878, "Science Fiction"),
            TMDb3.Genre(10770, "TV Movie"),
            TMDb3.Genre(53, "Thriller"),
            TMDb3.Genre(10752, "War"),
            TMDb3.Genre(37, "Western"),
        )

        fun sortLabel(isTv: Boolean, value: String): String {
            val values = if (isTv) tvSortValues else movieSortValues
            val labels = if (isTv) tvSortLabels else movieSortLabels
            return labels.getOrElse(values.indexOf(value)) { labels.first() }
        }

        fun genreLabel(ids: Set<Int>): String {
            val names = staticGenres.filter { it.id in ids }.map { it.name }
            val first = names.firstOrNull() ?: return "Genre"
            return if (ids.size > 1) "$first +${ids.size - 1}" else first
        }
    }
}

fun bindTmdbFilterButtons(
    context: Context,
    isTv: Boolean,
    genreButton: TextView,
    yearButton: TextView,
    sortButton: TextView,
    clearButton: TextView,
    currentOptions: () -> TmdbFilterOptions,
    onApply: (TmdbFilterOptions) -> Unit,
) {
    fun updateLabels(options: TmdbFilterOptions, animate: Boolean) {
        val labels = listOf(
            genreButton to if (options.genres.isEmpty()) {
                context.getString(R.string.tmdb_filter_genre_any)
            } else {
                context.getString(
                    R.string.tmdb_filter_genre_selected,
                    TmdbFilterDialog.genreLabel(options.genres),
                )
            },
            yearButton to context.getString(
                R.string.tmdb_filter_year_value,
                options.year?.toString() ?: context.getString(R.string.tmdb_filter_any_year),
            ),
            sortButton to context.getString(
                R.string.tmdb_filter_sort_value,
                TmdbFilterDialog.sortLabel(isTv, options.sortBy),
            ),
        )
        labels.forEach { (button, label) ->
            if (button.text == label) return@forEach
            if (animate) {
                button.animate().alpha(0f).setDuration(80L).withEndAction {
                    button.text = label
                    button.animate().alpha(1f).setDuration(140L).start()
                }.start()
            } else {
                button.text = label
            }
        }
    }

    listOf(
        genreButton to TmdbFilterDialog.Section.GENRE,
        yearButton to TmdbFilterDialog.Section.YEAR,
        sortButton to TmdbFilterDialog.Section.SORT,
    ).forEach { (button, section) ->
        button.setOnFocusChangeListener { view, focused ->
            view.animate()
                .scaleX(if (focused) 1.04f else 1f)
                .scaleY(if (focused) 1.04f else 1f)
                .translationZ(if (focused) 8f else 0f)
                .setDuration(160L)
                .start()
        }
        button.setOnClickListener {
            TmdbFilterDialog(context, isTv, section, currentOptions()) { options ->
                updateLabels(options, animate = true)
                onApply(options)
            }.show()
        }
    }
    clearButton.setOnFocusChangeListener { view, focused ->
        view.animate()
            .scaleX(if (focused) 1.04f else 1f)
            .scaleY(if (focused) 1.04f else 1f)
            .translationZ(if (focused) 8f else 0f)
            .setDuration(160L)
            .start()
    }
    clearButton.setOnClickListener {
        val options = TmdbFilterOptions()
        updateLabels(options, animate = true)
        onApply(options)
    }
    updateLabels(currentOptions(), animate = false)
}
