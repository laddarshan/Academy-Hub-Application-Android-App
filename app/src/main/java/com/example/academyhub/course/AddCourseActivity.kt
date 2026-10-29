package com.example.academyhub.course

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAddCourseBinding
import com.example.academyhub.models.Course
import com.google.firebase.firestore.FirebaseFirestore

class AddCourseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddCourseBinding
    private val db = FirebaseFirestore.getInstance()

    private var isEditMode       = false
    private var courseId         = ""
    private var selectedDuration = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode = intent.getBooleanExtra("isEdit", false)
        courseId   = intent.getStringExtra("courseId") ?: ""

        if (isEditMode) {
            binding.tvTitle.text     = "Edit Course"
            binding.tvSaveLabel.text = "Update Course"
            loadCourseData()
        }

        setupDurationDropdown()
        binding.btnBack.setOnClickListener { finish() }

        // Save button entrance animation
        binding.btnSave.alpha        = 0f
        binding.btnSave.translationY = 24f
        binding.btnSave.animate()
            .alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f)).start()

        binding.btnSave.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
                ); start()
            }
            it.postDelayed({ if (isEditMode) updateCourse() else saveCourse() }, 130)
        }
    }

    // ─── Duration dropdown ─────────────────────────────────────────────────────

    private fun setupDurationDropdown() {
        val durations = arrayOf("1 Year", "2 Years", "3 Years", "4 Years", "5 Years")
        val adapter   = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, durations)
        binding.spinnerDuration.setAdapter(adapter)
        binding.spinnerDuration.setText("3 Years", false)
        binding.etTotalSemesters.setText("6")
        selectedDuration = 3

        binding.spinnerDuration.setOnItemClickListener { _, _, position, _ ->
            selectedDuration = position + 1
            binding.etTotalSemesters.setText((selectedDuration * 2).toString())
        }
    }

    // ─── Load existing data (edit mode) ───────────────────────────────────────

    private fun loadCourseData() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("Courses").document(courseId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                val course = doc.toObject(Course::class.java) ?: return@addOnSuccessListener
                binding.etCourseName.setText(course.courseName)
                binding.etCourseCode.setText(course.courseCode)
                binding.spinnerDuration.setText(
                    "${course.duration} Year${if (course.duration > 1) "s" else ""}", false)
                binding.etDescription.setText(course.description)
                binding.etTotalSemesters.setText(course.totalSemesters.toString())
                selectedDuration = course.duration
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Save NEW course ───────────────────────────────────────────────────────

    private fun saveCourse() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isClickable    = false

        val newId = db.collection("Courses").document().id

        // Use HashMap with correct Firestore field "active" — NOT "isActive"
        // (Course model has @PropertyName("active") but Kotlin data class serialization
        //  can be inconsistent — HashMap guarantees correct field name)
        val data = hashMapOf<String, Any>(
            "id"             to newId,
            "courseName"     to binding.etCourseName.text.toString().trim(),
            "courseCode"     to binding.etCourseCode.text.toString().trim().uppercase(),
            "duration"       to selectedDuration,
            "totalSemesters" to (selectedDuration * 2),
            "description"    to binding.etDescription.text.toString().trim(),
            "active"         to true,           // ✅ correct Firestore field name
            "createdAt"      to System.currentTimeMillis(),
            "createdBy"      to "admin",
            "studentCount"   to 0
        )

        db.collection("Courses").document(newId).set(data)
            .addOnSuccessListener {
                generateYearsAndSemesters(
                    id             = newId,
                    courseName     = data["courseName"] as String,
                    courseCode     = data["courseCode"] as String,
                    duration       = selectedDuration,
                    totalSemesters = selectedDuration * 2
                )
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Auto-generate Years + Semesters ──────────────────────────────────────

    private fun generateYearsAndSemesters(
        id: String, courseName: String, courseCode: String,
        duration: Int, totalSemesters: Int
    ) {
        val yearNames     = arrayOf("FY", "SY", "TY", "FoY", "FiY")
        val yearFullNames = arrayOf("First Year", "Second Year", "Third Year", "Fourth Year", "Fifth Year")
        val batch         = db.batch()

        for (i in 0 until duration) {
            val yearId = db.collection("Years").document().id
            val yearMap = hashMapOf<String, Any>(
                "id"           to yearId,
                "courseId"     to id,
                "courseName"   to courseName,
                "yearName"     to yearNames[i],
                "yearNumber"   to (i + 1),
                "yearFullName" to yearFullNames[i],
                "active"       to true,
                "createdAt"    to System.currentTimeMillis()
            )
            batch.set(db.collection("Years").document(yearId), yearMap)

            for (j in 1..2) {
                val semNum = (i * 2) + j
                val semId  = db.collection("Semesters").document().id
                val semMap = hashMapOf<String, Any>(
                    "id"             to semId,
                    "courseId"       to id,
                    "courseName"     to courseName,
                    "yearId"         to yearId,
                    "yearName"       to yearNames[i],
                    "semesterName"   to "Sem-$semNum",
                    "semesterNumber" to semNum,
                    "active"         to true,
                    "createdAt"      to System.currentTimeMillis()
                )
                batch.set(db.collection("Semesters").document(semId), semMap)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this,
                    "Course created! $duration years, $totalSemesters semesters generated.",
                    Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this,
                    "Course saved but years/semesters error: ${e.message}",
                    Toast.LENGTH_LONG).show()
                finish()
            }
    }

    // ─── Update EXISTING course ────────────────────────────────────────────────

    private fun updateCourse() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isClickable    = false

        val updates = hashMapOf<String, Any>(
            "courseName"     to binding.etCourseName.text.toString().trim(),
            "courseCode"     to binding.etCourseCode.text.toString().trim().uppercase(),
            "duration"       to selectedDuration,
            "totalSemesters" to (selectedDuration * 2),
            "description"    to binding.etDescription.text.toString().trim()
        )

        db.collection("Courses").document(courseId).update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Course updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Validate ──────────────────────────────────────────────────────────────

    private fun validate(): Boolean {
        val name = binding.etCourseName.text.toString().trim()
        val code = binding.etCourseCode.text.toString().trim()
        if (name.isEmpty()) {
            binding.etCourseName.error = "Course name jaruri chhe"
            binding.etCourseName.requestFocus(); return false
        }
        if (code.isEmpty()) {
            binding.etCourseCode.error = "Course code jaruri chhe"
            binding.etCourseCode.requestFocus(); return false
        }
        if (binding.spinnerDuration.text.isNullOrEmpty()) {
            Toast.makeText(this, "Duration select karo", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
