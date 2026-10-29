package com.example.academyhub

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        // ✅ Global activity lifecycle monitor
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityResumed(activity: Activity) {
                // ✅ UserDashboardActivity resume થાય ત્યારે role verify કરો
                if (activity is UserDashboardActivity) {
                    val prefs = activity.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    val role  = prefs.getString("userRole", "") ?: ""

                    // Role "admin" હોય → AdminDashboard પર જાઓ
                    if (role == "admin") {
                        val intent = Intent(activity, AdminDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        activity.startActivity(intent)
                        activity.finish()
                        return
                    }

                    // Role empty હોય → Firebase DB થી check કરો
                    if (role.isEmpty()) {
                        val user = FirebaseAuth.getInstance().currentUser
                        if (user == null) {
                            val intent = Intent(activity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            activity.startActivity(intent)
                            activity.finish()
                            return
                        }

                        FirebaseDatabase.getInstance()
                            .getReference("Users")
                            .child(user.uid)
                            .get()
                            .addOnSuccessListener { snap ->
                                val dbRole = snap.child("role").value?.toString() ?: "student"

                                // SharedPrefs update
                                prefs.edit().putString("userRole", dbRole).apply()

                                if (dbRole == "admin") {
                                    val intent = Intent(activity, AdminDashboardActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    activity.startActivity(intent)
                                    activity.finish()
                                }
                            }
                    }
                }
            }

            // ── બાકી callbacks (empty) ──────────────────────────────────────
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}