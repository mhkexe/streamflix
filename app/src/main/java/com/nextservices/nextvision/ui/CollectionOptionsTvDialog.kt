package com.nextservices.nextvision.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.navigation.fragment.NavHostFragment
import com.nextservices.nextvision.R
import com.nextservices.nextvision.databinding.DialogShowOptionsTvBinding
import com.nextservices.nextvision.utils.getCurrentFragment
import com.nextservices.nextvision.utils.toActivity

class CollectionOptionsTvDialog(
    context: Context,
    private val collectionId: Int,
    private val title: String,
    private val poster: String?,
    private val backdrop: String?,
) : Dialog(context) {

    private val binding = DialogShowOptionsTvBinding.inflate(LayoutInflater.from(context))

    override fun show() {
        if (context.toActivity()?.isFinishing == true || context.toActivity()?.isDestroyed == true) return
        runCatching { super.show() }
            .onFailure { error ->
                if (error is WindowManager.BadTokenException || error is IllegalStateException) return@onFailure
                throw error
            }
    }

    override fun onStart() {
        super.onStart()
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes?.also { params -> params.gravity = Gravity.END }
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.35).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }

    init {
        setContentView(binding.root)
        binding.tvOptionsShowTitle.text = title
        binding.tvShowSubtitle.visibility = View.GONE
        binding.btnOptionPlay.visibility = View.GONE
        binding.btnOptionViewDetails.visibility = View.GONE
        binding.btnOptionShowFavorite.visibility = View.GONE
        binding.btnOptionShowWatched.visibility = View.GONE
        binding.btnOptionEpisodeMarkAllPreviousWatched.visibility = View.GONE
        binding.btnOptionProgramClear.visibility = View.GONE

        binding.btnOptionEpisodeOpenTvShow.apply {
            text = context.getString(R.string.collection_open)
            setOnClickListener {
                val fragment = context.toActivity()?.getCurrentFragment()
                if (fragment != null) {
                    NavHostFragment.findNavController(fragment).navigate(
                        R.id.collection,
                        android.os.Bundle().apply { putInt("id", collectionId) },
                    )
                }
                hide()
            }
            requestFocus()
        }
        binding.btnOptionCancel.setOnClickListener { hide() }

    }
}