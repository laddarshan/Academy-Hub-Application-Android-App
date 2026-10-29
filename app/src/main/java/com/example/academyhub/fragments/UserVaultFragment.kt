package com.example.academyhub.user.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import com.example.academyhub.databinding.FragmentUserVaultBinding

class UserVaultFragment : Fragment() {

    private var _binding: FragmentUserVaultBinding? = null
    private val binding get() = _binding!!
    private var animPlayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserVaultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!animPlayed) { animPlayed = true; runEntranceAnim() }
    }

    private fun runEntranceAnim() {
        val root = binding.root
        root.alpha = 0f; root.translationY = 40f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(root, "alpha", 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(root, "translationY", 40f, 0f).apply {
                    duration = 500; interpolator = OvershootInterpolator(1.0f)
                }
            ); start()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
