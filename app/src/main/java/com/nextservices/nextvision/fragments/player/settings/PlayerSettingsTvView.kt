package com.nextservices.nextvision.fragments.player.settings

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nextservices.nextvision.R
import com.nextservices.nextvision.databinding.ItemSettingTvBinding
import com.nextservices.nextvision.databinding.ViewPlayerSettingsTvBinding
import com.nextservices.nextvision.ui.SpacingItemDecoration
import com.nextservices.nextvision.utils.dp
import com.nextservices.nextvision.utils.margin
import com.nextservices.nextvision.utils.UserPreferences

class PlayerSettingsTvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerSettingsView(context, attrs, defStyleAttr) {

    val binding = ViewPlayerSettingsTvBinding.inflate(
        LayoutInflater.from(context),
        this,
        true
    )

    private val settingsAdapter = SettingsAdapter(this, Settings.listTv)
    private val qualityAdapter = SettingsAdapter(this, Settings.Quality.list)
    private val audioAdapter = SettingsAdapter(this, Settings.Audio.list)
    private val subtitlesAdapter = SettingsAdapter(this, Settings.Subtitle.list)
    private val subtitleShortcutAdapter = SettingsAdapter(this, emptyList())
    private val subtitleOffsetAdapter = SettingsAdapter(this, Settings.Subtitle.Offset.list)
    private val captionStyleAdapter = SettingsAdapter(this, Settings.Subtitle.Style.list)
    private val fontColorAdapter = SettingsAdapter(this, Settings.Subtitle.Style.FontColor.list)
    private val textSizeAdapter = SettingsAdapter(this, Settings.Subtitle.Style.TextSize.list)
    private val fontOpacityAdapter = SettingsAdapter(this, Settings.Subtitle.Style.FontOpacity.list)
    private val edgeStyleAdapter = SettingsAdapter(this, Settings.Subtitle.Style.EdgeStyle.list)
    private val backgroundColorAdapter = SettingsAdapter(this, Settings.Subtitle.Style.BackgroundColor.list)
    private val backgroundOpacityAdapter = SettingsAdapter(this, Settings.Subtitle.Style.BackgroundOpacity.list)
    private val windowColorAdapter = SettingsAdapter(this, Settings.Subtitle.Style.WindowColor.list)
    private val windowOpacityAdapter = SettingsAdapter(this, Settings.Subtitle.Style.WindowOpacity.list)
    private val openSubtitlesAdapter = SettingsAdapter(this, Settings.Subtitle.OpenSubtitles.list)
    private val subDLAdapter = SettingsAdapter(this, Settings.Subtitle.SubDLSubtitles.list)
    private val speedAdapter = SettingsAdapter(this, Settings.Speed.list)
    private val extraBufferingAdapter = SettingsAdapter(this, Settings.ExtraBuffering.list)
    private val softwareDecoderAdapter = SettingsAdapter(this, Settings.SoftwareDecoder.list)
    private val serversAdapter = SettingsAdapter(this, Settings.Server.list)
    private val marginAdapter = SettingsAdapter(this, Settings.Subtitle.Style.Margin.list)

    override var onSubtitlesClicked: (() -> Unit)? = null
    var onManualZoomClicked: (() -> Unit)? = null

    init {
        binding.rvSettings.addItemDecoration(SpacingItemDecoration(6.dp(context)))
        binding.rvSettings.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(context, R.anim.layout_anim_settings)
    }

    override fun onSubtitleSettingsChanged() {
        subtitleShortcutAdapter.replaceItems(
            Settings.Subtitle.list
        )
        binding.rvSettings.adapter?.notifyDataSetChanged()
        if (isShortcutPanel && currentSettings == Setting.SUBTITLES) {
            focusSelectedItem()
        }
    }

    private var pendingItem: Item? = null
    private var pendingBinding: ItemSettingTvBinding? = null
    private var isShortcutPanel = false
    private val pendingHandler = Handler(Looper.getMainLooper())
    private val pendingTimeout = Runnable { onPendingSelectionResult(false) }

    fun hasPendingSelection() = pendingItem != null

    private fun beginPendingSelection(item: Item, itemBinding: ItemSettingTvBinding): Boolean {
        if (pendingItem != null) return false
        pendingItem = item
        pendingBinding = itemBinding
        applyPendingState(itemBinding, loading = true, error = false)
        pendingHandler.removeCallbacks(pendingTimeout)
        pendingHandler.postDelayed(pendingTimeout, PENDING_TIMEOUT_MS)
        return true
    }

    /** Called by the player once the requested change has been applied (or has failed). */
    fun onPendingSelectionResult(success: Boolean) {
        if (pendingItem == null) return
        pendingHandler.removeCallbacks(pendingTimeout)
        val itemBinding = pendingBinding
        pendingItem = null
        if (success) {
            pendingBinding = null
            itemBinding?.let { applyPendingState(it, loading = false, error = false) }
            hide()
        } else {
            itemBinding?.let { applyPendingState(it, loading = false, error = true) }
        }
    }

    private fun completePendingSelectionSoon() {
        pendingHandler.postDelayed({ onPendingSelectionResult(true) }, PENDING_MIN_FEEDBACK_MS)
    }

    private fun clearPendingSelection() {
        pendingHandler.removeCallbacksAndMessages(null)
        pendingItem = null
        pendingBinding = null
    }

    private fun applyPendingState(
        itemBinding: ItemSettingTvBinding,
        loading: Boolean,
        error: Boolean,
    ) {
        itemBinding.pbSettingLoading.visibility = if (loading) View.VISIBLE else View.GONE
        itemBinding.ivSettingError.visibility = if (error) View.VISIBLE else View.GONE
        if (loading || error) {
            itemBinding.ivSettingIsSelected.visibility = View.GONE
            itemBinding.ivSettingEnter.visibility = View.GONE
        }
    }

    fun onBackPressed(): Boolean {
        clearPendingSelection()
        if (isShortcutPanel) {
            hide()
            return true
        }
        when (currentSettings) {
            Setting.MAIN -> hide()
            Setting.QUALITY,
            Setting.AUDIO,
            Setting.SUBTITLES,
            Setting.SPEED,
            Setting.EXTRA_BUFFERING,
            Setting.SOFTWARE_DECODER,
            Setting.SERVERS,
            Setting.GESTURES,
            Setting.KEEP_SCREEN_ON,
            Setting.MANUAL_ZOOM -> displaySettings(Setting.MAIN)
            Setting.SUBTITLE_OFFSET -> displaySettings(Setting.SUBTITLES)
            Setting.CAPTION_STYLE -> displaySettings(Setting.SUBTITLES)
            Setting.CAPTION_STYLE_FONT_COLOR,
            Setting.CAPTION_STYLE_TEXT_SIZE,
            Setting.CAPTION_STYLE_FONT_OPACITY,
            Setting.CAPTION_STYLE_EDGE_STYLE,
            Setting.CAPTION_STYLE_BACKGROUND_COLOR,
            Setting.CAPTION_STYLE_BACKGROUND_OPACITY,
            Setting.CAPTION_STYLE_WINDOW_COLOR,
            Setting.CAPTION_STYLE_WINDOW_OPACITY,
            Setting.CAPTION_STYLE_MARGIN -> displaySettings(Setting.CAPTION_STYLE)
            Setting.OPEN_SUBTITLES -> displaySettings(Setting.SUBTITLES)
            Setting.SUBDL -> displaySettings(Setting.SUBTITLES)
        }
        return true
    }

    override fun focusSearch(focused: View, direction: Int): View {
        return when {
            binding.rvSettings.hasFocus() -> focused
            else -> super.focusSearch(focused, direction)
        }
    }


    fun show() {
        isShortcutPanel = false
        showSetting(Setting.MAIN)
    }

    fun showQuality() = showShortcutSetting(Setting.QUALITY)
    fun showServer() = showShortcutSetting(Setting.SERVERS)
    fun showAudio() = showShortcutSetting(Setting.AUDIO)
    fun showSubtitles() = showShortcutSetting(Setting.SUBTITLES)

    private fun showShortcutSetting(setting: Setting) {
        isShortcutPanel = true
        showSetting(setting)
    }

    private fun showSetting(setting: Setting) {
        isHiding = false
        this.visibility = View.VISIBLE

        binding.clSettingsPanel.apply {
            animate().cancel()
            alpha = 0f
            translationX = PANEL_SLIDE_PX.dp(context).toFloat()
            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(PANEL_ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        displaySettings(setting)
    }

    private fun displaySettings(setting: Setting) {
        currentSettings = setting
        clearPendingSelection()

        if (setting == Setting.SUBTITLES) {
            onSubtitlesClicked?.invoke()
        }

        binding.tvSettingsHeader.apply {
            text = when (setting) {
                Setting.MAIN -> context.getString(R.string.player_settings_title)
                Setting.QUALITY -> context.getString(R.string.player_settings_quality_title)
                Setting.AUDIO -> context.getString(R.string.player_settings_audio_title)
                Setting.SUBTITLES -> context.getString(R.string.player_settings_subtitles_title)
                Setting.SUBTITLE_OFFSET -> context.getString(R.string.player_settings_subtitle_offset_title)
                Setting.CAPTION_STYLE -> context.getString(R.string.player_settings_caption_style_title)
                Setting.CAPTION_STYLE_FONT_COLOR -> context.getString(R.string.player_settings_caption_style_font_color_title)
                Setting.CAPTION_STYLE_TEXT_SIZE -> context.getString(R.string.player_settings_caption_style_text_size_title)
                Setting.CAPTION_STYLE_FONT_OPACITY -> context.getString(R.string.player_settings_caption_style_font_opacity_title)
                Setting.CAPTION_STYLE_EDGE_STYLE -> context.getString(R.string.player_settings_caption_style_edge_style_title)
                Setting.CAPTION_STYLE_BACKGROUND_COLOR -> context.getString(R.string.player_settings_caption_style_background_color_title)
                Setting.CAPTION_STYLE_BACKGROUND_OPACITY -> context.getString(R.string.player_settings_caption_style_background_opacity_title)
                Setting.CAPTION_STYLE_WINDOW_COLOR -> context.getString(R.string.player_settings_caption_style_window_color_title)
                Setting.CAPTION_STYLE_WINDOW_OPACITY -> context.getString(R.string.player_settings_caption_style_window_opacity_title)
                Setting.OPEN_SUBTITLES -> context.getString(R.string.player_settings_open_subtitles_title)
                Setting.SUBDL -> context.getString(R.string.player_settings_subdl_title)
                Setting.SPEED -> context.getString(R.string.player_settings_speed_title)
                Setting.EXTRA_BUFFERING -> context.getString(R.string.player_settings_extra_buffer_title)
                Setting.SOFTWARE_DECODER -> context.getString(R.string.player_settings_software_decoder_title)
                Setting.SERVERS -> context.getString(R.string.player_settings_servers_title)
                Setting.CAPTION_STYLE_MARGIN -> context.getString(R.string.player_settings_caption_style_margin_title)
                Setting.GESTURES -> context.getString(R.string.player_settings_gestures_title)
                Setting.KEEP_SCREEN_ON -> context.getString(R.string.player_settings_keep_screen_on_title)
                Setting.MANUAL_ZOOM -> context.getString(R.string.player_settings_manual_zoom_label)
            }
        }

        binding.rvSettings.adapter = when (setting) {
            Setting.MAIN -> settingsAdapter
            Setting.QUALITY -> qualityAdapter
            Setting.AUDIO -> audioAdapter
            Setting.SUBTITLES -> if (isShortcutPanel) subtitleShortcutAdapter else subtitlesAdapter
            Setting.SUBTITLE_OFFSET -> subtitleOffsetAdapter
            Setting.CAPTION_STYLE -> captionStyleAdapter
            Setting.CAPTION_STYLE_FONT_COLOR -> fontColorAdapter
            Setting.CAPTION_STYLE_TEXT_SIZE -> textSizeAdapter
            Setting.CAPTION_STYLE_FONT_OPACITY -> fontOpacityAdapter
            Setting.CAPTION_STYLE_EDGE_STYLE -> edgeStyleAdapter
            Setting.CAPTION_STYLE_BACKGROUND_COLOR -> backgroundColorAdapter
            Setting.CAPTION_STYLE_BACKGROUND_OPACITY -> backgroundOpacityAdapter
            Setting.CAPTION_STYLE_WINDOW_COLOR -> windowColorAdapter
            Setting.CAPTION_STYLE_WINDOW_OPACITY -> windowOpacityAdapter
            Setting.OPEN_SUBTITLES -> openSubtitlesAdapter
            Setting.SUBDL -> subDLAdapter
            Setting.SPEED -> speedAdapter
            Setting.EXTRA_BUFFERING -> extraBufferingAdapter
            Setting.SOFTWARE_DECODER -> softwareDecoderAdapter
            Setting.SERVERS -> serversAdapter
            Setting.CAPTION_STYLE_MARGIN -> marginAdapter
            else -> settingsAdapter
        }

        binding.rvSettings.scheduleLayoutAnimation()

        focusSelectedItem()
    }

    private fun focusSelectedItem() {
        val adapter = binding.rvSettings.adapter as? SettingsAdapter ?: return
        if (adapter.itemCount == 0) return
        val selectedPosition = adapter.selectedPosition().takeIf { it >= 0 } ?: 0
        binding.rvSettings.clearFocus()

        binding.rvSettings.post {
            (binding.rvSettings.layoutManager as? LinearLayoutManager)
                ?.scrollToPosition(selectedPosition)
            binding.rvSettings.post {
                binding.rvSettings.findViewHolderForAdapterPosition(selectedPosition)
                    ?.itemView
                    ?.requestFocus()
            }
        }
    }

    private fun focusSelectedSubtitleOffset() {
        val selectedOffset = Settings.Subtitle.Offset.selected.milliseconds
        val selectedPosition = Settings.Subtitle.Offset.list.indexOfFirst {
            it is Settings.Subtitle.Offset.Value && it.milliseconds == selectedOffset
        }

        if (selectedPosition < 0) {
            binding.rvSettings.requestFocus()
            return
        }

        binding.rvSettings.post {
            binding.rvSettings.doOnNextLayout {
                binding.rvSettings.findViewHolderForAdapterPosition(selectedPosition)
                    ?.itemView
                    ?.requestFocus()
            }
            (binding.rvSettings.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(selectedPosition, binding.rvSettings.height / 2)
        }
    }

    private var isHiding = false

    fun hide() {
        clearPendingSelection()
        if (visibility != View.VISIBLE || isHiding) return
        isHiding = true
        binding.clSettingsPanel.apply {
            animate().cancel()
            animate()
                .alpha(0f)
                .translationX(PANEL_SLIDE_PX.dp(context).toFloat())
                .setDuration(PANEL_ANIMATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    isHiding = false
                    this@PlayerSettingsTvView.visibility = View.GONE
                }
                .start()
        }
    }

    private companion object {
        const val PENDING_TIMEOUT_MS = 25_000L
        const val PENDING_MIN_FEEDBACK_MS = 250L
        const val PANEL_ANIMATION_MS = 180L
        const val PANEL_SLIDE_PX = 24
    }


    private class SettingsAdapter(
        private val settingsView: PlayerSettingsTvView,
        private var items: List<Item>,
    ) : RecyclerView.Adapter<SettingViewHolder>() {

        fun replaceItems(items: List<Item>) {
            this.items = items
            notifyDataSetChanged()
        }

        fun selectedPosition(): Int = items.indexOfFirst { item ->
            when (item) {
                is Settings.Quality -> item.isSelected
                is Settings.Audio -> item.isSelected
                is Settings.Subtitle.None -> item.isSelected
                is Settings.Subtitle.TextTrackInformation -> item.isSelected
                is Settings.Subtitle.Offset.Value -> item.isSelected
                is Settings.Subtitle.Style.FontColor -> item.isSelected
                is Settings.Subtitle.Style.TextSize -> item.isSelected
                is Settings.Subtitle.Style.FontOpacity -> item.isSelected
                is Settings.Subtitle.Style.EdgeStyle -> item.isSelected
                is Settings.Subtitle.Style.BackgroundColor -> item.isSelected
                is Settings.Subtitle.Style.BackgroundOpacity -> item.isSelected
                is Settings.Subtitle.Style.WindowColor -> item.isSelected
                is Settings.Subtitle.Style.WindowOpacity -> item.isSelected
                is Settings.Subtitle.Style.Margin -> item.isSelected
                is Settings.Speed -> item.isSelected
                is Settings.ExtraBuffering -> item.isSelected
                is Settings.SoftwareDecoder -> item.isSelected
                is Settings.Server -> item.isSelected
                else -> false
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            SettingViewHolder(
                settingsView,
                ItemSettingTvBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
            holder.displaySettings(items[position])
        }

        override fun getItemCount() = items.size
    }

    private class SettingViewHolder(
        private val settingsView: PlayerSettingsTvView,
        private val binding: ItemSettingTvBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun displaySettings(item: Item) {
            binding.pbSettingLoading.visibility = View.GONE
            binding.ivSettingError.visibility = View.GONE

            binding.root.apply {
                when (item) {
                    Settings.Subtitle.Style,
                    Settings.Subtitle.Style.ResetStyle -> margin(bottom = 6.dp(context))
                    Settings.Subtitle.LocalSubtitles -> margin(top = 6.dp(context))
                    else -> margin(bottom = 0, top = 0)
                }
                setOnClickListener {
                    if (settingsView.hasPendingSelection()) return@setOnClickListener
                    when (item) {
                        is Settings -> {
                            when (item) {
                                Settings.Quality -> settingsView.displaySettings(Setting.QUALITY)
                                Settings.Audio -> settingsView.displaySettings(Setting.AUDIO)
                                Settings.Subtitle -> settingsView.displaySettings(Setting.SUBTITLES)
                                Settings.Speed -> settingsView.displaySettings(Setting.SPEED)
                                Settings.ExtraBuffering -> settingsView.displaySettings(Setting.EXTRA_BUFFERING)
                                Settings.SoftwareDecoder -> settingsView.displaySettings(Setting.SOFTWARE_DECODER)
                                Settings.Server -> settingsView.displaySettings(Setting.SERVERS)
                                Settings.ManualZoom -> {
                                    settingsView.onManualZoomClicked?.invoke()
                                    settingsView.hide()
                                }
                                else -> {}
                            }
                        }

                        is Settings.Quality -> {
                            settingsView.onQualitySelected.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.Audio -> {
                            settingsView.onAudioSelected.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.Subtitle -> {
                            when (item) {
                                Settings.Subtitle.Style -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE)
                                }

                                Settings.Subtitle.Offset -> {
                                    settingsView.displaySettings(Setting.SUBTITLE_OFFSET)
                                }

                                is Settings.Subtitle.None,
                                is Settings.Subtitle.TextTrackInformation -> {
                                    settingsView.onSubtitleSelected.invoke(item)
                                    settingsView.hide()
                                }

                                Settings.Subtitle.LocalSubtitles -> {
                                    settingsView.onLocalSubtitlesClicked?.invoke()
                                    settingsView.hide()
                                }

                                Settings.Subtitle.OpenSubtitles -> {
                                    settingsView.displaySettings(Setting.OPEN_SUBTITLES)
                                }

                                Settings.Subtitle.SubDLSubtitles -> {
                                    settingsView.displaySettings(Setting.SUBDL)
                                }
                            }
                        }

                        is Settings.Subtitle.Offset.Value -> {
                            settingsView.onSubtitleOffsetSelected.invoke(item)
                            settingsView.displaySettings(Setting.SUBTITLES)
                        }

                        is Settings.Subtitle.Style -> {
                            when (item) {
                                Settings.Subtitle.Style.ResetStyle -> {
                                    settingsView.onTextSizeSelected.invoke(Settings.Subtitle.Style.TextSize.DEFAULT)
                                    settingsView.onCaptionStyleChanged.invoke(Settings.Subtitle.Style.DEFAULT)
                                    settingsView.hide()
                                }
                                Settings.Subtitle.Style.FontColor -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_FONT_COLOR)
                                }
                                Settings.Subtitle.Style.TextSize -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_TEXT_SIZE)
                                }
                                Settings.Subtitle.Style.FontOpacity -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_FONT_OPACITY)
                                }
                                Settings.Subtitle.Style.EdgeStyle -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_EDGE_STYLE)
                                }
                                Settings.Subtitle.Style.BackgroundColor -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_BACKGROUND_COLOR)
                                }
                                Settings.Subtitle.Style.BackgroundOpacity -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_BACKGROUND_OPACITY)
                                }
                                Settings.Subtitle.Style.WindowColor -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_WINDOW_COLOR)
                                }
                                Settings.Subtitle.Style.WindowOpacity -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_WINDOW_OPACITY)
                                }
                                Settings.Subtitle.Style.Margin -> {
                                    settingsView.displaySettings(Setting.CAPTION_STYLE_MARGIN)
                                }
                            }
                        }

                        is Settings.Subtitle.Style.FontColor -> {
                            settingsView.onFontColorSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.TextSize -> {
                            settingsView.onTextSizeSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.FontOpacity -> {
                            settingsView.onFontOpacitySelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.EdgeStyle -> {
                            settingsView.onEdgeStyleSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.BackgroundColor -> {
                            settingsView.onBackgroundColorSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.BackgroundOpacity -> {
                            settingsView.onBackgroundOpacitySelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.WindowColor -> {
                            settingsView.onWindowColorSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.WindowOpacity -> {
                            settingsView.onWindowOpacitySelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.Style.Margin -> {
                            settingsView.onMarginSelected.invoke(item)
                            settingsView.displaySettings(Setting.CAPTION_STYLE)
                        }

                        is Settings.Subtitle.OpenSubtitles.Subtitle -> {
                            settingsView.onOpenSubtitleSelected?.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.Subtitle.SubDLSubtitles.Subtitle -> {
                            settingsView.onSubDLSubtitleSelected?.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.Speed -> {
                            settingsView.onSpeedSelected.invoke(item)
                            settingsView.hide()
                        }



                        is Settings.ExtraBuffering -> {
                            settingsView.onExtraBufferingSelected.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.SoftwareDecoder -> {
                            settingsView.onSoftwareDecoderSelected.invoke(item)
                            settingsView.hide()
                        }

                        is Settings.Server -> {
                            settingsView.onServerSelected?.invoke(item)
                            settingsView.hide()
                        }
                        else -> {}
                    }
                }
            }

            binding.ivSettingIcon.apply {
                when (item) {
                    is Settings -> {
                        when (item) {
                            Settings.Quality -> setImageDrawable(
                                ContextCompat.getDrawable(context, R.drawable.ic_player_settings_quality)
                            )
                            Settings.Audio -> setImageDrawable(
                                ContextCompat.getDrawable(context, R.drawable.ic_player_settings_audio)
                            )
                            Settings.Subtitle -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    when (Settings.Subtitle.selected) {
                                        is Settings.Subtitle.TextTrackInformation -> R.drawable.ic_player_settings_subtitle_on
                                        else -> R.drawable.ic_player_settings_subtitle_off
                                    }
                                )
                            )
                            Settings.Speed -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_player_settings_playback_speed
                                )
                                )

                            Settings.ExtraBuffering -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_player_settings_extra_buffer
                                )
                            )

                            Settings.SoftwareDecoder -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_player_settings_extra_buffer
                                )
                            )

                            Settings.Server -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_player_settings_servers
                                )
                            )
                            Settings.ManualZoom -> setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.exo_styled_controls_aspect_ratio
                                )
                            )
                            else -> {}
                        }
                        visibility = View.VISIBLE
                    }

                    else -> {
                        visibility = View.GONE
                    }
                }
            }

            binding.vSettingColor.apply {
                when (item) {
                    is Settings.Subtitle.Style.FontColor -> {
                        backgroundTintList = ColorStateList.valueOf(item.color)
                        visibility = View.VISIBLE
                    }

                    is Settings.Subtitle.Style.BackgroundColor -> {
                        backgroundTintList = ColorStateList.valueOf(item.color)
                        visibility = View.VISIBLE
                    }

                    is Settings.Subtitle.Style.WindowColor -> {
                        backgroundTintList = ColorStateList.valueOf(item.color)
                        visibility = View.VISIBLE
                    }

                    else -> {
                        visibility = View.GONE
                    }
                }
            }

            binding.tvSettingMainText.apply {
                text = when (item) {
                    is Settings -> when (item) {
                        Settings.Quality -> context.getString(R.string.player_settings_quality_label)
                        Settings.Audio -> context.getString(R.string.player_settings_audio_label)
                        Settings.Subtitle -> context.getString(R.string.player_settings_subtitles_label)
                        Settings.Speed -> context.getString(R.string.player_settings_speed_label)
                        Settings.ExtraBuffering -> context.getString(R.string.player_settings_extra_buffer_server_label)
                        Settings.SoftwareDecoder -> context.getString(R.string.player_settings_software_decoder_label)
                        Settings.Server -> context.getString(R.string.player_settings_servers_label)
                        Settings.ManualZoom -> context.getString(R.string.player_settings_manual_zoom_label)
                        else -> ""
                    }

                    is Settings.Audio -> when (item) {
                        is Settings.Audio.AudioTrackInformation -> item.name
                    }

                    is Settings.Quality -> when (item) {
                        is Settings.Quality.Auto -> when {
                            item.isSelected -> when (val track = item.currentTrack) {
                                null -> context.getString(R.string.player_settings_quality_auto)
                                else -> context.getString(
                                    R.string.player_settings_quality_auto_selected,
                                    track.height
                                )
                            }

                            else -> context.getString(R.string.player_settings_quality_auto)
                        }
                        is Settings.Quality.VideoTrackInformation -> context.getString(
                            R.string.player_settings_quality,
                            item.height
                        )
                    }

                    is Settings.Subtitle -> when (item) {
                        Settings.Subtitle.Style -> context.getString(R.string.player_settings_caption_style_label)
                        Settings.Subtitle.Offset -> context.getString(R.string.player_settings_subtitle_offset_label)
                        is Settings.Subtitle.None -> context.getString(R.string.player_settings_subtitles_off)
                        is Settings.Subtitle.TextTrackInformation -> item.label.ifEmpty { item.name }
                        Settings.Subtitle.LocalSubtitles -> context.getString(R.string.player_settings_local_subtitles_label)
                        Settings.Subtitle.OpenSubtitles -> context.getString(R.string.player_settings_open_subtitles_label)
                        Settings.Subtitle.SubDLSubtitles -> context.getString(R.string.player_settings_subdl_label)
                    }

                    is Settings.Subtitle.Offset.Value -> context.getString(
                        R.string.player_settings_subtitle_offset_seconds,
                        item.milliseconds / 1_000.0,
                    )

                    is Settings.Subtitle.Style -> when (item) {
                        Settings.Subtitle.Style.ResetStyle -> context.getString(R.string.player_settings_caption_style_reset_style_label)
                        Settings.Subtitle.Style.FontColor -> context.getString(R.string.player_settings_caption_style_font_color_label)
                        Settings.Subtitle.Style.TextSize -> context.getString(R.string.player_settings_caption_style_text_size_label)
                        Settings.Subtitle.Style.FontOpacity -> context.getString(R.string.player_settings_caption_style_font_opacity_label)
                        Settings.Subtitle.Style.EdgeStyle -> context.getString(R.string.player_settings_caption_style_edge_style_label)
                        Settings.Subtitle.Style.BackgroundColor -> context.getString(R.string.player_settings_caption_style_background_color_label)
                        Settings.Subtitle.Style.BackgroundOpacity -> context.getString(R.string.player_settings_caption_style_background_opacity_label)
                        Settings.Subtitle.Style.WindowColor -> context.getString(R.string.player_settings_caption_style_window_color_label)
                        Settings.Subtitle.Style.WindowOpacity -> context.getString(R.string.player_settings_caption_style_window_opacity_label)
                        Settings.Subtitle.Style.Margin -> context.getString(R.string.player_settings_caption_style_margin_label)
                    }

                    is Settings.Subtitle.Style.FontColor -> context.getString(item.stringId)
                    is Settings.Subtitle.Style.Margin -> item.value.toString()

                    is Settings.Subtitle.Style.TextSize -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.FontOpacity -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.EdgeStyle -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.BackgroundColor -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.BackgroundOpacity -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.WindowColor -> context.getString(item.stringId)

                    is Settings.Subtitle.Style.WindowOpacity -> context.getString(item.stringId)

                    is Settings.Subtitle.OpenSubtitles.Subtitle -> item.openSubtitle.subFileName

                    is Settings.Subtitle.SubDLSubtitles.Subtitle -> item.subDLSubtitle.releaseName ?: item.subDLSubtitle.name

                    is Settings.Speed -> context.getString(item.stringId)

                    is Settings.ExtraBuffering -> context.getString(item.stringId)

                    is Settings.SoftwareDecoder -> context.getString(item.stringId)

                    is Settings.Server -> item.name

                    else -> ""
                }
            }

            binding.tvSettingSubText.apply {
                text = when (item) {
                    is Settings -> when (item) {
                        Settings.Quality -> when (val selected = Settings.Quality.selected) {
                            is Settings.Quality.Auto -> when (val track = selected.currentTrack) {
                                null -> context.getString(R.string.player_settings_quality_auto)
                                else -> context.getString(
                                    R.string.player_settings_quality_auto_selected,
                                    track.height
                                )
                            }
                            is Settings.Quality.VideoTrackInformation -> context.getString(
                                R.string.player_settings_quality,
                                selected.height
                            )
                        }
                        Settings.Audio -> Settings.Audio.selected?.name
                        Settings.Subtitle -> when (val selected = Settings.Subtitle.selected) {
                            is Settings.Subtitle.TextTrackInformation -> selected.label
                            else -> context.getString(R.string.player_settings_subtitles_off)
                        }
                        Settings.Speed -> context.getString(Settings.Speed.selected.stringId)
                        Settings.ExtraBuffering -> context.getString(Settings.ExtraBuffering.selected.stringId)
                        Settings.Server -> Settings.Server.selected?.name ?: ""
                        Settings.ManualZoom -> ""
                        else -> ""
                    }

                    is Settings.Subtitle -> when (item) {
                        Settings.Subtitle.Style -> context.getString(R.string.player_settings_caption_style_sub_label)
                        Settings.Subtitle.Offset -> context.getString(
                            R.string.player_settings_subtitle_offset_seconds,
                            Settings.Subtitle.Offset.selected.milliseconds / 1_000.0,
                        )
                        is Settings.Subtitle.TextTrackInformation -> item.language ?: ""
                        else -> ""
                    }

                    is Settings.Subtitle.Style -> when (item) {
                        Settings.Subtitle.Style.ResetStyle -> ""
                        Settings.Subtitle.Style.FontColor -> context.getString(Settings.Subtitle.Style.FontColor.selected.stringId)
                        Settings.Subtitle.Style.TextSize -> context.getString(Settings.Subtitle.Style.TextSize.selected.stringId)
                        Settings.Subtitle.Style.FontOpacity -> context.getString(Settings.Subtitle.Style.FontOpacity.selected.stringId)
                        Settings.Subtitle.Style.EdgeStyle -> context.getString(Settings.Subtitle.Style.EdgeStyle.selected.stringId)
                        Settings.Subtitle.Style.BackgroundColor -> context.getString(Settings.Subtitle.Style.BackgroundColor.selected.stringId)
                        Settings.Subtitle.Style.BackgroundOpacity -> context.getString(Settings.Subtitle.Style.BackgroundOpacity.selected.stringId)
                        Settings.Subtitle.Style.WindowColor -> context.getString(Settings.Subtitle.Style.WindowColor.selected.stringId)
                        Settings.Subtitle.Style.WindowOpacity -> context.getString(Settings.Subtitle.Style.WindowOpacity.selected.stringId)
                        Settings.Subtitle.Style.Margin -> Settings.Subtitle.Style.Margin.selected.value.toString()
                    }

                    is Settings.Subtitle.OpenSubtitles.Subtitle -> item.openSubtitle.languageName

                    is Settings.Subtitle.SubDLSubtitles.Subtitle -> item.subDLSubtitle.lang?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } ?: ""

                    else -> ""
                }
                visibility = when {
                    text.isEmpty() -> View.GONE
                    else -> View.VISIBLE
                }
            }

            binding.ivSettingIsSelected.apply {
                visibility = when (item) {
                    is Settings.Quality -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Audio -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle -> when (item) {
                        is Settings.Subtitle.None -> when {
                            item.isSelected -> View.VISIBLE
                            else -> View.GONE
                        }
                        is Settings.Subtitle.TextTrackInformation -> when {
                            item.isSelected -> View.VISIBLE
                            else -> View.GONE
                        }
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.FontColor -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.TextSize -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.FontOpacity -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.EdgeStyle -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.BackgroundColor -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.BackgroundOpacity -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.WindowColor -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.WindowOpacity -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Style.Margin -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Subtitle.Offset.Value -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Speed -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.ExtraBuffering -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.SoftwareDecoder -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    is Settings.Server -> when {
                        item.isSelected -> View.VISIBLE
                        else -> View.GONE
                    }

                    else -> View.GONE
                }
            }

            binding.ivSettingEnter.apply {
                visibility = when (item) {
                    is Settings -> {
                        when(item) {
                            Settings.Quality,
                            Settings.Audio,
                            Settings.Subtitle,
                            Settings.Speed,
                            Settings.ExtraBuffering,
                            Settings.SoftwareDecoder,
                            Settings.Server -> View.VISIBLE
                            else -> View.GONE
                        }
                    }

                    is Settings.Subtitle -> when (item) {
                        Settings.Subtitle.Style -> View.VISIBLE
                        Settings.Subtitle.Offset -> View.VISIBLE
                        is Settings.Subtitle.None -> View.GONE
                        is Settings.Subtitle.TextTrackInformation -> View.GONE
                        Settings.Subtitle.LocalSubtitles -> View.VISIBLE
                        Settings.Subtitle.OpenSubtitles -> View.VISIBLE
                        Settings.Subtitle.SubDLSubtitles -> View.VISIBLE
                    }

                    is Settings.Subtitle.Style -> when (item) {
                        Settings.Subtitle.Style.ResetStyle -> View.GONE
                        else -> View.VISIBLE
                    }

                    else -> View.GONE
                }
            }
        }
    }
}
