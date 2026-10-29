package com.example.academyhub.result

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityResultRoleBinding
import com.example.academyhub.models.*
import com.example.academyhub.ui.PickerBottomSheet
import com.google.firebase.firestore.FirebaseFirestore

class ResultRoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultRoleBinding
    private lateinit var firestore: FirebaseFirestore

    private val sessionsList  = mutableListOf<Session>()
    private val coursesList   = mutableListOf<Course>()
    private val yearsList     = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()

    private var selectedSession:  Session?      = null
    private var selectedCourse:   Course?       = null
    private var selectedYear:     AcademicYear? = null
    private var selectedSemester: Semester?     = null

    private val colorActive get() = ContextCompat.getColor(this, R.color.primary_color)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        // Steps 2-4 locked
        binding.cardStep2.alpha = 0.45f
        binding.cardStep3.alpha = 0.45f
        binding.cardStep4.alpha = 0.45f
        binding.btnProceed.visibility       = View.GONE
        binding.selectorCourse.visibility   = View.GONE
        binding.selectorYear.visibility     = View.GONE
        binding.selectorSemester.visibility = View.GONE
        binding.tvStep2Hint.visibility      = View.VISIBLE
        binding.tvStep3Hint.visibility      = View.VISIBLE
        binding.tvStep4Hint.visibility      = View.VISIBLE

        // Stagger card entrance
        listOf(
            binding.cardStep1 to 80L,
            binding.cardStep2 to 160L,
            binding.cardStep3 to 240L,
            binding.cardStep4 to 320L
        ).forEach { (card, delay) -> animateCardIn(card, delay) }

        loadSessions()
        setupClickListeners()
        binding.btnProceed.setOnClickListener { navigate() }
    }

    // ══ CLICK LISTENERS ══
    private fun setupClickListeners() {
        binding.selectorSession.setOnClickListener {
            showPicker("Session", sessionsList.map { it.sessionName },
                selectedSession?.let { s -> sessionsList.indexOf(s) } ?: -1) { onSessionPicked(it) }
        }
        binding.selectorCourse.setOnClickListener {
            showPicker("Course", coursesList.map { it.courseName },
                selectedCourse?.let { c -> coursesList.indexOf(c) } ?: -1) { onCoursePicked(it) }
        }
        binding.selectorYear.setOnClickListener {
            showPicker("Year", yearsList.map { it.yearName },
                selectedYear?.let { y -> yearsList.indexOf(y) } ?: -1) { onYearPicked(it) }
        }
        binding.selectorSemester.setOnClickListener {
            showPicker("Semester", semestersList.map { it.semesterName },
                selectedSemester?.let { s -> semestersList.indexOf(s) } ?: -1) { onSemesterPicked(it) }
        }
    }

    private fun showPicker(title: String, items: List<String>, currentIndex: Int = -1, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { Toast.makeText(this, "No $title available", Toast.LENGTH_SHORT).show(); return }
        PickerBottomSheet(title = title, items = items, selectedIndex = currentIndex, onPick = onPick)
            .show(supportFragmentManager, "picker_$title")
    }

    // ══ PICK HANDLERS ══
    private fun onSessionPicked(pos: Int) {
        selectedSession  = sessionsList[pos]
        selectedCourse   = null; selectedYear = null; selectedSemester = null
        updateSelectorText(binding.tvSessionSelected, selectedSession!!.sessionName)
        markStepDone(binding.frameStep1Circle, binding.iconStep1Done)
        resetSelector(binding.selectorCourse,   binding.tvCourseSelected,   "Tap to select course",   binding.tvStep2Hint, binding.cardStep2)
        resetSelector(binding.selectorYear,     binding.tvYearSelected,     "Tap to select year",     binding.tvStep3Hint, binding.cardStep3)
        resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep4Hint, binding.cardStep4)
        binding.btnProceed.visibility = View.GONE
        loadCourses()
    }

    private fun onCoursePicked(pos: Int) {
        selectedCourse   = coursesList[pos]
        selectedYear     = null; selectedSemester = null
        updateSelectorText(binding.tvCourseSelected, selectedCourse!!.courseName)
        markStepDone(binding.frameStep2Circle, binding.iconStep2Done)
        resetSelector(binding.selectorYear,     binding.tvYearSelected,     "Tap to select year",     binding.tvStep3Hint, binding.cardStep3)
        resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep4Hint, binding.cardStep4)
        binding.btnProceed.visibility = View.GONE
        loadYears(selectedCourse!!.id)
    }

    private fun onYearPicked(pos: Int) {
        selectedYear     = yearsList[pos]
        selectedSemester = null
        updateSelectorText(binding.tvYearSelected, selectedYear!!.yearName)
        markStepDone(binding.frameStep3Circle, binding.iconStep3Done)
        resetSelector(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester", binding.tvStep4Hint, binding.cardStep4)
        binding.btnProceed.visibility = View.GONE
        loadSemesters(selectedYear!!.id)
    }

    private fun onSemesterPicked(pos: Int) {
        selectedSemester = semestersList[pos]
        updateSelectorText(binding.tvSemesterSelected, selectedSemester!!.semesterName)
        markStepDone(binding.frameStep4Circle, binding.iconStep4Done)
        showProceedButton()
    }

    // ══ UI HELPERS ══
    private fun updateSelectorText(tv: TextView, text: String) {
        tv.text = text
        tv.setTextColor(colorActive)
    }

    private fun resetSelector(selectorRow: View, tvSelected: TextView, hintText: String, hintView: View, card: View) {
        selectorRow.visibility = View.GONE
        hintView.visibility    = View.VISIBLE
        tvSelected.text        = hintText
        tvSelected.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0.45f).apply { duration = 200; start() }
    }

    private fun revealSelector(selectorRow: View, hintView: View, card: View, dot: View) {
        hintView.visibility      = View.GONE
        selectorRow.visibility   = View.VISIBLE
        selectorRow.alpha        = 0f
        selectorRow.translationX = -18f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(selectorRow, "alpha", 0f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(selectorRow, "translationX", -18f, 0f).apply {
                    duration = 300; interpolator = DecelerateInterpolator()
                }
            )
            start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0.45f, 1f).apply { duration = 320 },
                ObjectAnimator.ofFloat(card, "translationY", 15f, 0f).apply {
                    duration = 320; interpolator = OvershootInterpolator(0.8f)
                }
            )
            start()
        }
        dot.background = ContextCompat.getDrawable(this, R.drawable.bg_dot_active)
        ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.6f, 1f).apply { duration = 350; start() }
        ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.6f, 1f).apply { duration = 350; start() }
    }

    private fun markStepDone(frame: FrameLayout, doneIcon: View) {
        val bg = frame.background
        if (bg is GradientDrawable) bg.setColor(colorActive)
        else frame.setBackgroundResource(R.drawable.bg_step_circle_active)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(frame, "scaleX", 1f, 1.22f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(frame, "scaleY", 1f, 1.22f, 1f).apply { duration = 300 }
            )
            start()
        }
        doneIcon.visibility = View.VISIBLE
        doneIcon.alpha = 0f; doneIcon.scaleX = 0.3f; doneIcon.scaleY = 0.3f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(doneIcon, "alpha",  0f,   1f).apply { duration = 380 },
                ObjectAnimator.ofFloat(doneIcon, "scaleX", 0.3f, 1f).apply { duration = 380; interpolator = OvershootInterpolator(2f) },
                ObjectAnimator.ofFloat(doneIcon, "scaleY", 0.3f, 1f).apply { duration = 380; interpolator = OvershootInterpolator(2f) }
            )
            start()
        }
    }

    private fun showProceedButton() {
        if (binding.btnProceed.visibility == View.VISIBLE) return
        binding.btnProceed.visibility = View.VISIBLE
        binding.btnProceed.alpha = 0f; binding.btnProceed.translationY = 40f; binding.btnProceed.scaleX = 0.8f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "alpha", 0f, 1f).apply { duration = 480 },
                ObjectAnimator.ofFloat(binding.btnProceed, "translationY", 40f, 0f).apply { duration = 480; interpolator = OvershootInterpolator(1.4f) },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 0.8f, 1f).apply { duration = 480; interpolator = OvershootInterpolator(1.4f) }
            )
            start()
        }
    }

    private fun animateCardIn(card: View, delay: Long) {
        card.alpha = 0f; card.translationY = 50f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, card.alpha.coerceAtLeast(0.45f)).apply { duration = 380 },
                ObjectAnimator.ofFloat(card, "translationY", 50f, 0f).apply { duration = 380; interpolator = OvershootInterpolator(0.7f) }
            )
            startDelay = delay; start()
        }
    }

    // ════ FIREBASE ════
    private fun loadSessions() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Sessions").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                sessionsList.clear()
                docs.forEach { val s = it.toObject(Session::class.java); if (s.isActive) sessionsList.add(s) }
                sessionsList.sortByDescending { it.startYear }
                if (sessionsList.isEmpty()) {
                    Toast.makeText(this, "No sessions found", Toast.LENGTH_LONG).show()
                } else {
                    binding.selectorSession.visibility = View.VISIBLE
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
                if (coursesList.isEmpty()) { Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
                revealSelector(binding.selectorCourse, binding.tvStep2Hint, binding.cardStep2, binding.dot2)
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
                if (yearsList.isEmpty()) { Toast.makeText(this, "No years found", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
                revealSelector(binding.selectorYear, binding.tvStep3Hint, binding.cardStep3, binding.dot3)
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
                if (semestersList.isEmpty()) { Toast.makeText(this, "No semesters found", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
                revealSelector(binding.selectorSemester, binding.tvStep4Hint, binding.cardStep4, binding.dot4)
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    // ════ NAVIGATE - same as original ════
    private fun navigate() {
        val session  = selectedSession  ?: return
        val course   = selectedCourse   ?: return
        val year     = selectedYear     ?: return
        val semester = selectedSemester ?: return

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 1f, 0.94f, 1f).apply { duration = 160 },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleY", 1f, 0.94f, 1f).apply { duration = 160 }
            )
            start()
        }

        binding.btnProceed.postDelayed({
            startActivity(Intent(this, ResultManagementActivity::class.java).apply {
                putExtra("sessionId",    session.id)
                putExtra("sessionName",  session.sessionName)
                putExtra("courseId",     course.id)
                putExtra("courseName",   course.courseName)
                putExtra("yearId",       year.id)
                putExtra("yearName",     year.yearName)
                putExtra("semesterId",   semester.id)
                putExtra("semesterName", semester.semesterName)
            })
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }, 150)
    }
}