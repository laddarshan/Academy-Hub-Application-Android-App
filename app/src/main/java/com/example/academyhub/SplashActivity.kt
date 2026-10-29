package com.example.academyhub

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivitySplashBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        startSplashAnimations()
        handler.postDelayed({ checkAndNavigate() }, 3000L)
    }

    // ── Animations ──────────────────────────────────────────────────────────
    private fun startSplashAnimations() {
        val logoScaleX = ObjectAnimator.ofFloat(binding.frameLogo, "scaleX", 0.6f, 1f).apply {
            duration = 1000; interpolator = DecelerateInterpolator()
        }
        val logoScaleY = ObjectAnimator.ofFloat(binding.frameLogo, "scaleY", 0.6f, 1f).apply {
            duration = 1000; interpolator = DecelerateInterpolator()
        }
        binding.layoutCenter.translationY = 100f
        val centerFade  = ObjectAnimator.ofFloat(binding.layoutCenter, "alpha", 0f, 1f).apply {
            duration = 1100; interpolator = DecelerateInterpolator()
        }
        val centerSlide = ObjectAnimator.ofFloat(binding.layoutCenter, "translationY", 100f, 0f).apply {
            duration = 1100; interpolator = DecelerateInterpolator()
        }
        val bottomFade  = ObjectAnimator.ofFloat(binding.layoutBottom, "alpha", 0f, 1f).apply {
            duration = 900; startDelay = 800
        }
        AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, centerFade, centerSlide, bottomFade)
            start()
        }
        handler.postDelayed({ animateDots() }, 1200)
    }

    private fun animateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        var index = 0
        val dotRunner = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                dots.forEach {
                    ObjectAnimator.ofFloat(it, "alpha", 0.25f).apply { duration = 250; start() }
                }
                ObjectAnimator.ofFloat(dots[index], "alpha", 1f).apply { duration = 250; start() }
                ObjectAnimator.ofFloat(dots[index], "scaleX", 1f, 1.5f, 1f).apply { duration = 500; start() }
                ObjectAnimator.ofFloat(dots[index], "scaleY", 1f, 1.5f, 1f).apply { duration = 500; start() }
                index = (index + 1) % dots.size
                handler.postDelayed(this, 500)
            }
        }
        handler.post(dotRunner)
    }

    // ── Firebase Check ──────────────────────────────────────────────────────
    private fun checkAndNavigate() {
        if (navigated) return
        navigated = true

        val user = auth.currentUser
        if (user == null) { goLogin(); return }

        val db  = FirebaseDatabase.getInstance().getReference("Users")
        val uid = user.uid
        val phone = user.phoneNumber?.removePrefix("+91") ?: ""

        // ✅ Step 1: UID node check
        db.child(uid).get()
            .addOnSuccessListener { uidSnap ->

                val roleFromUid = uidSnap.child("role").value?.toString() ?: ""

                if (roleFromUid == "admin" || roleFromUid == "student") {
                    // ✅ UID node માં role સ્પષ્ટ છે — directly navigate
                    val status = uidSnap.child("status").value?.toString() ?: "active"
                    if (status == "blocked") { auth.signOut(); goLogin(); return@addOnSuccessListener }

                    val name  = uidSnap.child("name").value?.toString()  ?: ""
                    val ph    = uidSnap.child("phone").value?.toString() ?: phone
                    savePrefsAndNavigate(roleFromUid, name, ph, uid)

                } else {
                    // ✅ Step 2: UID node માં role નથી — phone થી search કરો
                    // (Admin record push-key હેઠળ phone field સાથે store છે)
                    checkRoleByPhone(db, uid, phone)
                }
            }
            .addOnFailureListener {
                // Network error — phone fallback
                checkRoleByPhone(db, uid, phone)
            }
    }

    // ── Phone based role check (Admin push-key records માટે) ────────────────
    private fun checkRoleByPhone(
        db: com.google.firebase.database.DatabaseReference,
        uid: String,
        phone: String
    ) {
        if (phone.isEmpty()) { goLogin(); return }

        // Phone without +91 try
        db.orderByChild("phone").equalTo(phone).limitToFirst(1).get()
            .addOnSuccessListener { snap1 ->
                val node = snap1.children.firstOrNull()
                val role = node?.child("role")?.value?.toString() ?: ""

                if (role.isNotEmpty()) {
                    val status = node?.child("status")?.value?.toString() ?: "active"
                    if (status == "blocked") { auth.signOut(); goLogin(); return@addOnSuccessListener }

                    val name = node?.child("name")?.value?.toString() ?: ""

                    // ✅ UID node માં role + name save કરો (future UID lookups માટે)
                    db.child(uid).updateChildren(mapOf("role" to role, "name" to name, "phone" to phone))

                    savePrefsAndNavigate(role, name, phone, uid)
                } else {
                    // +91 format try
                    db.orderByChild("phone").equalTo("+91$phone").limitToFirst(1).get()
                        .addOnSuccessListener { snap2 ->
                            val node2 = snap2.children.firstOrNull()
                            val role2 = node2?.child("role")?.value?.toString() ?: ""

                            if (role2.isNotEmpty()) {
                                val status = node2?.child("status")?.value?.toString() ?: "active"
                                if (status == "blocked") { auth.signOut(); goLogin(); return@addOnSuccessListener }

                                val name2 = node2?.child("name")?.value?.toString() ?: ""
                                db.child(uid).updateChildren(mapOf("role" to role2, "name" to name2, "phone" to "+91$phone"))
                                savePrefsAndNavigate(role2, name2, phone, uid)
                            } else {
                                // No record found
                                auth.signOut(); goLogin()
                            }
                        }
                        .addOnFailureListener { auth.signOut(); goLogin() }
                }
            }
            .addOnFailureListener { auth.signOut(); goLogin() }
    }

    // ── SharedPrefs save + Navigate ─────────────────────────────────────────
    private fun savePrefsAndNavigate(role: String, name: String, phone: String, uid: String) {
        getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit()
            .putString("userRole",  role)
            .putString("userName",  name)
            .putString("userPhone", phone)
            .putString("userId",    uid)
            .apply()

        fadeOutAndGo {
            val intent = when (role) {
                "admin"   -> Intent(this, AdminDashboardActivity::class.java)
                "student" -> Intent(this, UserDashboardActivity::class.java)
                else      -> { auth.signOut(); goLogin(); return@fadeOutAndGo }
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun fadeOutAndGo(onEnd: () -> Unit) {
        ObjectAnimator.ofFloat(binding.root, "alpha", 1f, 0f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd() }
            })
            start()
        }
    }

    private fun goLogin() {
        fadeOutAndGo {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}