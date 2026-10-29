package com.example.academyhub.course

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityManageCoursesBinding
import com.example.academyhub.models.Course
import com.google.firebase.firestore.FirebaseFirestore

class ManageCoursesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageCoursesBinding
    private val db = FirebaseFirestore.getInstance()

    private val allList      = mutableListOf<Course>()
    private val filteredList = mutableListOf<Course>()
    private lateinit var adapter: CourseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCoursesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CourseAdapter(
            courses  = filteredList,
            onEdit   = { course -> openEdit(course) },
            onToggle = { course -> confirmToggle(course) },
            onDelete = { course -> confirmDelete(course) }
        )
        binding.rvCourses.layoutManager = LinearLayoutManager(this)
        binding.rvCourses.adapter       = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddCourse.setOnClickListener {
            startActivity(Intent(this, AddCourseActivity::class.java))
        }

        setupSearch()
        setupSwipeRefresh()
        loadCourses()
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadCourses(fromSwipe = true) }
    }

    // ─── Load ALL courses ──────────────────────────────────────────────────────
    // Note: Load all (active + inactive) so toggle/delete works on inactive too

    private fun loadCourses(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        db.collection("Courses").get()
            .addOnSuccessListener { snap ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allList.clear()
                for (doc in snap) {
                    val course = doc.toObject(Course::class.java)
                    // Course model has val id — copy to set the doc id
                    allList.add(course.copy(id = doc.id))
                }
                allList.sortBy { it.courseName }
                loadStudentCounts()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Student count per course ──────────────────────────────────────────────

    private fun loadStudentCounts() {
        db.collection("users").whereEqualTo("role", "student").get()
            .addOnSuccessListener { snap ->
                val countMap = mutableMapOf<String, Int>()
                for (doc in snap) {
                    val cId = doc.getString("courseId") ?: continue
                    countMap[cId] = (countMap[cId] ?: 0) + 1
                }
                val updated = allList.map { c -> c.copy(studentCount = countMap[c.id] ?: 0) }
                allList.clear(); allList.addAll(updated)
                updateStats()
                applyFilter()
            }
            .addOnFailureListener {
                // Student count optional — continue anyway
                updateStats()
                applyFilter()
            }
    }

    // ─── Stats with ValueAnimator ──────────────────────────────────────────────

    private fun updateStats() {
        val total    = allList.size
        val active   = allList.count { it.isActive }
        val students = allList.sumOf { it.studentCount }

        ValueAnimator.ofInt(0, total).apply {
            duration = 500
            addUpdateListener { binding.tvTotalCount.text = "${it.animatedValue}" }
            start()
        }
        ValueAnimator.ofInt(0, active).apply {
            duration = 600
            addUpdateListener { binding.tvActiveCount.text = "${it.animatedValue}" }
            start()
        }
        ValueAnimator.ofInt(0, students).apply {
            duration = 700
            addUpdateListener { binding.tvTotalStudents.text = "${it.animatedValue}" }
            start()
        }
        binding.tvCount.text = "$total courses"
    }

    // ─── Filter + fade animation ───────────────────────────────────────────────

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(
            if (q.isEmpty()) allList
            else allList.filter {
                it.courseName.lowercase().contains(q) ||
                it.courseCode.lowercase().contains(q)
            }
        )
        val isEmpty = filteredList.isEmpty()
        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility = if (isEmpty) View.GONE    else View.VISIBLE
        binding.tvNoCourses.text =
            if (q.isNotEmpty()) "No results for \"$q\"" else "No courses found"

        adapter.notifyDataSetChanged()
        // Fade list in
        binding.rvCourses.alpha = 0f
        binding.rvCourses.animate().alpha(1f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── Edit ──────────────────────────────────────────────────────────────────

    private fun openEdit(course: Course) {
        startActivity(Intent(this, AddCourseActivity::class.java).apply {
            putExtra("isEdit",   true)
            putExtra("courseId", course.id)
        })
    }

    // ─── Toggle Active/Inactive ─────────────────────────────────────────────────
    // FIXED: reads live Firestore value + uses correct field "active" (not "isActive")

    private fun confirmToggle(course: Course) {
        db.collection("Courses").document(course.id).get()
            .addOnSuccessListener { doc ->
                val currentActive = doc.getBoolean("active") ?: true
                val newStatus     = !currentActive
                val label         = if (newStatus) "Activate" else "Deactivate"

                AlertDialog.Builder(this)
                    .setTitle("$label Course")
                    .setMessage("\"${course.courseName}\" ne $label karvu chhe?")
                    .setPositiveButton(label) { _, _ ->
                        binding.progressBar.visibility = View.VISIBLE
                        db.collection("Courses").document(course.id)
                            .update("active", newStatus)          // ✅ correct field name
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this,
                                    "${course.courseName} ${if (newStatus) "Activated!" else "Deactivated!"}",
                                    Toast.LENGTH_SHORT).show()
                                loadCourses()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Delete course + associated Years + Semesters ─────────────────────────

    private fun confirmDelete(course: Course) {
        AlertDialog.Builder(this)
            .setTitle("Delete Course")
            .setMessage("\"${course.courseName}\" delete karvu chhe?\n\nAa sathe badha Years ane Semesters pan delete thashe.")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                db.collection("Courses").document(course.id).delete()
                    .addOnSuccessListener { deleteYears(course.id) }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteYears(courseId: String) {
        db.collection("Years").whereEqualTo("courseId", courseId).get()
            .addOnSuccessListener { docs ->
                val batch = db.batch()
                for (doc in docs) batch.delete(doc.reference)
                batch.commit().addOnSuccessListener { deleteSemesters(courseId) }
            }
            .addOnFailureListener { deleteSemesters(courseId) }
    }

    private fun deleteSemesters(courseId: String) {
        db.collection("Semesters").whereEqualTo("courseId", courseId).get()
            .addOnSuccessListener { docs ->
                val batch = db.batch()
                for (doc in docs) batch.delete(doc.reference)
                batch.commit().addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Course deleted successfully", Toast.LENGTH_SHORT).show()
                    loadCourses()
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Course deleted", Toast.LENGTH_SHORT).show()
                loadCourses()
            }
    }

    override fun onResume() { super.onResume(); loadCourses() }
}
