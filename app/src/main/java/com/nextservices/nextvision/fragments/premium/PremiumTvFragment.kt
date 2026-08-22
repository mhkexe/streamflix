package com.nextservices.nextvision.fragments.premium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.nextservices.nextvision.R
import com.nextservices.nextvision.databinding.FragmentPremiumTvBinding

class PremiumTvFragment : Fragment() {

    private var _binding: FragmentPremiumTvBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Glide.with(this)
            .load("file:///android_asset/premiumqr.png")
            .into(binding.ivPremiumQr)
        Glide.with(this)
            .load("file:///android_asset/nextviptv.webp")
            .into(binding.ivPremiumLogo)

        binding.premiumLeft.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.premium_slide_in_left)
        )
        binding.premiumRight.startAnimation(
            android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.premium_slide_in_right)
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}