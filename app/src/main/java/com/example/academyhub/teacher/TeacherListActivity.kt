package com.example.academyhub.teacher

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityTeacherListBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.teacher.adapters.TeacherAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class TeacherListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherListBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var teacherAdapter: TeacherAdapter
    private var teachersList   = mutableListOf<Teacher>()
    private var filteredList   = mutableListOf<Teacher>()

    // Filter state
    private var selectedStatus     = "all"
    private var selectedSubject    = "all"
    private var selectedExperience = "all"
    private var sortBy             = "name"

    // Optional: course-based filter (from ManageCoursesActivity)
    private var filterByCourseId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityTeacherListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        // If launched from Course Details
        filterByCourseId = intent.getStringExtra("filterByCourseId") ?: ""
        val courseTitle   = intent.getStringExtra("courseTitle") ?: ""
        if (courseTitle.isNotEmpty()) {
            binding.tvTitle.text = courseTitle
        }

        setupRecyclerView()
        setupFilterDrawer()
        setupSearchAndClickListeners()
        loadTeachers()
    }

    private fun setupRecyclerView() {
        teacherAdapter = TeacherAdapter(
            teachers    = filteredList,
            onItemClick = { teacher ->
                startActivity(Intent(this, TeacherDetailsActivity::class.java)
                    .putExtra("teacherId", teacher.id))
            },
            onMoreClick = { teacher -> showOptionsMenu(teacher) }
        )
        binding.rvTeachers.apply {
            layoutManager = LinearLayoutManager(this@TeacherListActivity)
            adapter = teacherAdapter
        }
    }

    private fun setupFilterDrawer() {
        val statusOptions = arrayOf("All", "Active", "Inactive", "Blocked", "On Leave")
        binding.spinnerStatusFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusOptions)
        )
        val expOptions = arrayOf("All", "0–5 Years", "5–10 Years", "10+ Years")
        binding.spinnerExperienceFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, expOptions)
        )
        val sortOptions = arrayOf("Name (A–Z)", "Experience ↓", "Rating ↓", "Salary ↓")
        binding.spinnerSortBy.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, sortOptions)
        )
    }

    private fun setupSearchAndClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.fabAddTeacher.setOnClickListener {
            startActivity(Intent(this, AddTeacherActivity::class.java))
        }

        // Open filter drawer (same as student list)
        binding.btnFilter.setOnClickListener {
            binding.drawerLayout.openDrawer(binding.filterDrawer)
        }

        // Apply filters
        binding.btnApplyFilter.setOnClickListener {
            selectedStatus = when (binding.spinnerStatusFilter.text.toString()) {
                "Active"   -> "active"
                "Inactive" -> "inactive"
                "Blocked"  -> "blocked"
                "On Leave" -> "on_leave"
                else       -> "all"
            }
            selectedSubject = binding.spinnerSubjectFilter.text.toString().let {
                if (it.isEmpty() || it == "All Subjects") "all" else it
            }
            selectedExperience = when (binding.spinnerExperienceFilter.text.toString()) {
                "0–5 Years"  -> "0-5"
                "5–10 Years" -> "5-10"
                "10+ Years"  -> "10+"
                else         -> "all"
            }
            sortBy = when (binding.spinnerSortBy.text.toString()) {
                "Experience ↓" -> "experience"
                "Rating ↓"     -> "rating"
                "Salary ↓"     -> "salary"
                else           -> "name"
            }
            applyFiltersAndSort()
            binding.drawerLayout.closeDrawer(binding.filterDrawer)
        }

        // Clear inside drawer
        binding.btnClearAllFilters.setOnClickListener { clearAllFilters() }

        // Clear banner button
        binding.btnClearFilters.setOnClickListener { clearAllFilters() }

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFiltersAndSort() }
        })
    }

    private fun loadTeachers() {
        binding.progressBar.visibility  = View.VISIBLE
        binding.rvTeachers.visibility   = View.GONE
        binding.tvNoTeachers.visibility = View.GONE

        // If course filter active → filter by assignedCourses
        val query = if (filterByCourseId.isNotEmpty()) {
            firestore.collection("Teachers")
                .whereArrayContains("assignedCourses", filterByCourseId)
                .orderBy("name", Query.Direction.ASCENDING)
        } else {
            firestore.collection("Teachers")
                .orderBy("name", Query.Direction.ASCENDING)
        }

        query.addSnapshotListener { snapshot, error ->
            binding.progressBar.visibility = View.GONE

            if (error != null) {
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.tvNoTeachers.visibility = View.VISIBLE
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                teachersList.clear()
                for (doc in snapshot.documents) {
                    val t = doc.toObject(Teacher::class.java)
                    t?.let { teachersList.add(it) }
                }

                // Populate subject filter from loaded data
                val subjects = teachersList.map { it.subject }.filter { it.isNotEmpty() }.distinct().sorted()
                val subjectOptions = arrayOf("All Subjects") + subjects.toTypedArray()
                binding.spinnerSubjectFilter.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, subjectOptions)
                )

                applyFiltersAndSort()
                updateStats()
            } else {
                binding.tvNoTeachers.text = if (filterByCourseId.isNotEmpty())
                    "No teachers assigned to this course"
                else "No teachers found\n\nClick + to add first teacher"
                binding.tvNoTeachers.visibility = View.VISIBLE
                binding.rvTeachers.visibility   = View.GONE
            }
        }
    }

    // ── Complete filter + sort + search ──
    private fun applyFiltersAndSort() {
        val query = binding.etSearch.text.toString().lowercase().trim()
        var result = teachersList.toMutableList()

        // 1. Search
        if (query.isNotEmpty()) {
            result = result.filter {
                it.name.lowercase().contains(query)           ||
                it.subject.lowercase().contains(query)        ||
                it.mobile.contains(query)                     ||
                it.email.lowercase().contains(query)          ||
                it.qualification.lowercase().contains(query)
            }.toMutableList()
        }

        // 2. Status
        if (selectedStatus != "all") {
            result = result.filter { it.status == selectedStatus }.toMutableList()
        }

        // 3. Subject
        if (selectedSubject != "all") {
            result = result.filter {
                it.subject.lowercase() == selectedSubject.lowercase()
            }.toMutableList()
        }

        // 4. Experience
        when (selectedExperience) {
            "0-5"  -> result = result.filter { it.experience in 0..5 }.toMutableList()
            "5-10" -> result = result.filter { it.experience in 6..10 }.toMutableList()
            "10+"  -> result = result.filter { it.experience > 10 }.toMutableList()
        }

        // 5. Sort
        result = when (sortBy) {
            "experience" -> result.sortedByDescending { it.experience }.toMutableList()
            "rating"     -> result.sortedByDescending { it.rating }.toMutableList()
            "salary"     -> result.sortedByDescending { it.currentSalary }.toMutableList()
            else         -> result.sortedBy { it.name }.toMutableList()
        }

        filteredList.clear()
        filteredList.addAll(result)
        teacherAdapter.updateList(filteredList)

        // Filter active banner
        val hasFilters = selectedStatus != "all" || selectedSubject != "all" ||
                         selectedExperience != "all" || sortBy != "name"
        binding.layoutActiveFilters.visibility = if (hasFilters) View.VISIBLE else View.GONE
        if (hasFilters) binding.tvActiveFilters.text = buildFilterLabel()

        // Empty state
        if (filteredList.isEmpty()) {
            binding.tvNoTeachers.text = if (query.isNotEmpty()) "No teachers match your search"
                                        else "No teachers found"
            binding.tvNoTeachers.visibility = View.VISIBLE
            binding.rvTeachers.visibility   = View.GONE
        } else {
            binding.tvNoTeachers.visibility = View.GONE
            binding.rvTeachers.visibility   = View.VISIBLE
        }
    }

    private fun buildFilterLabel(): String {
        val parts = mutableListOf<String>()
        if (selectedStatus != "all")     parts.add("Status: $selectedStatus")
        if (selectedSubject != "all")    parts.add("Subject: $selectedSubject")
        if (selectedExperience != "all") parts.add("Exp: $selectedExperience")
        if (sortBy != "name")            parts.add("Sort: $sortBy")
        return "Filters: ${parts.joinToString(" • ")}"
    }

    private fun updateStats() {
        binding.tvTotalTeachers.text    = "Total: ${teachersList.size}"
        binding.tvActiveTeachers.text   = "Active: ${teachersList.count { it.status == "active" }}"
        binding.tvInactiveTeachers.text = "Inactive: ${teachersList.count { it.status == "inactive" }}"
    }

    private fun clearAllFilters() {
        selectedStatus = "all"; selectedSubject = "all"
        selectedExperience = "all"; sortBy = "name"
        binding.etSearch.setText("")
        binding.spinnerStatusFilter.setText("", false)
        binding.spinnerSubjectFilter.setText("", false)
        binding.spinnerExperienceFilter.setText("", false)
        binding.spinnerSortBy.setText("", false)
        applyFiltersAndSort()
        if (binding.drawerLayout.isDrawerOpen(binding.filterDrawer)) {
            binding.drawerLayout.closeDrawer(binding.filterDrawer)
        }
    }

    private fun showOptionsMenu(teacher: Teacher) {
        val popup = PopupMenu(this, binding.rvTeachers)
        popup.menuInflater.inflate(R.menu.menu_teacher_options, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_view -> {
                    startActivity(Intent(this, TeacherDetailsActivity::class.java)
                        .putExtra("teacherId", teacher.id)); true
                }
                R.id.action_edit -> {
                    startActivity(Intent(this, AddTeacherActivity::class.java)
                        .putExtra("teacherId", teacher.id).putExtra("isEdit", true)); true
                }
                R.id.action_delete    -> { showDeleteConfirmation(teacher); true }
                R.id.action_salary    -> {
                    startActivity(Intent(this, TeacherSalaryActivity::class.java)
                        .putExtra("teacherId", teacher.id)); true
                }
                R.id.action_attendance -> {
                    startActivity(Intent(this, TeacherAttendanceActivity::class.java)
                        .putExtra("teacherId", teacher.id)); true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDeleteConfirmation(teacher: Teacher) {
        AlertDialog.Builder(this)
            .setTitle("Delete Teacher")
            .setMessage("Delete \"${teacher.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteTeacher(teacher) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteTeacher(teacher: Teacher) {
        firestore.collection("Teachers").document(teacher.id).delete()
            .addOnSuccessListener {
                // Also delete photo_chunks subcollection
                firestore.collection("Teachers").document(teacher.id)
                    .collection("photo_chunks").get()
                    .addOnSuccessListener { chunks ->
                        val batch = firestore.batch()
                        chunks.documents.forEach { batch.delete(it.reference) }
                        batch.commit()
                    }
                Toast.makeText(this, "Teacher deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
