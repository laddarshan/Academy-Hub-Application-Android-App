package com.example.academyhub.subject

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
import com.example.academyhub.databinding.ActivitySubjectRoleBinding
import com.example.academyhub.models.*
import com.example.academyhub.ui.PickerBottomSheet
import com.google.firebase.firestore.FirebaseFirestore

class SubjectRoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectRoleBinding
    private lateinit var firestore: FirebaseFirestore

    private val sessionsList  = mutableListOf<Map<String, String>>()
    private val coursesList   = mutableListOf<Course>()
    private val yearsList     = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()

    private var selectedSessionId   = ""; private var selectedSessionName  = ""
    private var selectedCourse:   Course?       = null
    private var selectedYear:     AcademicYear? = null
    private var selectedSemester: Semester?     = null

    private val colorActive get() = ContextCompat.getColor(this, R.color.primary_color)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        binding.btnBack.setOnClickListener { finish() }

        // Initial dim state - steps 2,3,4 locked
        binding.cardStep2.alpha = 0.45f
        binding.cardStep3.alpha = 0.45f
        binding.cardStep4.alpha = 0.45f
        binding.selectorCourse.visibility   = View.GONE
        binding.selectorYear.visibility     = View.GONE
        binding.selectorSemester.visibility = View.GONE
        binding.btnProceed.visibility       = View.GONE

        // Stagger card entrances
        listOf(60L to binding.cardStep1, 140L to binding.cardStep2,
            220L to binding.cardStep3, 300L to binding.cardStep4)
            .forEach { (d, c) -> animateCardIn(c, d) }

        loadSessions()

        binding.selectorSession.setOnClickListener  { pickSession()  }
        binding.selectorCourse.setOnClickListener   { pickCourse()   }
        binding.selectorYear.setOnClickListener     { pickYear()     }
        binding.selectorSemester.setOnClickListener { pickSemester() }
        binding.btnProceed.setOnClickListener       { navigate()     }
    }

    // ─── Pickers ───────────────────────────────────────────────────────────────

    private fun pickSession() {
        showPicker("Session", sessionsList.map { it["name"] ?: "" }) { pos ->
            val s = sessionsList[pos]
            selectedSessionId = s["id"] ?: ""; selectedSessionName = s["name"] ?: ""
            updateLabel(binding.tvSessionSelected, selectedSessionName)
            markDone(binding.frameStep1Circle, binding.iconStep1Done)
            // Reset downstream
            selectedCourse = null; selectedYear = null; selectedSemester = null
            resetSelector(binding.selectorCourse,   binding.tvCourseSelected,   "Tap to select course",    binding.tvStep2Hint, binding.cardStep2)
            resetSelector(binding.selectorYear,     binding.tvYearSelected,     "Tap to select year",      binding.tvStep3Hint, binding.cardStep3)
            resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester",  binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadCourses()
        }
    }

    private fun pickCourse() {
        showPicker("Course", coursesList.map { it.courseName }) { pos ->
            selectedCourse = coursesList[pos]; selectedYear = null; selectedSemester = null
            updateLabel(binding.tvCourseSelected, selectedCourse!!.courseName)
            markDone(binding.frameStep2Circle, binding.iconStep2Done)
            resetSelector(binding.selectorYear,     binding.tvYearSelected,     "Tap to select year",     binding.tvStep3Hint, binding.cardStep3)
            resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadYears(selectedCourse!!.id)
        }
    }

    private fun pickYear() {
        showPicker("Year", yearsList.map { it.yearName }) { pos ->
            selectedYear = yearsList[pos]; selectedSemester = null
            updateLabel(binding.tvYearSelected, selectedYear!!.yearName)
            markDone(binding.frameStep3Circle, binding.iconStep3Done)
            resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep4Hint, binding.cardStep4)
            binding.btnProceed.visibility = View.GONE
            loadSemesters(selectedYear!!.id)
        }
    }

    private fun pickSemester() {
        showPicker("Semester", semestersList.map { it.semesterName }) { pos ->
            selectedSemester = semestersList[pos]
            updateLabel(binding.tvSemesterSelected, selectedSemester!!.semesterName)
            markDone(binding.frameStep4Circle, binding.iconStep4Done)
            showProceedBtn()
        }
    }

    private fun showPicker(title: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { Toast.makeText(this, "No $title available", Toast.LENGTH_SHORT).show(); return }
        PickerBottomSheet(title, items, -1, onPick).show(supportFragmentManager, "picker_$title")
    }

    // ─── UI Helpers ────────────────────────────────────────────────────────────

    private fun updateLabel(tv: android.widget.TextView, text: String) {
        tv.text = text
        tv.setTextColor(colorActive)
    }

    private fun resetSelector(row: View, tv: android.widget.TextView, hint: String, hintView: View, card: View) {
        row.visibility = View.GONE
        hintView.visibility = View.VISIBLE
        tv.text = hint
        tv.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0.45f).apply { duration = 200; start() }
    }

    private fun revealSelector(row: View, hintView: View, card: View, dot: View) {
        hintView.visibility = View.GONE
        row.visibility = View.VISIBLE
        row.alpha = 0f; row.translationX = -16f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, "alpha", 0f, 1f).apply { duration = 260 },
                ObjectAnimator.ofFloat(row, "translationX", -16f, 0f).apply { duration = 260; interpolator = DecelerateInterpolator() }
            ); start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0.45f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(card, "translationY", 10f, 0f).apply { duration = 300; interpolator = OvershootInterpolator(0.8f) }
            ); start()
        }
        dot.background = ContextCompat.getDrawable(this, R.drawable.bg_dot_active)
        ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f).apply { duration = 300; start() }
        ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f).apply { duration = 300; start() }
    }

    private fun markDone(frame: FrameLayout, icon: View) {
        try { (frame.background as GradientDrawable).setColor(colorActive) }
        catch (e: Exception) { frame.setBackgroundResource(R.drawable.bg_step_circle_active) }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(frame, "scaleX", 1f, 1.2f, 1f).apply { duration = 260 },
                ObjectAnimator.ofFloat(frame, "scaleY", 1f, 1.2f, 1f).apply { duration = 260 }
            ); start()
        }
        icon.visibility = View.VISIBLE; icon.alpha = 0f; icon.scaleX = 0.3f; icon.scaleY = 0.3f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, "alpha", 0f, 1f).apply { duration = 340 },
                ObjectAnimator.ofFloat(icon, "scaleX", 0.3f, 1f).apply { duration = 340; interpolator = OvershootInterpolator(2f) },
                ObjectAnimator.ofFloat(icon, "scaleY", 0.3f, 1f).apply { duration = 340; interpolator = OvershootInterpolator(2f) }
            ); start()
        }
    }

    private fun showProceedBtn() {
        if (binding.btnProceed.visibility == View.VISIBLE) return
        binding.btnProceed.visibility = View.VISIBLE
        binding.btnProceed.alpha = 0f; binding.btnProceed.translationY = 30f; binding.btnProceed.scaleX = 0.88f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "alpha", 0f, 1f).apply { duration = 420 },
                ObjectAnimator.ofFloat(binding.btnProceed, "translationY", 30f, 0f).apply { duration = 420; interpolator = OvershootInterpolator(1.2f) },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 0.88f, 1f).apply { duration = 420; interpolator = OvershootInterpolator(1.2f) }
            ); start()
        }
    }

    private fun animateCardIn(card: View, delay: Long) {
        card.alpha = 0f; card.translationY = 40f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, card.alpha.coerceAtLeast(0.45f)).apply { duration = 340 },
                ObjectAnimator.ofFloat(card, "translationY", 40f, 0f).apply { duration = 340; interpolator = OvershootInterpolator(0.6f) }
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
                    binding.tvStep1Hint.visibility    = View.GONE
                    binding.selectorSession.visibility = View.VISIBLE
                } else {
                    binding.tvStep1Hint.text = "No active sessions found"
                    Toast.makeText(this, "No sessions found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading sessions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCourses() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                coursesList.clear()
                docs.forEach { val c = it.toObject(Course::class.java); if (c.isActive) coursesList.add(c) }
                coursesList.sortBy { it.courseName }
                if (coursesList.isNotEmpty()) revealSelector(binding.selectorCourse, binding.tvStep2Hint, binding.cardStep2, binding.dot2)
                else Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadYears(courseId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Years").whereEqualTo("courseId", courseId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                yearsList.clear()
                docs.forEach { val y = it.toObject(AcademicYear::class.java); if (y.isActive) yearsList.add(y) }
                yearsList.sortBy { it.yearNumber }
                if (yearsList.isNotEmpty()) revealSelector(binding.selectorYear, binding.tvStep3Hint, binding.cardStep3, binding.dot3)
                else Toast.makeText(this, "No years found for this course", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadSemesters(yearId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Semesters").whereEqualTo("yearId", yearId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                semestersList.clear()
                docs.forEach { val s = it.toObject(Semester::class.java); if (s.isActive) semestersList.add(s) }
                semestersList.sortBy { it.semesterNumber }
                if (semestersList.isNotEmpty()) revealSelector(binding.selectorSemester, binding.tvStep4Hint, binding.cardStep4, binding.dot4)
                else Toast.makeText(this, "No semesters found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    // ─── Navigate ──────────────────────────────────────────────────────────────

    private fun navigate() {
        val course   = selectedCourse   ?: return
        val year     = selectedYear     ?: return
        val semester = selectedSemester ?: return

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
            ); start()
        }
        binding.btnProceed.postDelayed({
            startActivity(Intent(this, SubjectListActivity::class.java).apply {
                putExtra("sessionId",    selectedSessionId)
                putExtra("sessionName",  selectedSessionName)
                putExtra("courseId",     course.id)
                putExtra("courseName",   course.courseName)
                putExtra("yearId",       year.id)
                putExtra("yearName",     year.yearName)
                putExtra("semesterId",   semester.id)
                putExtra("semesterName", semester.semesterName)
            })
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }, 120)
    }
}