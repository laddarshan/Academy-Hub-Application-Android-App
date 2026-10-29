package com.example.academyhub.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.academyhub.databinding.FragmentManagementBinding
import com.example.academyhub.exam.ExamRoleActivity
import com.example.academyhub.fees.FeesDashboardActivity
import com.example.academyhub.reports.ReportsActivity
import com.example.academyhub.result.ResultRoleActivity
import com.example.academyhub.student.StudentRoleActivity
import com.example.academyhub.subject.SubjectRoleActivity
import com.example.academyhub.timetable.TimetableRoleActivity

class ManagementFragment : Fragment() {

    private var _binding: FragmentManagementBinding? = null
    private val binding get() = _binding!!

    private var animationPlayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!animationPlayed) {
            animationPlayed = true
            runEntranceAnimation()
        }

        setupClickListeners()
    }

    // ══ STAGGER SLIDE-IN: cards come in one after another ══
    private fun runEntranceAnimation() {
        val cards = listOf(
            binding.cardStudentManagement,
            binding.cardTeacherManagement,
            binding.cardCourseManagement
        )

        // Hide + translate all cards initially
        cards.forEach { card ->
            card.alpha = 0f
            card.translationX = -80f
        }

        // Stagger each card with 100ms offset
        cards.forEachIndexed { i, card ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
                        duration   = 450
                        startDelay = (i * 110).toLong()
                    },
                    ObjectAnimator.ofFloat(card, "translationX", -80f, 0f).apply {
                        duration   = 450
                        startDelay = (i * 110).toLong()
                        interpolator = OvershootInterpolator(0.8f)
                    }
                )
                start()
            }
        }

        // Small cards (Fees/Exams/Reports) fade up after main cards
        binding.cardFees.apply   { alpha = 0f; translationY = 40f }
        binding.cardExams.apply  { alpha = 0f; translationY = 40f }
        binding.cardReports.apply { alpha = 0f; translationY = 40f }

        listOf(binding.cardFees, binding.cardExams, binding.cardReports).forEachIndexed { i, card ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(card, "alpha", 0f, 1f).apply {
                        duration   = 380
                        startDelay = (400 + i * 80).toLong()
                    },
                    ObjectAnimator.ofFloat(card, "translationY", 40f, 0f).apply {
                        duration   = 380
                        startDelay = (400 + i * 80).toLong()
                        interpolator = DecelerateInterpolator()
                    }
                )
                start()
            }
        }
    }

    // ══ TAP ANIMATION: card slightly shrinks and springs back ══
    private fun animateTap(view: View, onDone: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.96f, 1f).apply { duration = 200 },
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.96f, 1f).apply { duration = 200 }
            )
            start()
        }
        view.postDelayed(onDone, 120)
    }

    private fun setupClickListeners() {

        binding.cardStudentManagement.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, StudentRoleActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardTeacherManagement.setOnClickListener { v ->
            animateTap(v) {
                Toast.makeText(context, "Teacher Management - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardCourseManagement.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, SubjectRoleActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardTimetable.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, TimetableRoleActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardResult.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, ResultRoleActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardFees.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, FeesDashboardActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardExams.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, ExamRoleActivity::class.java)
                startActivity(intent)
            }
        }

        binding.cardReports.setOnClickListener { v ->
            animateTap(v) {
                val intent = Intent(activity, ReportsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.root?.let { root ->
            ObjectAnimator.ofFloat(root, "alpha", 1f, 0.85f).apply { duration = 150; start() }
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.root?.let { root ->
            ObjectAnimator.ofFloat(root, "alpha", 0.85f, 1f).apply {
                duration = 250; interpolator = DecelerateInterpolator(); start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
