package com.example.academyhub.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.academyhub.databinding.FragmentDashboardBinding
import com.example.academyhub.students.StudentStatsActivity
import com.example.academyhub.fees.FeesStatsActivity
import com.example.academyhub.course.ManageCoursesActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var animationPlayed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setWelcomeGreeting()   // ← Fixed: fetches name from DB if missing
        if (!animationPlayed) { animationPlayed = true; runEntranceAnimation() }
        loadStatistics()
        setupCardClicks()
    }

    // ── Card click listeners ──────────────────────────────────────────────────
    private fun setupCardClicks() {
        binding.cardStudents.setOnClickListener {
            scaleClick(it) { startActivity(Intent(requireContext(), StudentStatsActivity::class.java)) }
        }
        binding.cardCourses.setOnClickListener {
            scaleClick(it) { startActivity(Intent(requireContext(), ManageCoursesActivity::class.java)) }
        }
        binding.cardFees.setOnClickListener {
            scaleClick(it) { startActivity(Intent(requireContext(), FeesStatsActivity::class.java)) }
        }
    }

    private fun scaleClick(v: View, action: () -> Unit) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(100).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
        v.postDelayed(action, 160)
    }

    // ── Firestore statistics ──────────────────────────────────────────────────
    private fun loadStatistics() {
        if (_binding == null) return

        db.collection("Students").get()
            .addOnSuccessListener { snap ->
                if (_binding == null) return@addOnSuccessListener
                animateCount(binding.tvTotalStudents, snap.size())
            }
            .addOnFailureListener { if (_binding != null) binding.tvTotalStudents.text = "0" }

        db.collection("Courses").get()
            .addOnSuccessListener { snap ->
                if (_binding == null) return@addOnSuccessListener
                val active = snap.documents.count { doc -> doc.getBoolean("active") ?: true }
                animateCount(binding.tvTotalCourses, active)
            }
            .addOnFailureListener { if (_binding != null) binding.tvTotalCourses.text = "0" }

        db.collection("Students").get()
            .addOnSuccessListener { snap ->
                if (_binding == null) return@addOnSuccessListener
                var totalPending = 0.0
                for (doc in snap) {
                    val total  = doc.getDouble("totalFees") ?: 0.0
                    val paid   = doc.getDouble("paidFees")  ?: 0.0
                    val status = doc.getString("feesStatus") ?: "pending"
                    if (total > 0 && status != "paid") totalPending += (total - paid).coerceAtLeast(0.0)
                }
                animateFeesCount(binding.tvPendingFees, totalPending.toLong())
            }
            .addOnFailureListener { if (_binding != null) binding.tvPendingFees.text = "₹0" }

        binding.layoutNoActivity.visibility = View.VISIBLE
    }

    // ── Welcome greeting + name ───────────────────────────────────────────────
    /**
     * Shows greeting based on time-of-day.
     * Name priority:
     *   1. SharedPreferences "userName" (set during login)
     *   2. If empty → fetch fresh from Realtime Database "Users/{uid}/name"
     *      and save it back to SharedPreferences for future use
     */
    private fun setWelcomeGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvWelcomeGreet.text = when {
            hour < 12 -> "Good Morning,"
            hour < 17 -> "Good Afternoon,"
            else      -> "Good Evening,"
        }

        val prefs     = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("userName", "") ?: ""

        if (savedName.isNotEmpty()) {
            // Name already available — show immediately
            binding.tvWelcomeName.text = savedName
        } else {
            // Name missing (after logout + re-login) → fetch from Realtime DB
            binding.tvWelcomeName.text = "..."   // placeholder while fetching

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(uid)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (_binding == null) return@addOnSuccessListener

                        // Try common Realtime DB field names
                        val fetchedName =
                            snap.child("name").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: snap.child("Name").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: snap.child("displayName").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: "Admin"

                        // Save back so next time we don't need to fetch
                        prefs.edit().putString("userName", fetchedName).apply()

                        binding.tvWelcomeName.text = fetchedName
                    }
                    .addOnFailureListener {
                        if (_binding != null) binding.tvWelcomeName.text = "Admin"
                    }
            } else {
                binding.tvWelcomeName.text = "Admin"
            }
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────
    private fun animateCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 1200; startDelay = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener { if (_binding != null) tv.text = "${it.animatedValue}" }
            start()
        }
    }

    private fun animateFeesCount(tv: TextView, target: Long) {
        ValueAnimator.ofInt(0, target.toInt()).apply {
            duration = 1300; startDelay = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { if (_binding != null) tv.text = "₹${it.animatedValue}" }
            start()
        }
    }

    private fun runEntranceAnimation() {
        binding.cardWelcome.apply { alpha = 0f; translationY = 60f }
        binding.statsRow1.apply   { alpha = 0f; translationY = 80f }
        binding.statsRow2.apply   { alpha = 0f; translationY = 80f }

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.cardWelcome, "alpha", 0f, 1f).apply { duration = 480 },
                ObjectAnimator.ofFloat(binding.cardWelcome, "translationY", 60f, 0f).apply {
                    duration = 480; interpolator = DecelerateInterpolator()
                }
            ); start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.statsRow1, "alpha", 0f, 1f).apply { duration = 550; startDelay = 200 },
                ObjectAnimator.ofFloat(binding.statsRow1, "translationY", 80f, 0f).apply {
                    duration = 550; startDelay = 200; interpolator = OvershootInterpolator(1.1f)
                }
            ); start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.statsRow2, "alpha", 0f, 1f).apply { duration = 550; startDelay = 370 },
                ObjectAnimator.ofFloat(binding.statsRow2, "translationY", 80f, 0f).apply {
                    duration = 550; startDelay = 370; interpolator = OvershootInterpolator(1.1f)
                }
            ); start()
        }
    }

    override fun onResume() {
        super.onResume()
        loadStatistics()
        setWelcomeGreeting()   // re-check name on every resume too
        _binding?.root?.let {
            ObjectAnimator.ofFloat(it, "alpha", 0.85f, 1f).apply {
                duration = 250; interpolator = DecelerateInterpolator(); start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.root?.let {
            ObjectAnimator.ofFloat(it, "alpha", 1f, 0.85f).apply { duration = 150; start() }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}