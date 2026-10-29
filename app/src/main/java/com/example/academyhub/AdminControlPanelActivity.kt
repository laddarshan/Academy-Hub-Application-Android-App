package com.example.academyhub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.announcements.AnnouncementRoleActivity
import com.example.academyhub.attendance.AttendanceRoleActivity
import com.example.academyhub.batch.AddBatchActivity
import com.example.academyhub.batch.ManageBatchesActivity
import com.example.academyhub.course.AddCourseActivity
import com.example.academyhub.course.ManageCoursesActivity
import com.example.academyhub.databinding.ActivityAdminControlPanelBinding
import com.example.academyhub.exam.ExamRoleActivity
import com.example.academyhub.fees.FeesDashboardActivity
import com.example.academyhub.fees.FeesRoleActivity
import com.example.academyhub.models.FeesDashboard
import com.example.academyhub.reports.ReportsActivity
import com.example.academyhub.result.ResultRoleActivity
import com.example.academyhub.session.AddSessionActivity
import com.example.academyhub.session.ManageSessionsActivity
import com.example.academyhub.student.StudentRoleActivity
import com.example.academyhub.subject.SubjectRoleActivity

import com.example.academyhub.teacher.TeacherListActivity
import com.example.academyhub.timetable.TimetableRoleActivity

class AdminControlPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminControlPanelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminControlPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load admin data
        loadAdminData()

        // Setup click listeners
        setupClickListeners()
    }

    private fun loadAdminData() {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val adminName = prefs.getString("userName", "Admin")
        val adminEmail = prefs.getString("userEmail", "")

        binding.tvAdminName.text = adminName?.uppercase() ?: "ADMIN"
        binding.tvAdminEmail.text = adminEmail

        // TODO: Load profile image from Firestore Base64
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        // Student Management
        binding.cardStudentManagement.setOnClickListener {
            val intent = Intent(this, StudentRoleActivity::class.java)
            startActivity(intent)
        }

        // Teacher Management
        binding.cardTeacherManagement.setOnClickListener {
            val intent = Intent(this, TeacherListActivity::class.java)
            startActivity(intent)
        }

        binding.cardsessionManagement.setOnClickListener {
            val intent = Intent(this, ManageSessionsActivity::class.java)
            startActivity(intent)
        }

        // Course Management
        binding.cardCourseManagement.setOnClickListener {
           val intent = Intent(this, ManageCoursesActivity::class.java)
            startActivity(intent)
        }

         //Batch Management
        binding.cardBatchManagement.setOnClickListener {
            val intent = Intent(this, ManageBatchesActivity::class.java)
            startActivity(intent)
        }

        //Subject Management
        binding.cardSubjectManagement.setOnClickListener {
            val intent = Intent(this, SubjectRoleActivity::class.java)
            startActivity(intent)
        }

        // Timetable Management
        binding.cardTimetableManagement.setOnClickListener {
            val intent = Intent(this, TimetableRoleActivity::class.java)
            startActivity(intent)
        }

        // Attendance Management
        binding.cardAttendanceManagement.setOnClickListener {
            val intent = Intent(this, AttendanceRoleActivity::class.java)
            startActivity(intent)
        }

        // Exam Management
        binding.cardExamManagement.setOnClickListener {
            val intent = Intent(this, ExamRoleActivity::class.java)
            startActivity(intent)
        }

        // Result Management
        binding.cardResultManagement.setOnClickListener {
            val intent = Intent(this, ResultRoleActivity::class.java)
            startActivity(intent)
        }

        // Fees Management
        binding.cardFeesManagement.setOnClickListener {
            val intent = Intent(this, FeesDashboardActivity::class.java)
            startActivity(intent)
        }

        // Notifications Management
        binding.cardNotificationsManagement.setOnClickListener {
            val intent = Intent(this, AnnouncementRoleActivity::class.java)
            startActivity(intent)
        }

        // Reports
        binding.cardReports.setOnClickListener {
            val intent = Intent(this, ReportsActivity::class.java)
            startActivity(intent)
        }

        // Settings
        binding.cardSettings.setOnClickListener {
            // TODO: Navigate to SettingsActivity
            Toast.makeText(this, "Settings - Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun performLogout() {
        // Sign out from Firebase
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

        // Sign out from Google
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        ).build()
        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut()

        // Sign out from Facebook
        com.facebook.login.LoginManager.getInstance().logOut()

        // Clear SharedPreferences
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Navigate to Login Screen
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }
}