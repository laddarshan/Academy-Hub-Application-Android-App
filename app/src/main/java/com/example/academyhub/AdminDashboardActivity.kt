package com.example.academyhub

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.academyhub.announcements.AnnouncementRoleActivity
import com.example.academyhub.attendance.AttendanceRoleActivity
import com.example.academyhub.batch.ManageBatchesActivity
import com.example.academyhub.course.ManageCoursesActivity
import com.example.academyhub.databinding.ActivityAdminDashboardBinding
import com.example.academyhub.exam.ExamRoleActivity
import com.example.academyhub.fees.FeesDashboardActivity
import com.example.academyhub.fragments.AnnouncementsFragment
import com.example.academyhub.fragments.DashboardFragment
import com.example.academyhub.fragments.ManagementFragment
import com.example.academyhub.reports.ReportsActivity
import com.example.academyhub.result.ResultRoleActivity
import com.example.academyhub.session.ManageSessionsActivity
import com.example.academyhub.student.StudentRoleActivity
import com.example.academyhub.subject.SubjectRoleActivity
import com.example.academyhub.teacher.TeacherListActivity
import com.example.academyhub.timetable.TimetableRoleActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class AdminDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var auth: FirebaseAuth

    private var isDrawerOpen        = false
    private var isFabExpanded       = false
    private var isAcademicGroupOpen = false
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) { goToLogin(); return }

        val userRole = prefs.getString("userRole", "")
        if (userRole == "student") { goToUserDashboard(); return }

        setupAdminProfile()
        setupViewPagerWithTabs()
        setupFAB()
        startHeaderAnimation()
        setupDrawer()
    }

    // ══ PROFILE ══════════════════════════════════════════════════════════════
    private fun setupAdminProfile() {
        val prefs     = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("userName", "") ?: ""

        if (savedName.isNotEmpty()) {
            applyNameToUI(savedName)
        } else {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                FirebaseDatabase.getInstance()
                    .getReference("Users").child(uid).get()
                    .addOnSuccessListener { snap ->
                        val fetchedName =
                            snap.child("name").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: snap.child("Name").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: snap.child("displayName").value?.toString()?.takeIf { it.isNotEmpty() }
                                ?: "Admin"
                        prefs.edit()
                            .putString("userName", fetchedName)
                            .putString("userRole", "admin")
                            .putString("userId", uid)
                            .apply()
                        applyNameToUI(fetchedName)
                    }
                    .addOnFailureListener { applyNameToUI("Admin") }
            } else {
                applyNameToUI("Admin")
            }
        }
    }

    private fun applyNameToUI(name: String) {
        binding.tvAdminName.text  = name.uppercase()
        binding.tvDrawerName.text = name
    }

    // ══ HEADER ANIMATION ═════════════════════════════════════════════════════
    private fun startHeaderAnimation() {
        binding.layoutDashboardContent.alpha = 0f
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "alpha", 0f, 1f).apply {
            duration = 400; interpolator = DecelerateInterpolator(); start()
        }
    }

    // ══ DRAWER ═══════════════════════════════════════════════════════════════
    private fun setupDrawer() {
        val screenWidth = resources.displayMetrics.widthPixels
        val panelWidth  = (screenWidth * 0.62f).toInt()

        val lp = binding.layoutDrawerPanel.layoutParams
        lp.width = panelWidth
        binding.layoutDrawerPanel.layoutParams = lp
        binding.layoutDrawerPanel.translationX = panelWidth.toFloat()

        binding.layoutAdminProfile.setOnClickListener     { openDrawer() }
        binding.layoutAdminProfile.setOnLongClickListener { showLogoutDialog(); true }
        binding.viewScrim.setOnClickListener              { closeDrawer() }
        binding.menuAcademicSetup.setOnClickListener      { toggleAcademicGroup() }

        setupMenuClicks()
    }

    private fun openDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true

        val panelWidth = (resources.displayMetrics.widthPixels * 0.62f).toInt()

        binding.viewScrim.visibility         = View.VISIBLE
        binding.viewScrim.alpha              = 0f
        binding.layoutDrawerPanel.visibility = View.VISIBLE

        ObjectAnimator.ofFloat(binding.viewScrim, "alpha", 0f, 1f).apply { duration = 280; start() }
        ObjectAnimator.ofFloat(binding.layoutDrawerPanel, "translationX", panelWidth.toFloat(), 0f).apply {
            duration = 300; interpolator = DecelerateInterpolator(1.2f); start()
        }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "alpha",  1f, 0.5f).apply { duration = 280; start() }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "scaleX", 1f, 0.97f).apply { duration = 280; start() }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "scaleY", 1f, 0.97f).apply { duration = 280; start() }

        staggerMenuItems()
    }

    private fun closeDrawer() {
        if (!isDrawerOpen) return
        isDrawerOpen = false

        val panelWidth = (resources.displayMetrics.widthPixels * 0.62f).toInt()

        ObjectAnimator.ofFloat(binding.layoutDrawerPanel, "translationX", 0f, panelWidth.toFloat()).apply {
            duration = 260; interpolator = DecelerateInterpolator(); start()
        }
        ObjectAnimator.ofFloat(binding.viewScrim, "alpha", 1f, 0f).apply { duration = 240; start() }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "alpha",  0.5f, 1f).apply { duration = 260; start() }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "scaleX", 0.97f, 1f).apply { duration = 260; start() }
        ObjectAnimator.ofFloat(binding.layoutDashboardContent, "scaleY", 0.97f, 1f).apply { duration = 260; start() }

        binding.layoutDrawerPanel.postDelayed({
            binding.viewScrim.visibility         = View.GONE
            binding.layoutDrawerPanel.visibility = View.GONE
        }, 280)
    }

    private fun staggerMenuItems() {
        val menu = binding.layoutDrawerMenu
        for (i in 0 until menu.childCount) {
            val child = menu.getChildAt(i)
            child.alpha = 0f
            child.translationY = 25f
            ObjectAnimator.ofFloat(child, "alpha", 0f, 1f).apply {
                duration = 250; startDelay = (i * 40L); start()
            }
            ObjectAnimator.ofFloat(child, "translationY", 25f, 0f).apply {
                duration = 250; startDelay = (i * 40L)
                interpolator = OvershootInterpolator(0.6f); start()
            }
        }
    }

    // ══ ACADEMIC SETUP TOGGLE — FIXED ════════════════════════════════════════
    private fun toggleAcademicGroup() {
        isAcademicGroupOpen = !isAcademicGroupOpen

        // ✅ FIX: layoutAcademicGroup show/hide karvo — menuAcademicSetup nahi
        if (isAcademicGroupOpen) {
            binding.layoutAcademicGroup.visibility = View.VISIBLE
            // Animate chevron arrow down
            ObjectAnimator.ofFloat(binding.iconAcademicChevron, "rotation", 90f, 270f)
                .apply { duration = 200; start() }
        } else {
            binding.layoutAcademicGroup.visibility = View.GONE
            // Animate chevron arrow right
            ObjectAnimator.ofFloat(binding.iconAcademicChevron, "rotation", 270f, 90f)
                .apply { duration = 200; start() }
        }
    }

    // ══ MENU NAVIGATION ══════════════════════════════════════════════════════
    private fun navigateWithFeedback(row: View, action: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, "scaleX", 1f, 0.95f, 1f).apply { duration = 150 },
                ObjectAnimator.ofFloat(row, "scaleY", 1f, 0.95f, 1f).apply { duration = 150 }
            )
            start()
        }
        closeDrawer()
        action()
    }

    private fun setupMenuClicks() {
        binding.menuStudents.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, StudentRoleActivity::class.java)) }
        }
        binding.menuTeachers.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, TeacherListActivity::class.java)) }
        }
        binding.menuAttendance.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, AttendanceRoleActivity::class.java)) }
        }
        binding.menuExams.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ExamRoleActivity::class.java)) }
        }
        binding.menuResults.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ResultRoleActivity::class.java)) }
        }
        binding.menuFees.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, FeesDashboardActivity::class.java)) }
        }
        binding.menuTimetable.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, TimetableRoleActivity::class.java)) }
        }
        binding.menuAnnouncements.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, AnnouncementRoleActivity::class.java)) }
        }
        binding.menuReports.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ReportsActivity::class.java)) }
        }
        binding.menuSession.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ManageSessionsActivity::class.java)) }
        }
        binding.menuCourse.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ManageCoursesActivity::class.java)) }
        }
        binding.menuBatch.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, ManageBatchesActivity::class.java)) }
        }
        binding.menuSubject.setOnClickListener {
            navigateWithFeedback(it) { startActivity(Intent(this, SubjectRoleActivity::class.java)) }
        }
        binding.menuSettings.setOnClickListener {
            navigateWithFeedback(it) {
                Toast.makeText(this, "Settings - Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }
        binding.menuLogout.setOnClickListener {
            closeDrawer()
            binding.menuLogout.postDelayed({ showLogoutDialog() }, 290)
        }
    }

    // ══ STATS ANIMATION (called from DashboardFragment) ══════════════════════
    fun animateStatsCards(statsRow1: View, statsRow2: View, welcomeCard: View) {
        welcomeCard.alpha = 0f; welcomeCard.translationY = 40f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(welcomeCard, "alpha", 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(welcomeCard, "translationY", 40f, 0f).apply {
                    duration = 500; interpolator = DecelerateInterpolator()
                }
            ); start()
        }
        statsRow1.translationY = 80f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(statsRow1, "alpha", 0f, 1f).apply { duration = 550; startDelay = 200 },
                ObjectAnimator.ofFloat(statsRow1, "translationY", 80f, 0f).apply {
                    duration = 550; startDelay = 200; interpolator = OvershootInterpolator(0.8f)
                }
            ); start()
        }
        statsRow2.translationY = 80f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(statsRow2, "alpha", 0f, 1f).apply { duration = 550; startDelay = 380 },
                ObjectAnimator.ofFloat(statsRow2, "translationY", 80f, 0f).apply {
                    duration = 550; startDelay = 380; interpolator = OvershootInterpolator(0.8f)
                }
            ); start()
        }
    }

    fun animateCountUp(textView: android.widget.TextView, targetValue: Int, prefix: String = "", suffix: String = "") {
        ValueAnimator.ofInt(0, targetValue).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
            addUpdateListener { textView.text = "$prefix${it.animatedValue}$suffix" }
            start()
        }
    }

    // ══ FAB ══════════════════════════════════════════════════════════════════
    private fun setupFAB() {
        binding.fabAdd.setOnClickListener {
            if (isFabExpanded) collapseFab() else expandFab()
        }
    }

    private fun expandFab() {
        isFabExpanded = true
        ObjectAnimator.ofFloat(binding.fabAdd, "rotation", 0f, 45f).apply {
            duration = 300; interpolator = OvershootInterpolator(); start()
        }
        Toast.makeText(this, "Quick Add — coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun collapseFab() {
        isFabExpanded = false
        ObjectAnimator.ofFloat(binding.fabAdd, "rotation", 45f, 0f).apply {
            duration = 300; interpolator = DecelerateInterpolator(); start()
        }
    }

    private fun setupViewPagerWithTabs() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = when (pos) { 0 -> "DASHBOARD"; 1 -> "MANAGEMENT"; 2 -> "ANNOUNCEMENTS"; else -> "" }
        }.attach()
    }

    // ══ LOGOUT ═══════════════════════════════════════════════════════════════
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        auth.signOut()
        prefs.edit().clear().apply()
        goToLogin()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToUserDashboard() {
        val intent = Intent(this, UserDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ══ onResume ═════════════════════════════════════════════════════════════
    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) { goToLogin(); return }
        val userRole = prefs.getString("userRole", "")
        if (userRole == "student") { goToUserDashboard(); return }
    }

    // ══ BACK PRESS ═══════════════════════════════════════════════════════════
    override fun onBackPressed() {
        super.onBackPressed()
        when {
            isDrawerOpen  -> closeDrawer()
            isFabExpanded -> collapseFab()
            else -> AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes") { _, _ -> finishAffinity() }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount() = 3
        override fun createFragment(position: Int): Fragment = when (position) {
            0    -> DashboardFragment()
            1    -> ManagementFragment()
            2    -> AnnouncementsFragment()
            else -> DashboardFragment()
        }
    }
}