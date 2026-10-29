package com.example.academyhub

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.academyhub.databinding.ActivityUserDashboardBinding
import com.example.academyhub.fragments.UserHomeFragment
import com.example.academyhub.models.Student
import com.example.academyhub.user.fragments.UserAchieveFragment
import com.example.academyhub.user.fragments.UserProfileFragment
import com.example.academyhub.user.fragments.UserVaultFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class UserDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDashboardBinding
    private val db    = FirebaseFirestore.getInstance()
    private val auth  = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    var currentStudent: Student? = null

    private var activeNavIndex = 0

    // ✅ Flag: onCreate માં role verified થઈ ગઈ હોય તો onResume skip DB call
    private var roleVerifiedOnCreate = false

    companion object {
        private const val KEY_STUDENT_DOC_ID = "studentDocId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ Auth check
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        // ✅ SharedPrefs માં role check
        val userRole = prefs.getString("userRole", "")
        when {
            userRole == "admin" -> { goToAdminDashboard(); return }
            userRole == "student" -> {
                // ✅ Role confirmed - proceed normally
                roleVerifiedOnCreate = true
                setupUI()
            }
            else -> {
                // ✅ Role empty/unknown → Firebase DB થી verify કરો
                verifyRoleFromDB(onVerified = { setupUI() })
            }
        }
    }

    // ── Setup UI ─────────────────────────────────────────────────────────────
    private fun setupUI() {
        setupHeader()
        setupBottomNav()
        loadStudentData()

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            switchFragment(UserHomeFragment(), 0)
        }

        binding.layoutHeader.alpha = 0f
        ObjectAnimator.ofFloat(binding.layoutHeader, "alpha", 0f, 1f).apply {
            duration = 400; interpolator = DecelerateInterpolator(); start()
        }
    }

    // ── Firebase DB Role Verify ───────────────────────────────────────────────
    private fun verifyRoleFromDB(onVerified: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid ?: run { goToLogin(); return }

        FirebaseDatabase.getInstance().getReference("Users").child(uid).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) { auth.signOut(); goToLogin(); return@addOnSuccessListener }

                val status = snap.child("status").value?.toString() ?: "active"
                if (status == "blocked") { auth.signOut(); goToLogin(); return@addOnSuccessListener }

                val role  = snap.child("role").value?.toString()  ?: "student"
                val name  = snap.child("name").value?.toString()  ?: ""
                val phone = snap.child("phone").value?.toString() ?: ""

                // ✅ SharedPrefs update
                prefs.edit().apply {
                    putString("userRole",  role)
                    putString("userName",  name)
                    putString("userPhone", phone)
                    putString("userId",    uid)
                    apply()
                }

                when (role) {
                    "admin"   -> goToAdminDashboard()
                    "student" -> onVerified?.invoke()
                    else      -> { auth.signOut(); goToLogin() }
                }
            }
            .addOnFailureListener { goToLogin() }
    }

    // ── Header ───────────────────────────────────────────────────────────────
    private fun setupHeader() {
        val name = prefs.getString("userName", "") ?: ""
        if (name.isNotEmpty()) binding.tvHeaderName.text = name.uppercase()
    }

    fun updateHeader(student: Student) {
        binding.tvHeaderName.text = student.name.uppercase()
    }

    // ── Bottom Nav ───────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        binding.navHome.setOnClickListener    { animPress(it); switchFragment(UserHomeFragment(), 0) }
        binding.navAchieve.setOnClickListener { animPress(it); switchFragment(UserAchieveFragment(), 1) }
        binding.navVault.setOnClickListener   { animPress(it); switchFragment(UserVaultFragment(), 2) }
        binding.navProfile.setOnClickListener { animPress(it); switchFragment(UserProfileFragment(), 3) }
    }

    fun switchFragment(fragment: Fragment, index: Int) {
        if (activeNavIndex == index &&
            supportFragmentManager.findFragmentById(R.id.fragmentContainer) != null &&
            supportFragmentManager.findFragmentById(R.id.fragmentContainer)!!::class == fragment::class
        ) return

        activeNavIndex = index
        setActiveNav(index)

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setActiveNav(index: Int) {
        val icons  = listOf(binding.navIcHome, binding.navIcAchieve, binding.navIcVault, binding.navIcProfile)
        val labels = listOf(binding.navTvHome, binding.navTvAchieve, binding.navTvVault, binding.navTvProfile)
        val teal   = getColor(R.color.primary_color)
        val gray   = android.graphics.Color.parseColor("#8AABA7")

        icons.forEachIndexed  { i, iv -> iv.setColorFilter(if (i == index) teal else gray) }
        labels.forEachIndexed { i, tv -> tv.setTextColor(if (i == index) teal else gray) }
    }

    private fun animPress(v: android.view.View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.88f, 1f).apply { duration = 200 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.88f, 1f).apply {
                    duration = 200; interpolator = OvershootInterpolator(2f)
                }
            ); start()
        }
    }

    // ── Data Loading ─────────────────────────────────────────────────────────
    private fun loadStudentData() {
        val studentDocId = prefs.getString(KEY_STUDENT_DOC_ID, "") ?: ""
        val userPhone    = prefs.getString("userPhone", "") ?: ""

        if (studentDocId.isNotEmpty()) {
            db.collection("Students").document(studentDocId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val s = doc.toObject(Student::class.java)
                        if (s != null) { s.id = doc.id; onStudentLoaded(s) }
                        else loadByPhone(userPhone)
                    } else loadByPhone(userPhone)
                }
                .addOnFailureListener { loadByPhone(userPhone) }
        } else {
            loadByPhone(userPhone)
        }
    }

    private fun loadByPhone(phone: String) {
        if (phone.isEmpty()) return
        val variants = if (phone.startsWith("+91")) listOf(phone, phone.removePrefix("+91"))
        else listOf(phone, "+91$phone")
        tryPhone(variants, 0)
    }

    private fun tryPhone(variants: List<String>, index: Int) {
        if (index >= variants.size) return
        db.collection("Students").whereEqualTo("mobile", variants[index]).limit(1).get()
            .addOnSuccessListener { q ->
                if (!q.isEmpty) {
                    val doc = q.documents[0]
                    val s   = doc.toObject(Student::class.java)
                    if (s != null) {
                        s.id = doc.id
                        prefs.edit().putString(KEY_STUDENT_DOC_ID, doc.id).apply()
                        onStudentLoaded(s)
                    } else tryPhone(variants, index + 1)
                } else tryPhone(variants, index + 1)
            }
            .addOnFailureListener { tryPhone(variants, index + 1) }
    }

    private fun onStudentLoaded(s: Student) {
        currentStudent = s
        updateHeader(s)
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is UserHomeFragment) current.bindStudent(s)
    }

    // ── Logout ───────────────────────────────────────────────────────────────
    fun showLogoutDialog() {
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

    private fun goToAdminDashboard() {
        val intent = Intent(this, AdminDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ── Theme toggle ──────────────────────────────────────────────────────────
    fun toggleTheme() {
        val dark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("isDark", !dark).apply()
    }

    override fun onResume() {
        super.onResume()

        // ✅ Auth check
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        // ✅ KEY FIX: Role DB થી verify કરો - SharedPrefs પર trust ન કરો
        val userRole = prefs.getString("userRole", "")
        when {
            userRole == "admin" -> { goToAdminDashboard(); return }
            userRole == "student" -> {
                // Normal resume
                setActiveNav(activeNavIndex)
                loadStudentData()
            }
            else -> {
                // ✅ Role empty → DB check
                verifyRoleFromDB(onVerified = {
                    setActiveNav(activeNavIndex)
                    loadStudentData()
                })
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (activeNavIndex != 0) {
            switchFragment(UserHomeFragment(), 0)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes") { _, _ -> finishAffinity() }
                .setNegativeButton("No", null)
                .show()
        }
    }
}