package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentProfileBinding
import com.example.academyhub.models.Student
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileBinding
    private lateinit var firestore: FirebaseFirestore
    private val auth  = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        setupClickListeners()
        loadStudentData()
    }

    // -------------------------------------------------------
    //  Listeners
    // -------------------------------------------------------
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        binding.btnLogout.setOnClickListener {
            ObjectAnimator.ofFloat(binding.btnLogout, "scaleX", 1f, 1.3f, 1f)
                .apply { duration = 200; start() }
            ObjectAnimator.ofFloat(binding.btnLogout, "scaleY", 1f, 1.3f, 1f)
                .apply { duration = 200; start() }
            showLogoutDialog()
        }
    }

    // -------------------------------------------------------
    //  Load student data from SharedPrefs → Firestore
    // -------------------------------------------------------
    private fun loadStudentData() {
        binding.progressBar.visibility = View.VISIBLE

        val studentDocId  = prefs.getString("studentDocId", "") ?: ""
        val studentMobile = prefs.getString("userPhone",    "") ?: ""

        if (studentDocId.isNotEmpty()) {
            firestore.collection("Students").document(studentDocId).get()
                .addOnSuccessListener { doc ->
                    binding.progressBar.visibility = View.GONE
                    if (doc.exists()) {
                        doc.toObject(Student::class.java)?.let { s ->
                            displayStudent(s)
                            loadPhoto(studentDocId)
                        }
                    } else {
                        loadByPhone(studentMobile)
                    }
                }
                .addOnFailureListener {
                    binding.progressBar.visibility = View.GONE
                    loadByPhone(studentMobile)
                }
        } else if (studentMobile.isNotEmpty()) {
            loadByPhone(studentMobile)
        } else {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadByPhone(phone: String) {
        if (phone.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            return
        }
        firestore.collection("Students")
            .whereEqualTo("mobile", phone).limit(1).get()
            .addOnSuccessListener { q ->
                binding.progressBar.visibility = View.GONE
                if (!q.isEmpty) {
                    val doc = q.documents[0]
                    doc.toObject(Student::class.java)?.let { s ->
                        displayStudent(s)
                        loadPhoto(doc.id)
                    }
                } else {
                    Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------
    //  Display
    // -------------------------------------------------------
    private fun displayStudent(s: Student) {
        binding.tvStudentName.text = s.name
        binding.tvRollNumber.text  =
            if (s.rollNumber.isNotEmpty()) "Roll: ${s.rollNumber}" else "Roll: --"

        setStatusBadge(s.status)

        binding.tvEmail.text         = s.email.ifEmpty { "--" }
        binding.tvMobile.text        = s.mobile.ifEmpty { "--" }
        binding.tvGender.text        = s.gender.ifEmpty { "--" }
        binding.tvDOB.text           = s.dateOfBirth.ifEmpty { "--" }
        binding.tvAddress.text       = s.address.ifEmpty { "--" }

        binding.tvSession.text       = s.sessionName.ifEmpty { "--" }
        binding.tvCourse.text        = s.courseName.ifEmpty { "--" }
        binding.tvYear.text          = s.yearName.ifEmpty { "--" }
        binding.tvSemester.text      = s.semesterName.ifEmpty { "--" }
        binding.tvBatch.text         = s.batchName.ifEmpty { "--" }
        binding.tvAdmissionDate.text = s.admissionDate.ifEmpty { "--" }

        animateAttendance(s.attendancePercentage)

        binding.tvTotalFees.text = "Rs. ${s.totalFees.toInt()}"
        binding.tvPaidFees.text  = "Rs. ${s.paidFees.toInt()}"
        val due = (s.totalFees - s.paidFees).toInt().coerceAtLeast(0)
        binding.tvDueFees.text   = "Rs. $due"
        setFeesBadge(s.feesStatus)

        animateContentIn()
    }

    private fun setStatusBadge(status: String) {
        val (label, textColor) = when (status) {
            "active"   -> "ACTIVE"   to "#075E54"
            "inactive" -> "INACTIVE" to "#616161"
            "blocked"  -> "BLOCKED"  to "#C62828"
            "passout"  -> "PASS OUT" to "#1565C0"
            else       -> status.uppercase() to "#616161"
        }
        binding.tvStatus.text = label
        binding.tvStatus.setTextColor(Color.parseColor(textColor))
    }

    private fun setFeesBadge(feesStatus: String) {
        val (label, textColor, bgColor) = when (feesStatus) {
            "paid"    -> Triple("PAID",    "#2E7D32", "#E8F5E9")
            "pending" -> Triple("PENDING", "#E65100", "#FFF8E1")
            "partial" -> Triple("PARTIAL", "#F57F17", "#FFFDE7")
            else      -> Triple("N/A",     "#9E9E9E", "#F5F5F5")
        }
        binding.tvFeesStatus.text = label
        binding.tvFeesStatus.setTextColor(Color.parseColor(textColor))
        (binding.tvFeesStatus.background as? GradientDrawable)
            ?.setColor(Color.parseColor(bgColor))
    }

    // -------------------------------------------------------
    //  Attendance animated bar
    // -------------------------------------------------------
    private fun animateAttendance(percent: Double) {
        val safe = percent.coerceIn(0.0, 100.0)

        ValueAnimator.ofFloat(0f, safe.toFloat()).apply {
            duration = 800; interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                binding.tvAttendance.text = "${v.toInt()}%"
                binding.tvAttendance.setTextColor(Color.parseColor(when {
                    v >= 75 -> "#075E54"
                    v >= 50 -> "#E65100"
                    else    -> "#C62828"
                }))
            }
            start()
        }

        binding.attendanceBarFill.post {
            val trackWidth  = (binding.attendanceBarFill.parent as ViewGroup).width
            val targetWidth = ((safe / 100.0) * trackWidth).toInt()
            ValueAnimator.ofInt(0, targetWidth).apply {
                duration = 900; interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    val lp = binding.attendanceBarFill.layoutParams
                    lp.width = anim.animatedValue as Int
                    binding.attendanceBarFill.layoutParams = lp
                    val pct = (anim.animatedValue as Int).toFloat() / trackWidth.coerceAtLeast(1) * 100f
                    (binding.attendanceBarFill.background as? GradientDrawable)
                        ?.setColor(Color.parseColor(when {
                            pct >= 75 -> "#075E54"
                            pct >= 50 -> "#E65100"
                            else      -> "#C62828"
                        }))
                }
                start()
            }
        }
    }

    // -------------------------------------------------------
    //  Entrance animation
    // -------------------------------------------------------
    private fun animateContentIn() {
        binding.layoutContent.visibility = View.VISIBLE
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutContent, "alpha", 0f, 1f)
                    .apply { duration = 350 },
                ObjectAnimator.ofFloat(binding.layoutContent, "translationY", 40f, 0f)
                    .apply { duration = 380; interpolator = DecelerateInterpolator() }
            )
            start()
        }
    }

    // -------------------------------------------------------
    //  Load profile photo
    // -------------------------------------------------------
    private fun loadPhoto(id: String) {
        binding.ivStudentProfile.setImageResource(R.drawable.ic_student_default)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                ImageUtil.loadImageFromChunks(
                    firestore   = firestore,
                    userId      = id,
                    collection  = "Students",
                    imageView   = binding.ivStudentProfile,
                    progressBar = null
                ) { success ->
                    binding.progressBar.visibility = View.GONE
                    if (success) {
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleX", 0.7f, 1f)
                                    .apply { duration = 300 },
                                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleY", 0.7f, 1f)
                                    .apply { duration = 300; interpolator = OvershootInterpolator() }
                            )
                            start()
                        }
                    } else {
                        binding.ivStudentProfile.setImageResource(R.drawable.ic_student_default)
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.ivStudentProfile.setImageResource(R.drawable.ic_student_default)
            }
        }
    }

    // -------------------------------------------------------
    //  Logout
    // -------------------------------------------------------
    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                auth.signOut()
                prefs.edit().clear().apply()
                val intent = Intent(this, com.example.academyhub.LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_up)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadStudentData()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, StudentProfileActivity::class.java))
        }
    }
}