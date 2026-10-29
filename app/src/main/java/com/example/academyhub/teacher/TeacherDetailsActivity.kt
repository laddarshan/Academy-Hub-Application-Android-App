package com.example.academyhub.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.academyhub.databinding.ActivityTeacherDetailsBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.utils.ImageUtil
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TeacherDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherDetailsBinding
    private lateinit var firestore: FirebaseFirestore
    private var teacherId: String = ""
    private var teacher: Teacher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        if (teacherId.isEmpty()) {
            Toast.makeText(this, "Invalid teacher ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadTeacherDetails()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnEdit.setOnClickListener {
            startActivity(
                Intent(this, AddTeacherActivity::class.java)
                    .putExtra("teacherId", teacherId)
                    .putExtra("isEdit", true)
            )
        }

        binding.btnSalary.setOnClickListener {
            startActivity(
                Intent(this, TeacherSalaryActivity::class.java)
                    .putExtra("teacherId", teacherId)
            )
        }

        binding.btnAttendance.setOnClickListener {
            startActivity(
                Intent(this, TeacherAttendanceActivity::class.java)
                    .putExtra("teacherId", teacherId)
            )
        }

        // ─── FIX: btnPerformance - was using un-initialized intent variable ───
        binding.btnPerformance.setOnClickListener {
            // Uncomment when TeacherPerformanceActivity is created:
            // startActivity(
            //     Intent(this, TeacherPerformanceActivity::class.java)
            //         .putExtra("teacherId", teacherId)
            // )
            Toast.makeText(this, "Performance - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnLeave.setOnClickListener {
            startActivity(
                Intent(this, LeaveManagementActivity::class.java)
                    .putExtra("teacherId", teacherId)
            )
        }

        binding.btnDocuments.setOnClickListener {
            startActivity(
                Intent(this, DocumentsActivity::class.java)
                    .putExtra("teacherId", teacherId)
            )
        }
    }

    private fun loadTeacherDetails() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Teachers")
            .document(teacherId)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE

                if (document.exists()) {
                    teacher = document.toObject(Teacher::class.java)
                    teacher?.let {
                        displayTeacherData(it)

                        loadProfileImage(teacherId)
                    }
                } else {
                    Toast.makeText(this, "Teacher not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadProfileImage(teacherId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                ImageUtil.loadImageFromChunks(
                    firestore = firestore,
                    userId = teacherId,
                    collection = "Teachers",
                    imageView = binding.ivTeacherProfile,
                    progressBar = binding.progressBar,
                    onComplete = { binding.progressBar.visibility = View.GONE }
                )
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                e.printStackTrace()
            }
        }
    }

    private fun displayTeacherData(teacher: Teacher) {
        binding.tvTeacherName.text = teacher.name
        binding.tvTeacherId.text   = "ID: ${teacher.id.take(8)}"
        binding.tvStatus.text      = teacher.status.uppercase()

        binding.tvStatus.setBackgroundColor(
            when (teacher.status) {
                "active"   -> getColor(android.R.color.holo_green_dark)
                "inactive" -> getColor(android.R.color.holo_orange_dark)
                "blocked"  -> getColor(android.R.color.holo_red_dark)
                "on_leave" -> getColor(android.R.color.holo_blue_dark)
                else       -> getColor(android.R.color.darker_gray)
            }
        )

        binding.tvExperience.text = "${teacher.experience} Years"
        binding.tvRating.text     = "⭐ ${teacher.getRatingString()}"
        binding.tvSalary.text     = "₹${String.format("%,.0f", teacher.currentSalary)}"
        binding.tvAttendance.text = teacher.getAttendancePercent()

        binding.tvEmail.text       = "Email          :  ${teacher.email}"
        binding.tvMobile.text      = "Mobile         :  ${teacher.mobile}"
        binding.tvGender.text      = "Gender         :  ${teacher.gender}"
        binding.tvDOB.text         = "DateOfBirth  :  ${teacher.dateOfBirth}"
        binding.tvAddress.text     = "Address        :  ${teacher.address}"
        binding.tvSubject.text     = "Subject :  ${teacher.subject}"
        binding.tvQualification.text = "Qualification : ${teacher.qualification}"
        binding.tvExperienceDetail.text = "Experience    : ${teacher.experience}"
        binding.tvJoiningDate.text   = "JoiningDate   : ${teacher.joiningDate}"

        binding.tvCasualLeave.text = "CL : ${teacher.getRemainingCasualLeave()}/${teacher.casualLeaveBalance}"
        binding.tvSickLeave.text   = "SL : ${teacher.getRemainingSickLeave()}/${teacher.sickLeaveBalance}"

        if (teacher.assignedCourses.isNotEmpty()) {
            binding.tvAssignedCourses.text = "Courses: ${teacher.assignedCourses.joinToString(", ")}"
            binding.tvAssignedCourses.visibility = View.VISIBLE
        } else {
            binding.tvAssignedCourses.visibility = View.GONE
        }

        if (teacher.assignedBatches.isNotEmpty()) {
            binding.tvAssignedBatches.text = "Batches: ${teacher.assignedBatches.joinToString(", ")}"
            binding.tvAssignedBatches.visibility = View.VISIBLE
        } else {
            binding.tvAssignedBatches.visibility = View.GONE
        }
    }



    override fun onResume() {
        super.onResume()
        loadTeacherDetails()
    }

    private inner class ViewPagerAdapter(
        activity: AppCompatActivity,
        private val teacher: Teacher
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TeacherOverviewFragment.newInstance(teacher.id)
                1 -> TeacherPerformanceFragment.newInstance(teacher.id)
                2 -> TeacherAttendanceFragment.newInstance(teacher.id)
                3 -> TeacherDocumentsFragment.newInstance(teacher.id)
                else -> TeacherOverviewFragment.newInstance(teacher.id)
            }
        }
    }
}

// ─── Placeholder Fragments ───

class TeacherOverviewFragment : Fragment() {
    companion object {
        fun newInstance(teacherId: String) = TeacherOverviewFragment().apply {
            arguments = Bundle().apply { putString("teacherId", teacherId) }
        }
    }
}

class TeacherPerformanceFragment : Fragment() {
    companion object {
        fun newInstance(teacherId: String) = TeacherPerformanceFragment().apply {
            arguments = Bundle().apply { putString("teacherId", teacherId) }
        }
    }
}

class TeacherAttendanceFragment : Fragment() {
    companion object {
        fun newInstance(teacherId: String) = TeacherAttendanceFragment().apply {
            arguments = Bundle().apply { putString("teacherId", teacherId) }
        }
    }
}

class TeacherDocumentsFragment : Fragment() {
    companion object {
        fun newInstance(teacherId: String) = TeacherDocumentsFragment().apply {
            arguments = Bundle().apply { putString("teacherId", teacherId) }
        }
    }
}
