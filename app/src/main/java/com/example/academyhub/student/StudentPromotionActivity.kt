package com.example.academyhub.student

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityStudentPromotionBinding
import com.example.academyhub.models.*
import com.google.firebase.firestore.FirebaseFirestore

class StudentPromotionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentPromotionBinding
    private lateinit var firestore: FirebaseFirestore

    private val coursesList = mutableListOf<Course>()
    private val yearsList = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()
    private val studentsList = mutableListOf<Student>()
    private val selectedStudents = mutableListOf<Student>()

    private var selectedCourse: Course? = null
    private var selectedYear: AcademicYear? = null
    private var selectedSemester: Semester? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentPromotionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        setupRecyclerView()
        setupClickListeners()
        loadCourses()
    }

    private fun setupRecyclerView() {
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.spinnerCourse.setOnItemClickListener { _, _, position, _ ->
            selectedCourse = coursesList[position]
            selectedYear = null
            selectedSemester = null
            binding.spinnerYear.setText("", false)
            binding.spinnerSemester.setText("", false)
            loadYears(selectedCourse!!.id)
        }

        binding.spinnerYear.setOnItemClickListener { _, _, position, _ ->
            selectedYear = yearsList[position]
            selectedSemester = null
            binding.spinnerSemester.setText("", false)
            loadSemesters(selectedYear!!.id)
        }

        binding.spinnerSemester.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = semestersList[position]
        }

        binding.btnLoadStudents.setOnClickListener {
            if (validateSelection()) {
                loadStudents()
            }
        }

        binding.btnPromote.setOnClickListener {
            if (selectedStudents.isNotEmpty()) {
                showPromotionConfirmation()
            }
        }
    }

    private fun loadCourses() {
        firestore.collection("Courses")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { documents ->
                coursesList.clear()
                for (doc in documents) {
                    coursesList.add(doc.toObject(Course::class.java))
                }

                val courseNames = coursesList.map { it.courseName }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, courseNames)
                binding.spinnerCourse.setAdapter(adapter)
            }
    }

    private fun loadYears(courseId: String) {
        firestore.collection("Years")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { documents ->
                yearsList.clear()
                for (doc in documents) {
                    yearsList.add(doc.toObject(AcademicYear::class.java))
                }

                yearsList.sortBy { it.yearNumber }

                val yearNames = yearsList.map { it.yearName }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, yearNames)
                binding.spinnerYear.setAdapter(adapter)
            }
    }

    private fun loadSemesters(yearId: String) {
        firestore.collection("Semesters")
            .whereEqualTo("yearId", yearId)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { documents ->
                semestersList.clear()
                for (doc in documents) {
                    semestersList.add(doc.toObject(Semester::class.java))
                }

                semestersList.sortBy { it.semesterNumber }

                val semesterNames = semestersList.map { it.semesterName }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesterNames)
                binding.spinnerSemester.setAdapter(adapter)
            }
    }

    private fun validateSelection(): Boolean {
        if (selectedCourse == null) {
            Toast.makeText(this, "Please select course", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedYear == null) {
            Toast.makeText(this, "Please select year", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedSemester == null) {
            Toast.makeText(this, "Please select semester", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun loadStudents() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Students")
            .whereEqualTo("courseId", selectedCourse!!.id)
            .whereEqualTo("yearId", selectedYear!!.id)
            .whereEqualTo("semesterId", selectedSemester!!.id)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE

                studentsList.clear()
                for (doc in documents) {
                    studentsList.add(doc.toObject(Student::class.java))
                }

                if (studentsList.isEmpty()) {
                    Toast.makeText(this, "No students found", Toast.LENGTH_SHORT).show()
                } else {
                    // Setup adapter for student selection
                    Toast.makeText(this, "${studentsList.size} students loaded", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPromotionConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Promote Students")
            .setMessage("Are you sure you want to promote ${selectedStudents.size} students to next semester?")
            .setPositiveButton("Promote") { _, _ ->
                promoteStudents()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promoteStudents() {
        binding.progressBar.visibility = View.VISIBLE

        // TODO: Implement promotion logic
        // This will move students to next semester

        Toast.makeText(this, "Promotion feature coming soon!", Toast.LENGTH_SHORT).show()
        binding.progressBar.visibility = View.GONE
    }

    private fun updateSelectedCount() {
        binding.tvSelectedCount.text = "${selectedStudents.size} students selected"
        binding.btnPromote.isEnabled = selectedStudents.isNotEmpty()
    }
}