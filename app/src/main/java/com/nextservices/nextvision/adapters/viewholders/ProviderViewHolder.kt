package com.nextservices.nextvision.adapters.viewholders

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nextservices.nextvision.R
import com.nextservices.nextvision.databinding.ItemProviderMobileBinding
import com.nextservices.nextvision.databinding.ItemProviderTvBinding
import com.nextservices.nextvision.models.Provider
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.toActivity

class ProviderViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var provider: Provider

    fun bind(provider: Provider) {
        this.provider = provider

        when (_binding) {
            is ItemProviderMobileBinding -> displayMobileItem(_binding)
            is ItemProviderTvBinding -> displayTvItem(_binding)
        }
    }


    private fun displayMobileItem(binding: ItemProviderMobileBinding) {
        binding.root.setOnClickListener { selectProvider() }
        binding.tvProviderName.text = provider.name
        loadFlag(binding.ivProviderLogo)
    }

    private fun displayTvItem(binding: ItemProviderTvBinding) {
        binding.root.setOnClickListener { selectProvider() }
        binding.tvProviderName.text = provider.name
        loadFlag(binding.ivProviderLogo)

        val tile = binding.flProviderTile
        val name = binding.tvProviderName
        val focused = binding.root.hasFocus()

        tile.scaleX = if (focused) FOCUSED_SCALE else 1f
        tile.scaleY = tile.scaleX
        name.alpha = if (focused) 1f else 0f
        name.translationY = if (focused) 0f else NAME_OFFSET_PX * name.resources.displayMetrics.density

        binding.root.setOnFocusChangeListener { view, hasFocus ->
            // keep the enlarged tile above its neighbours while focused
            view.z = if (hasFocus) 1f else 0f

            tile.animate()
                .scaleX(if (hasFocus) FOCUSED_SCALE else 1f)
                .scaleY(if (hasFocus) FOCUSED_SCALE else 1f)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(INTERPOLATOR)
                .start()

            name.animate()
                .alpha(if (hasFocus) 1f else 0f)
                .translationY(if (hasFocus) 0f else NAME_OFFSET_PX * name.resources.displayMetrics.density)
                .setDuration(ANIMATION_DURATION)
                .setInterpolator(INTERPOLATOR)
                .start()
        }
    }

    private fun selectProvider() {
        UserPreferences.currentProvider = provider.provider
        context.toActivity()?.apply {
            startActivity(
                Intent(this, this::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }

    private fun loadFlag(imageView: ImageView) {
        if (provider.logo.startsWith("res://")) {
            val resName = provider.logo.substringAfter("res://drawable/")
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                imageView.setImageResource(resId)
            } else {
                imageView.setImageResource(R.drawable.ic_provider_default_logo)
            }
        } else {
            Glide.with(context)
                .load(provider.logo.takeIf { it.isNotEmpty() } ?: R.drawable.ic_provider_default_logo)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        }
    }

    private companion object {
        const val FOCUSED_SCALE = 1.12f
        const val ANIMATION_DURATION = 180L
        const val NAME_OFFSET_PX = 6f
        val INTERPOLATOR = FastOutSlowInInterpolator()
    }
}
