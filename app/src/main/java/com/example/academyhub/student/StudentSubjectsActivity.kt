package com.example.academyhub.student

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ActivityStudentSubjectsBinding
import com.example.academyhub.databinding.ItemStudentSubjectBinding
import com.example.academyhub.models.Student
import com.example.academyhub.models.Subject
import com.google.firebase.firestore.FirebaseFirestore

class StudentSubjectsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentSubjectsBinding
    private val db    = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    private var semesterId   = ""
    private var semesterName = ""
    private var courseName   = ""
    private var yearName     = ""

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, StudentSubjectsActivity::class.java).apply {
                putExtra("semesterId",   student.semesterId)
                putExtra("semesterName", student.semesterName)
                putExtra("courseName",   student.courseName)
                putExtra("yearName",     student.yearName)
                context.startActivity(this)
            }
        }

        fun start(context: Context) {
            context.startActivity(Intent(context, StudentSubjectsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentSubjectsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""

        binding.rvSubjects.layoutManager = LinearLayoutManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // Subtitle: BCA › Year 2 › Semester 3
        binding.tvSubtitle.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  ›  ")

        binding.tvSemesterName.text = semesterName.ifEmpty { "Current Semester" }

        if (semesterId.isEmpty()) loadStudentThenSubjects()
        else                      loadSubjects()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  If semesterId not passed — load from prefs/Firestore
    // ─────────────────────────────────────────────────────────────────────
    private fun loadStudentThenSubjects() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = prefs.getString("studentDocId", "") ?: ""
        if (docId.isEmpty()) { showEmpty(); return }

        db.collection("Students").document(docId).get()
            .addOnSuccessListener { doc ->
                semesterId   = doc.getString("semesterId")   ?: ""
                semesterName = doc.getString("semesterName") ?: ""
                courseName   = doc.getString("courseName")   ?: ""
                yearName     = doc.getString("yearName")     ?: ""

                binding.tvSubtitle.text = listOf(courseName, yearName, semesterName)
                    .filter { it.isNotEmpty() }.joinToString("  ›  ")
                binding.tvSemesterName.text = semesterName.ifEmpty { "Current Semester" }

                if (semesterId.isEmpty()) showEmpty()
                else loadSubjects()
            }
            .addOnFailureListener { showEmpty() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load subjects from Firestore
    //  Collection: Subjects  →  semesterId + active = true
    // ─────────────────────────────────────────────────────────────────────
    private fun loadSubjects() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("Subjects")
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snap ->
                binding.progressBar.visibility = View.GONE

                val subjects = snap.documents.mapNotNull { doc ->
                    doc.toObject(Subject::class.java)?.also { it.id = doc.id }
                }.sortedBy { it.subjectName }

                if (subjects.isEmpty()) {
                    showEmpty()
                } else {
                    binding.layoutEmpty.visibility  = View.GONE
                    binding.rvSubjects.visibility   = View.VISIBLE
                    binding.tvSubjectCount.text     = "${subjects.size} Subjects"
                    binding.rvSubjects.adapter      = SubjectAdapter(subjects)
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                showEmpty()
            }
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.rvSubjects.visibility  = View.GONE
        binding.tvSubjectCount.text    = "0 Subjects"
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Adapter
    // ─────────────────────────────────────────────────────────────────────
    inner class SubjectAdapter(
        private val list: List<Subject>
    ) : RecyclerView.Adapter<SubjectAdapter.VH>() {

        inner class VH(val b: ItemStudentSubjectBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemStudentSubjectBinding.inflate(
                LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val subject = list[position]
            val b = holder.b

            b.tvIndex.text       = "${position + 1}"
            b.tvSubjectName.text = subject.subjectName
            b.tvSubjectCode.text = subject.subjectCode.ifEmpty { "—" }

            // Staggered entrance animation
            b.root.alpha = 0f
            b.root.translationY = 24f
            b.root.postDelayed({
                b.root.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(280)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }, position * 60L)
        }
    }
}
