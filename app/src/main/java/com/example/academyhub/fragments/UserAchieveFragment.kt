// ════════════════════════════════════════════════════════════════════
// FILE 1:  com/example/academyhub/user/fragments/UserAchieveFragment.kt
// ════════════════════════════════════════════════════════════════════
package com.example.academyhub.user.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import com.example.academyhub.UserDashboardActivity
import com.example.academyhub.databinding.FragmentUserAchieveBinding
import com.example.academyhub.student.StudentExamTimetableActivity
import com.example.academyhub.student.StudentResultActivity
import com.example.academyhub.student.StudentSubjectsActivity

class UserAchieveFragment : Fragment() {

    private var _binding: FragmentUserAchieveBinding? = null
    private val binding get() = _binding!!
    private var animPlayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserAchieveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!animPlayed) { animPlayed = true; runEntranceAnim() }

        val act = activity as? UserDashboardActivity

        binding.rowExams.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> StudentExamTimetableActivity.start(requireContext(), s) }
            }
        }

        binding.rowResults.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> StudentResultActivity.start(requireContext(), s) }
                    ?: StudentResultActivity.start(requireContext())
            }
        }

        binding.rowReports.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> StudentSubjectsActivity.start(requireContext(), s) }
                    ?: StudentResultActivity.start(requireContext())}
        }
    }

    private fun runEntranceAnim() {
        val card = _binding?.root?.findViewById<View>(
            com.example.academyhub.R.id.rowExams
        )?.parent as? View ?: return

        card.alpha = 0f; card.translationY = 50f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply { duration = 450 },
                ObjectAnimator.ofFloat(card, "translationY", 50f, 0f).apply {
                    duration = 450; interpolator = OvershootInterpolator(1.0f)
                }
            ); start()
        }
    }

    private fun animPress(v: View, action: () -> Unit) {
        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { action() }.start()
        }.start()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
