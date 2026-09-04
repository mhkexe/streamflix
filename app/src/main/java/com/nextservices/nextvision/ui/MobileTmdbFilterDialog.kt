package com.nextservices.nextvision.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nextservices.nextvision.R
import com.nextservices.nextvision.utils.TMDb3
import com.nextservices.nextvision.utils.TmdbFilterOptions
import java.util.Calendar

class MobileTmdbFilterDialog(
    context: Context,
    private val isTv: Boolean,
    initial: TmdbFilterOptions,
    private val onApply: (TmdbFilterOptions) -> Unit,
) : BottomSheetDialog(context) {

    private var options = initial
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
    }
    private val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dp, 0, 20.dp, 12.dp)
    }
    private val genreBox = ChipGroup(context).apply {
        isSingleLine = false
        setChipSpacingHorizontal(8.dp)
        setChipSpacingVertical(8.dp)
    }
    private val yearOptions = AutoCompleteTextView(context)
    private val sortOptions = AutoCompleteTextView(context)

    init {
        setContentView(root)
        root.addView(View(context).apply {
            setBackgroundResource(R.drawable.bg_detail_sheet_handle)
            layoutParams = LinearLayout.LayoutParams(52.dp, 5.dp).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 10.dp
            }
        })
        root.addView(header())
        root.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(body)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(sectionLabel("Genres"))
        body.addView(genreBox)
        body.addView(sectionLabel("Year"))
        body.addView(yearOptions)
        body.addView(sectionLabel("Sort by"))
        body.addView(sortOptions)
        root.addView(actions())

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        addGenres(genres)
        addYears(currentYear)
        addSorts(if (isTv) tvSortValues else movieSortValues)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun header() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(20.dp, 16.dp, 12.dp, 8.dp)
        addView(TextView(context).apply {
            text = "Filters"
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(ImageButton(context).apply {
            contentDescription = "Close filters"
            setImageResource(R.drawable.ic_player_settings_close)
            imageTintList = ContextCompat.getColorStateList(context, R.color.filter_option_text)
            setBackgroundResource(R.drawable.bg_detail_sheet_close)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(44.dp, 44.dp))
    }

    private fun actions() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(20.dp, 10.dp, 20.dp, 20.dp)
        addView(actionButton("Apply", true) {
            onApply(options)
            dismiss()
        }, LinearLayout.LayoutParams(-1, 52.dp))
    }

    private fun sectionLabel(label: String) = TextView(context).apply {
        text = label
        setTextColor(Color.parseColor("#AEB7C4"))
        textSize = 13f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 18.dp, 0, 8.dp)
    }

    private fun actionButton(label: String, primary: Boolean, click: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else Color.parseColor("#D4DAE2"))
        textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setBackgroundColor(Color.TRANSPARENT)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 16.dp.toFloat()
            setColor(if (primary) Color.parseColor("#E50914") else Color.parseColor("#30343B"))
        }
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, 52.dp, if (primary) 1.6f else 1f).apply {
            marginEnd = if (primary) 0 else 8.dp
        }
    }

    private fun addGenres(items: List<TMDb3.Genre>) {
        items.forEach { genre ->
            genreBox.addView(Chip(context).apply {
                text = genre.name
                tag = genre.id
                isCheckable = true
                isChecked = genre.id in options.genres
                setTextColor(ColorStateList.valueOf(Color.WHITE))
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#30343B"))
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#4A515B"))
                chipStrokeWidth = 1.dp.toFloat()
                setEnsureMinTouchTargetSize(false)
                setOnClickListener {
                    val selected = (0 until genreBox.childCount)
                        .mapNotNull { genreBox.getChildAt(it) as? Chip }
                        .filter { it.isChecked }
                        .mapNotNull { it.tag as? Int }
                        .toSet()
                    options = options.copy(genres = selected)
                }
            })
        }
    }

    private fun addYears(currentYear: Int) {
        val years = buildList {
            add("Any year")
            addAll((currentYear downTo 1980).map { it.toString() })
        }
        setupDropdown(yearOptions, years, options.year?.toString() ?: "Any year") { selected ->
            options = options.copy(year = selected.toIntOrNull())
        }
    }

    private fun addSorts(values: List<String>) {
        val labels = if (isTv) tvSortLabels else movieSortLabels
        val selectedIndex = values.indexOf(options.sortBy).coerceAtLeast(0)
        setupDropdown(sortOptions, labels, labels[selectedIndex]) { selected ->
            options = options.copy(sortBy = values[labels.indexOf(selected)])
        }
    }

    private fun setupDropdown(
        field: AutoCompleteTextView,
        values: List<String>,
        selected: String,
        onSelected: (String) -> Unit,
    ) {
        field.setText(selected, false)
        field.setTextColor(Color.WHITE)
        field.setHintTextColor(Color.parseColor("#9BA4B0"))
        field.textSize = 15f
        field.setPadding(16.dp, 0, 16.dp, 0)
        field.setSingleLine(true)
        field.inputType = android.text.InputType.TYPE_NULL
        field.keyListener = null
        field.setBackgroundColor(Color.TRANSPARENT)
        field.setDropDownBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        field.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, values).apply {
            setDropDownViewResource(android.R.layout.simple_dropdown_item_1line)
        })
        field.setOnItemClickListener { _, _, position, _ -> onSelected(values[position]) }
        field.setOnClickListener { field.showDropDown() }
        field.layoutParams = LinearLayout.LayoutParams(-1, 52.dp).apply {
            topMargin = 2.dp
        }
        field.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 14.dp.toFloat()
            setColor(Color.parseColor("#17191D"))
            setStroke(1.dp, Color.parseColor("#41464F"))
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private val movieSortLabels = listOf("Most Popular", "Least Popular", "Top Rated", "Lowest Rated", "Most Voted", "Newest", "Oldest", "Title A-Z", "Title Z-A", "Highest Revenue", "Lowest Revenue")
        private val movieSortValues = listOf("popularity.desc", "popularity.asc", "vote_average.desc", "vote_average.asc", "vote_count.desc", "primary_release_date.desc", "primary_release_date.asc", "original_title.asc", "original_title.desc", "revenue.desc", "revenue.asc")
        private val tvSortLabels = listOf("Most Popular", "Least Popular", "Top Rated", "Lowest Rated", "Most Voted", "Newest", "Oldest", "Name A-Z", "Name Z-A")
        private val tvSortValues = listOf("popularity.desc", "popularity.asc", "vote_average.desc", "vote_average.asc", "vote_count.desc", "first_air_date.desc", "first_air_date.asc", "name.asc", "name.desc")
        private val genres = listOf(
            TMDb3.Genre(28, "Action"), TMDb3.Genre(12, "Adventure"), TMDb3.Genre(16, "Animation"),
            TMDb3.Genre(35, "Comedy"), TMDb3.Genre(80, "Crime"), TMDb3.Genre(99, "Documentary"),
            TMDb3.Genre(18, "Drama"), TMDb3.Genre(10751, "Family"), TMDb3.Genre(14, "Fantasy"),
            TMDb3.Genre(36, "History"), TMDb3.Genre(27, "Horror"), TMDb3.Genre(10402, "Music"),
            TMDb3.Genre(9648, "Mystery"), TMDb3.Genre(10749, "Romance"), TMDb3.Genre(878, "Science Fiction"),
            TMDb3.Genre(10770, "TV Movie"), TMDb3.Genre(53, "Thriller"), TMDb3.Genre(10752, "War"),
            TMDb3.Genre(37, "Western"),
        )
    }
}

fun bindMobileTmdbCatalogFilterButtons(
    context: Context,
    isTv: Boolean,
    filterButton: TextView,
    resetButton: TextView,
    currentOptions: () -> TmdbFilterOptions,
    onApply: (TmdbFilterOptions) -> Unit,
) {
    filterButton.isFocusable = false
    filterButton.isFocusableInTouchMode = false
    filterButton.isClickable = true
    resetButton.isFocusable = false
    resetButton.isFocusableInTouchMode = false
    resetButton.isClickable = true
    filterButton.setOnClickListener {
        MobileTmdbFilterDialog(context, isTv, currentOptions(), onApply).show()
    }
    resetButton.setOnClickListener { onApply(TmdbFilterOptions()) }
}
