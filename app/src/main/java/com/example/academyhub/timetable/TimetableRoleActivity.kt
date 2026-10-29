package com.example.academyhub.timetable

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityTimetableRoleBinding
import com.example.academyhub.models.*
import com.example.academyhub.ui.PickerBottomSheet
import com.google.firebase.firestore.FirebaseFirestore

class TimetableRoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimetableRoleBinding
    private lateinit var firestore: FirebaseFirestore

    private val sessionsList  = mutableListOf<Map<String, String>>()
    private val coursesList   = mutableListOf<Course>()
    private val semestersList = mutableListOf<Semester>()
    private val batchesList   = mutableListOf<Batch>()

    private var selectedSessionId   = ""; private var selectedSessionName  = ""
    private var selectedCourse:   Course?   = null
    private var selectedSemester: Semester? = null
    private var selectedBatch:    Batch?    = null

    private val colorActive get() = ContextCompat.getColor(this, R.color.primary_color)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimetableRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        // Initial dim state
        binding.cardStep2.alpha = 0.4f
        binding.cardStep3.alpha = 0.4f
        binding.cardStep4.alpha = 0.4f
        binding.btnProceed.visibility    = View.GONE
        binding.selectorCourse.visibility   = View.GONE
        binding.selectorSemester.visibility = View.GONE
        binding.selectorBatch.visibility    = View.GONE

        listOf(60L to binding.cardStep1, 140L to binding.cardStep2,
            220L to binding.cardStep3, 300L to binding.cardStep4)
            .forEach { (d, c) -> animateCardIn(c, d) }

        loadSessions()

        binding.selectorSession.setOnClickListener  { pickSession()  }
        binding.selectorCourse.setOnClickListener   { pickCourse()   }
        binding.selectorSemester.setOnClickListener { pickSemester() }
        binding.selectorBatch.setOnClickListener    { pickBatch()    }
        binding.btnProceed.setOnClickListener       { navigate()     }
    }

    // ─── Pickers ───────────────────────────────────────────────────────────────

    private fun pickSession() {
        showPicker("Session", sessionsList.map { it["name"] ?: "" }) { pos ->
            val s = sessionsList[pos]
            selectedSessionId = s["id"] ?: ""; selectedSessionName = s["name"] ?: ""
            updateLabel(binding.tvSessionSelected, selectedSessionName)
            markDone(binding.frameStep1Circle, binding.iconStep1Done)
            // reset downstream
            selectedCourse = null; selectedSemester = null; selectedBatch = null
            resetStep(binding.selectorCourse,   binding.tvCourseSelected,   "Tap to select course",   binding.tvStep2Hint, binding.cardStep2)
            resetStep(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep3Hint, binding.cardStep3)
            resetStep(binding.selectorBatch,    binding.tvBatchSelected,    "Tap to select batch",    binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadCourses()
        }
    }

    private fun pickCourse() {
        showPicker("Course", coursesList.map { it.courseName }) { pos ->
            selectedCourse = coursesList[pos]; selectedSemester = null; selectedBatch = null
            updateLabel(binding.tvCourseSelected, selectedCourse!!.courseName)
            markDone(binding.frameStep2Circle, binding.iconStep2Done)
            resetStep(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep3Hint, binding.cardStep3)
            resetStep(binding.selectorBatch,    binding.tvBatchSelected,    "Tap to select batch",    binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadSemesters(selectedCourse!!.id)
        }
    }

    private fun pickSemester() {
        showPicker("Semester", semestersList.map { it.semesterName }) { pos ->
            selectedSemester = semestersList[pos]; selectedBatch = null
            updateLabel(binding.tvSemesterSelected, selectedSemester!!.semesterName)
            markDone(binding.frameStep3Circle, binding.iconStep3Done)
            resetStep(binding.selectorBatch, binding.tvBatchSelected, "Tap to select batch", binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadBatches()
        }
    }

    private fun pickBatch() {
        showPicker("Batch", batchesList.map { "${it.batchName} (${it.startTime} - ${it.endTime})" }) { pos ->
            selectedBatch = batchesList[pos]
            updateLabel(binding.tvBatchSelected, "${selectedBatch!!.batchName} (${selectedBatch!!.startTime} - ${selectedBatch!!.endTime})")
            markDone(binding.frameStep4Circle, binding.iconStep4Done)
            showProceedBtn()
        }
    }

    private fun showPicker(title: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { Toast.makeText(this, "No $title available", Toast.LENGTH_SHORT).show(); return }
        PickerBottomSheet(title, items, -1, onPick).show(supportFragmentManager, "picker_$title")
    }

    // ─── UI helpers ────────────────────────────────────────────────────────────

    private fun updateLabel(tv: android.widget.TextView, text: String) {
        tv.text = text; tv.setTextColor(colorActive)
    }

    private fun resetStep(row: View, tv: android.widget.TextView, hint: String, hintView: View, card: View) {
        row.visibility = View.GONE; hintView.visibility = View.VISIBLE
        tv.text = hint; tv.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0.4f).apply { duration = 200; start() }
    }

    private fun revealStep(row: View, hintView: View, card: View, dot: View) {
        hintView.visibility = View.GONE; row.visibility = View.VISIBLE
        row.alpha = 0f; row.translationX = -20f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, "alpha", 0f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(row, "translationX", -20f, 0f).apply { duration = 280; interpolator = DecelerateInterpolator() }
            ); start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0.4f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(card, "translationY", 12f, 0f).apply { duration = 300; interpolator = OvershootInterpolator(0.8f) }
            ); start()
        }
        dot.background = ContextCompat.getDrawable(this, R.drawable.bg_dot_active)
        ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f).apply { duration = 320; start() }
        ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f).apply { duration = 320; start() }
    }

    private fun markDone(frame: FrameLayout, icon: View) {
        (frame.background as? GradientDrawable)?.setColor(colorActive)
            ?: frame.setBackgroundResource(R.drawable.bg_step_circle_active)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(frame, "scaleX", 1f, 1.2f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(frame, "scaleY", 1f, 1.2f, 1f).apply { duration = 280 }
            ); start()
        }
        icon.visibility = View.VISIBLE; icon.alpha = 0f; icon.scaleX = 0.3f; icon.scaleY = 0.3f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f).apply { duration = 360 },
                ObjectAnimator.ofFloat(icon, "scaleX", 0.3f, 1f).apply { duration = 360; interpolator = OvershootInterpolator(2f) },
                ObjectAnimator.ofFloat(icon, "scaleY", 0.3f, 1f).apply { duration = 360; interpolator = OvershootInterpolator(2f) }
            ); start()
        }
    }

    private fun showProceedBtn() {
        if (binding.btnProceed.visibility == View.VISIBLE) return
        binding.btnProceed.visibility = View.VISIBLE
        binding.btnProceed.alpha = 0f; binding.btnProceed.translationY = 36f; binding.btnProceed.scaleX = 0.85f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "alpha", 0f, 1f).apply { duration = 440 },
                ObjectAnimator.ofFloat(binding.btnProceed, "translationY", 36f, 0f).apply { duration = 440; interpolator = OvershootInterpolator(1.2f) },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 0.85f, 1f).apply { duration = 440; interpolator = OvershootInterpolator(1.2f) }
            ); start()
        }
    }

    private fun animateCardIn(card: View, delay: Long) {
        card.alpha = 0f; card.translationY = 48f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, card.alpha.coerceAtLeast(0.4f)).apply { duration = 360 },
                ObjectAnimator.ofFloat(card, "translationY", 48f, 0f).apply { duration = 360; interpolator = OvershootInterpolator(0.6f) }
            ); startDelay = delay; start()
        }
    }

    // ─── Firestore ─────────────────────────────────────────────────────────────

    private fun loadSessions() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Sessions").get()  // Load all sessions - filter removed
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                sessionsList.clear()
                for (doc in docs) {
                    val name = doc.getString("sessionName") ?: doc.getString("name") ?: ""
                    if (name.isNotEmpty()) sessionsList.add(mapOf("id" to doc.id, "name" to name))
                }
                sessionsList.sortByDescending { it["name"] }
                if (sessionsList.isNotEmpty()) {
                    binding.tvStep1Hint.visibility   = View.GONE
                    binding.selectorSession.visibility = View.VISIBLE
                } else Toast.makeText(this, "No sessions found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadCourses() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                coursesList.clear()
                docs.forEach { val c = it.toObject(Course::class.java); if (c.isActive) coursesList.add(c) }
                coursesList.sortBy { it.courseName }
                if (coursesList.isNotEmpty()) revealStep(binding.selectorCourse, binding.tvStep2Hint, binding.cardStep2, binding.dot2)
                else Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadSemesters(courseId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Semesters").whereEqualTo("courseId", courseId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                semestersList.clear()
                docs.forEach { val s = it.toObject(Semester::class.java); if (s.isActive) semestersList.add(s) }
                semestersList.sortBy { it.semesterNumber }
                if (semestersList.isNotEmpty()) revealStep(binding.selectorSemester, binding.tvStep3Hint, binding.cardStep3, binding.dot3)
                else Toast.makeText(this, "No semesters for this course", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadBatches() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Batches").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                batchesList.clear()
                docs.forEach { val b = it.toObject(Batch::class.java); if (b.isActive) batchesList.add(b) }
                batchesList.sortBy { it.batchName }
                if (batchesList.isNotEmpty()) revealStep(binding.selectorBatch, binding.tvStep4Hint, binding.cardStep4, binding.dot4)
                else Toast.makeText(this, "No batches found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    // ─── Navigate ──────────────────────────────────────────────────────────────

    private fun navigate() {
        val course = selectedCourse ?: return; val semester = selectedSemester ?: return; val batch = selectedBatch ?: return
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 1f, 0.93f, 1f).apply { duration = 150 },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleY", 1f, 0.93f, 1f).apply { duration = 150 }
            ); start()
        }
        binding.btnProceed.postDelayed({
            startActivity(Intent(this, TimetableListActivity::class.java).apply {
                putExtra("sessionId",    selectedSessionId)
                putExtra("sessionName",  selectedSessionName)
                putExtra("courseId",     course.id)
                putExtra("courseName",   course.courseName)
                putExtra("semesterId",   semester.id)
                putExtra("semesterName", semester.semesterName)
                putExtra("batchId",      batch.id)
                putExtra("batchName",    batch.batchName)
            })
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }, 140)
    }
}