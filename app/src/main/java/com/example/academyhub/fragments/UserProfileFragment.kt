package com.example.academyhub.user.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.academyhub.LoginActivity
import com.example.academyhub.UserDashboardActivity
import com.example.academyhub.databinding.FragmentUserProfileBinding
import com.example.academyhub.student.StudentFeesActivity
import com.example.academyhub.student.StudentProfileActivity
import com.example.academyhub.student.StudentTimetableActivity
import com.google.firebase.auth.FirebaseAuth

class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private var animPlayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!animPlayed) { animPlayed = true; runEntranceAnim() }

        val act = activity as? UserDashboardActivity

        // Schedule
        binding.rowSchedule.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> StudentTimetableActivity.start(requireContext(), s) }
                    ?: StudentTimetableActivity.start(requireContext())
            }
        }

        // Fees
        binding.rowFees.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> StudentFeesActivity.start(requireContext(), s) }
                    ?: StudentFeesActivity.start(requireContext())
            }
        }

        // Profile
        binding.rowProfile.setOnClickListener { v ->
            animPress(v) { StudentProfileActivity.start(requireContext()) }
        }

        // Logout
        binding.rowLogout.setOnClickListener { v ->
            animPress(v) { showLogoutDialog() }
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(requireContext(), gso).signOut()
            com.facebook.login.LoginManager.getInstance().logOut()
        } catch (_: Exception) {}

        requireContext()
            .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .edit().clear().apply()

        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun runEntranceAnim() {
        val card = _binding?.root?.findViewById<View>(
            com.example.academyhub.R.id.rowSchedule
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
