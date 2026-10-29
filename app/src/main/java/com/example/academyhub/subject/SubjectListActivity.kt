package com.example.academyhub.subject

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import com.example.academyhub.databinding.ActivitySubjectListBinding
import com.example.academyhub.models.Subject
import com.google.firebase.firestore.FirebaseFirestore

class SubjectListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectListBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SubjectAdapter

    private val allSubjects      = mutableListOf<Subject>()
    private val filteredSubjects = mutableListOf<Subject>()

    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvGroupBadge.text = listOf(sessionName, courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        setupRecycler()
        setupSearch()
        setupSwipeRefresh()

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }
        binding.fabAddSubject.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.9f, 1f).apply { duration = 140 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.9f, 1f).apply { duration = 140 }
                ); start()
            }
            it.postDelayed({ openAddSubject(null) }, 110)
        }

        loadSubjects()
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecycler() {
        adapter = SubjectAdapter(
            subjects      = filteredSubjects,
            onEditClick   = { openAddSubject(it) },
            onDeleteClick = { confirmDelete(it) }
        )
        binding.rvSubjects.layoutManager = LinearLayoutManager(this)
        binding.rvSubjects.adapter = adapter
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterAndShow()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── SwipeRefresh ─────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadSubjects(fromSwipe = true) }
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    private fun loadSubjects(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Subjects")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allSubjects.clear()
                for (doc in docs) {
                    val s = doc.toObject(Subject::class.java)
                    // Kotlin Boolean "isActive" serializes as "active" in Firestore (Java Bean)
                    // Check "active" field first, fall back to "isActive", default true
                    val isActive = doc.getBoolean("active")
                        ?: doc.getBoolean("isActive")
                        ?: true
                    if (isActive) allSubjects.add(s)
                }
                allSubjects.sortBy { it.subjectName }
                filterAndShow()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    private fun filterAndShow() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredSubjects.clear()
        filteredSubjects.addAll(
            if (q.isEmpty()) allSubjects
            else allSubjects.filter {
                it.subjectName.lowercase().contains(q) ||
                        it.subjectCode.lowercase().contains(q)
            }
        )

        binding.tvSubjectCount.text = "${filteredSubjects.size}"

        if (filteredSubjects.isEmpty()) {
            binding.rvSubjects.visibility   = View.GONE
            binding.layoutEmpty.visibility  = View.VISIBLE
            binding.tvNoSubjects.text = if (q.isNotEmpty()) "No results for \"$q\""
            else "No subjects found\nTap Add to create one"
        } else {
            binding.layoutEmpty.visibility  = View.GONE
            binding.rvSubjects.visibility   = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
        binding.rvSubjects.alpha = 0f
        binding.rvSubjects.animate().alpha(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── Add / Edit ───────────────────────────────────────────────────────────

    private fun openAddSubject(subject: Subject?) {
        startActivity(Intent(this, AddSubjectActivity::class.java).apply {
            putExtra("sessionId",    sessionId)
            putExtra("sessionName",  sessionName)
            putExtra("courseId",     courseId)
            putExtra("courseName",   courseName)
            putExtra("yearId",       yearId)
            putExtra("yearName",     yearName)
            putExtra("semesterId",   semesterId)
            putExtra("semesterName", semesterName)
            if (subject != null) {
                putExtra("subjectId", subject.id)
                putExtra("isEdit",    true)
            }
        })
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun confirmDelete(subject: Subject) {
        AlertDialog.Builder(this)
            .setTitle("Delete Subject")
            .setMessage("\"${subject.subjectName} (${subject.subjectCode})\" delete karvasho?")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                firestore.collection("Subjects").document(subject.id)
                    .update(mapOf("active" to false, "isActive" to false))
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Subject deleted", Toast.LENGTH_SHORT).show()
                        loadSubjects()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); loadSubjects() }
}