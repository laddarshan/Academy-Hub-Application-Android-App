package com.example.academyhub.fees

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
import com.example.academyhub.databinding.ActivityFeesRoleBinding
import com.example.academyhub.models.AcademicYear
import com.example.academyhub.models.Course
import com.example.academyhub.models.Semester
import com.example.academyhub.ui.PickerBottomSheet

import com.google.firebase.firestore.FirebaseFirestore

class FeesRoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeesRoleBinding
    private lateinit var firestore: FirebaseFirestore

    private val coursesList   = mutableListOf<Course>()
    private val yearsList     = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()

    private var selectedCourse:   Course?       = null
    private var selectedYear:     AcademicYear? = null
    private var selectedSemester: Semester?     = null

    // "structure" = Fees Structure, "student_fees" = Student Fees
    private var mode = "student_fees"

    private val colorActive get() = ContextCompat.getColor(this, R.color.primary_color)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeesRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        mode = intent.getStringExtra("mode") ?: "student_fees"

        if (mode == "structure") {
            binding.tvHeaderTitle.text    = "Fees Structure"
            binding.tvHeaderSubtitle.text = "Select course and semester"
            binding.btnProceed.text       = "Set Fees Structure"
        } else {
            binding.tvHeaderTitle.text    = "Student Fees"
            binding.tvHeaderSubtitle.text = "Select group to view fees"
            binding.btnProceed.text       = "View Student Fees"
        }

        // Initial states
        binding.cardStep2.alpha = 0.45f
        binding.cardStep3.alpha = 0.45f
        binding.selectorYear.visibility     = View.GONE
        binding.selectorSemester.visibility = View.GONE
        binding.btnProceed.visibility       = View.GONE

        // Card entrance animations
        listOf(60L to binding.cardStep1, 140L to binding.cardStep2, 220L to binding.cardStep3)
            .forEach { (d, c) -> animateCardIn(c, d) }

        loadCourses()

        binding.btnBack.setOnClickListener { finish() }
        binding.selectorCourse.setOnClickListener   { pickCourse()   }
        binding.selectorYear.setOnClickListener     { pickYear()     }
        binding.selectorSemester.setOnClickListener { pickSemester() }
        binding.btnProceed.setOnClickListener       { navigate()     }
    }

    // ─── Pickers ───────────────────────────────────────────────────────────────

    private fun pickCourse() {
        showPicker("Course", coursesList.map { it.courseName }) { pos ->
            selectedCourse = coursesList[pos]; selectedYear = null; selectedSemester = null
            updateLabel(binding.tvCourseSelected, selectedCourse!!.courseName)
            markDone(binding.frameStep1Circle, binding.iconStep1Done)
            resetStep(binding.selectorYear,     binding.tvYearSelected,     "Tap to select year",
                      binding.tvStep2Hint, binding.cardStep2)
            resetStep(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester",
                      binding.tvStep3Hint, binding.cardStep3)
            binding.btnProceed.visibility = View.GONE
            loadYears(selectedCourse!!.id)
        }
    }

    private fun pickYear() {
        showPicker("Year", yearsList.map { it.yearName }) { pos ->
            selectedYear = yearsList[pos]; selectedSemester = null
            updateLabel(binding.tvYearSelected, selectedYear!!.yearName)
            markDone(binding.frameStep2Circle, binding.iconStep2Done)
            resetStep(binding.selectorSemester, binding.tvSemesterSelected, "Tap to select semester",
                      binding.tvStep3Hint, binding.cardStep3)
            binding.btnProceed.visibility = View.GONE
            loadSemesters(selectedYear!!.id)
        }
    }

    private fun pickSemester() {
        showPicker("Semester", semestersList.map { it.semesterName }) { pos ->
            selectedSemester = semestersList[pos]
            updateLabel(binding.tvSemesterSelected, selectedSemester!!.semesterName)
            markDone(binding.frameStep3Circle, binding.iconStep3Done)
            showProceedBtn()
        }
    }

    private fun showPicker(title: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) {
            Toast.makeText(this, "No $title available", Toast.LENGTH_SHORT).show(); return
        }
        PickerBottomSheet(title, items, -1, onPick).show(supportFragmentManager, "picker_$title")
    }

    // ─── UI Helpers ────────────────────────────────────────────────────────────

    private fun updateLabel(tv: android.widget.TextView, text: String) {
        tv.text = text
        tv.setTextColor(colorActive)
    }

    private fun resetStep(row: View, tv: android.widget.TextView, hint: String,
                          hintView: android.widget.TextView, card: View) {
        row.visibility  = View.GONE
        hintView.visibility = View.VISIBLE
        tv.text = hint
        tv.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0.45f).apply { duration = 200; start() }
    }

    private fun revealStep(row: View, hintView: View, card: View, dot: View) {
        hintView.visibility = View.GONE
        row.visibility = View.VISIBLE
        row.alpha = 0f; row.translationX = -16f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, "alpha", 0f, 1f).apply { duration = 260 },
                ObjectAnimator.ofFloat(row, "translationX", -16f, 0f)
                    .apply { duration = 260; interpolator = DecelerateInterpolator() }
            ); start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0.45f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(card, "translationY", 10f, 0f)
                    .apply { duration = 300; interpolator = OvershootInterpolator(0.8f) }
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
                ObjectAnimator.ofFloat(icon, "scaleX", 0.3f, 1f)
                    .apply { duration = 340; interpolator = OvershootInterpolator(2f) },
                ObjectAnimator.ofFloat(icon, "scaleY", 0.3f, 1f)
                    .apply { duration = 340; interpolator = OvershootInterpolator(2f) }
            ); start()
        }
    }

    private fun showProceedBtn() {
        if (binding.btnProceed.visibility == View.VISIBLE) return
        binding.btnProceed.visibility = View.VISIBLE
        binding.btnProceed.alpha = 0f; binding.btnProceed.translationY = 30f
        binding.btnProceed.scaleX = 0.88f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnProceed, "alpha", 0f, 1f).apply { duration = 420 },
                ObjectAnimator.ofFloat(binding.btnProceed, "translationY", 30f, 0f)
                    .apply { duration = 420; interpolator = OvershootInterpolator(1.2f) },
                ObjectAnimator.ofFloat(binding.btnProceed, "scaleX", 0.88f, 1f)
                    .apply { duration = 420; interpolator = OvershootInterpolator(1.2f) }
            ); start()
        }
    }

    private fun animateCardIn(card: View, delay: Long) {
        card.alpha = 0f; card.translationY = 40f
        AnimatorSet().apply {
            val targetAlpha = if (card == binding.cardStep1) 1f else 0.45f
            playTogether(
                ObjectAnimator.ofFloat(card, "alpha", 0f, targetAlpha).apply { duration = 340 },
                ObjectAnimator.ofFloat(card, "translationY", 40f, 0f)
                    .apply { duration = 340; interpolator = OvershootInterpolator(0.6f) }
            ); startDelay = delay; start()
        }
    }

    // ─── Firestore ─────────────────────────────────────────────────────────────

    private fun loadCourses() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                coursesList.clear()
                docs.forEach { val c = it.toObject(Course::class.java); if (c.isActive) coursesList.add(c) }
                coursesList.sortBy { it.courseName }
                if (coursesList.isNotEmpty()) {
                    binding.tvStep1Hint.visibility    = View.GONE
                    binding.selectorCourse.visibility = View.VISIBLE
                } else {
                    binding.tvStep1Hint.text = "No courses found"
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading courses", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadYears(courseId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Years").whereEqualTo("courseId", courseId)
            .whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                yearsList.clear()
                docs.forEach { val y = it.toObject(AcademicYear::class.java); if (y.isActive) yearsList.add(y) }
                yearsList.sortBy { it.yearNumber }
                if (yearsList.isNotEmpty())
                    revealStep(binding.selectorYear, binding.tvStep2Hint, binding.cardStep2, binding.dot2)
                else Toast.makeText(this, "No years found", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun loadSemesters(yearId: String) {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Semesters").whereEqualTo("yearId", yearId)
            .whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                semestersList.clear()
                docs.forEach { val s = it.toObject(Semester::class.java); if (s.isActive) semestersList.add(s) }
                semestersList.sortBy { it.semesterNumber }
                if (semestersList.isNotEmpty())
                    revealStep(binding.selectorSemester, binding.tvStep3Hint, binding.cardStep3, binding.dot3)
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
            val extras = mapOf(
                "courseId"     to course.id,
                "courseName"   to course.courseName,
                "yearId"       to year.id,
                "yearName"     to year.yearName,
                "semesterId"   to semester.id,
                "semesterName" to semester.semesterName
            )
            val intent = if (mode == "structure")
                Intent(this, FeesStructureActivity::class.java)
            else
                Intent(this, StudentFeesListActivity::class.java)

            extras.forEach { (k, v) -> intent.putExtra(k, v) }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }, 120)
    }
}
