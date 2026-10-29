package com.example.academyhub.subject

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAddSubjectBinding
import com.example.academyhub.models.Subject
import com.google.firebase.firestore.FirebaseFirestore

class AddSubjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSubjectBinding
    private lateinit var firestore: FirebaseFirestore

    private var isEditMode   = false
    private var subjectId    = ""

    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        isEditMode   = intent.getBooleanExtra("isEdit", false)
        subjectId    = intent.getStringExtra("subjectId")    ?: ""
        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvGroupBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        if (isEditMode) {
            binding.tvTitle.text     = "Edit Subject"
            binding.tvSaveLabel.text = "Update Subject"
            loadSubjectData()
        }

        // Entrance animations
        binding.btnSave.alpha = 0f; binding.btnSave.translationY = 24f
        binding.btnSave.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f)).start()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            pulseBtn()
            binding.btnSave.postDelayed({ if (isEditMode) updateSubject() else saveSubject() }, 130)
        }
    }

    // ─── Load for edit ────────────────────────────────────────────────────────

    private fun loadSubjectData() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Subjects").document(subjectId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                if (doc.exists()) {
                    val s = doc.toObject(Subject::class.java)
                    s?.let {
                        binding.etSubjectName.setText(it.subjectName)
                        binding.etSubjectCode.setText(it.subjectCode)
                    }
                }
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private fun saveSubject() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        val id = firestore.collection("Subjects").document().id
        val subject = Subject(
            id           = id,
            sessionId    = sessionId,    sessionName  = sessionName,
            courseId     = courseId,     courseName   = courseName,
            yearId       = yearId,       yearName     = yearName,
            semesterId   = semesterId,   semesterName = semesterName,
            subjectName  = binding.etSubjectName.text.toString().trim(),
            subjectCode  = binding.etSubjectCode.text.toString().trim().uppercase(),
            isActive     = true,
            createdAt    = System.currentTimeMillis(),
            updatedAt    = System.currentTimeMillis()
        )
        firestore.collection("Subjects").document(id).set(subject)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Subject added!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    private fun updateSubject() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        val updates = mapOf(
            "subjectName" to binding.etSubjectName.text.toString().trim(),
            "subjectCode" to binding.etSubjectCode.text.toString().trim().uppercase(),
            "updatedAt"   to System.currentTimeMillis()
        )
        firestore.collection("Subjects").document(subjectId).update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Subject updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Validate ─────────────────────────────────────────────────────────────

    private fun validate(): Boolean {
        val name = binding.etSubjectName.text.toString().trim()
        val code = binding.etSubjectCode.text.toString().trim()
        if (name.isEmpty()) {
            binding.etSubjectName.error = "Enter subject name"
            binding.etSubjectName.requestFocus(); return false
        }
        if (code.isEmpty()) {
            binding.etSubjectCode.error = "Enter subject code"
            binding.etSubjectCode.requestFocus(); return false
        }
        return true
    }

    private fun pulseBtn() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnSave, "scaleX", 1f, 0.94f, 1f).apply { duration = 130; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(binding.btnSave, "scaleY", 1f, 0.94f, 1f).apply { duration = 130; interpolator = OvershootInterpolator() }
            ); start()
        }
    }
}
