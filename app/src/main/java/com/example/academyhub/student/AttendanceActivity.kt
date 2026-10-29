package com.example.academyhub.student

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAttendanceBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore

class AttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceBinding
    private val db    = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    // Student metadata
    private var studentDocId = ""
    private var semesterId   = ""
    private var semesterName = ""
    private var courseId     = ""
    private var userPhone    = ""

    // Data model
    private data class SubjectRow(
        val subjectId:   String,
        val subjectName: String,
        val subjectCode: String,
        val percentage:  Int,
        val present:     Int,
        val absent:      Int,
        val total:       Int
    )

    private var allSubjects  = mutableListOf<SubjectRow>()
    private var filterMode   = "all"  // all / low / good

    // Color schemes per subject (round-robin)
    private val colorSchemes = listOf(
        Triple(R.drawable.bg_menu_icon_green,  "#059669", "Green"),
        Triple(R.drawable.bg_menu_icon_sky,    "#0284C7", "Sky"),
        Triple(R.drawable.bg_menu_icon_amber,  "#D97706", "Amber"),
        Triple(R.drawable.bg_menu_icon_violet, "#7C3AED", "Violet"),
        Triple(R.drawable.bg_menu_icon_rose,   "#E11D48", "Rose")
    )

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, AttendanceActivity::class.java).apply {
                putExtra("studentDocId", student.id)
                putExtra("semesterId",   student.semesterId)
                putExtra("semesterName", student.semesterName)
                putExtra("courseId",     student.courseId)
                context.startActivity(this)
            }
        }

        fun start(context: Context) {
            context.startActivity(Intent(context, AttendanceActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extract intent extras
        studentDocId = intent.getStringExtra("studentDocId") ?: prefs.getString("studentDocId", "") ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        userPhone    = prefs.getString("userPhone", "")       ?: ""

        binding.tvHeaderSemester.text = semesterName.ifEmpty { "Current Semester" }

        setupClickListeners()
        runEntranceAnimation()

        // Load attendance data
        if (semesterId.isEmpty()) loadStudentThenAttendance()
        else                      loadAttendance()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load Student Metadata (if not provided via intent)
    // ─────────────────────────────────────────────────────────────────────
    private fun loadStudentThenAttendance() {
        binding.progressBar.visibility = View.VISIBLE

        if (studentDocId.isNotEmpty()) {
            db.collection("Students").document(studentDocId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        semesterId   = doc.getString("semesterId")   ?: ""
                        semesterName = doc.getString("semesterName") ?: ""
                        courseId     = doc.getString("courseId")     ?: ""
                        binding.tvHeaderSemester.text = semesterName
                        loadAttendance()
                    } else loadByPhone()
                }
                .addOnFailureListener { loadByPhone() }
        } else loadByPhone()
    }

    private fun loadByPhone() {
        if (userPhone.isEmpty()) {
            showEmpty()
            return
        }

        val variants = if (userPhone.startsWith("+91"))
            listOf(userPhone, userPhone.removePrefix("+91"))
        else listOf(userPhone, "+91$userPhone")

        db.collection("Students").whereEqualTo("mobile", variants[0]).limit(1).get()
            .addOnSuccessListener { qs ->
                if (!qs.isEmpty) {
                    val doc = qs.documents[0]
                    studentDocId = doc.id
                    semesterId   = doc.getString("semesterId")   ?: ""
                    semesterName = doc.getString("semesterName") ?: ""
                    courseId     = doc.getString("courseId")     ?: ""
                    binding.tvHeaderSemester.text = semesterName
                    prefs.edit().putString("studentDocId", studentDocId).apply()
                    loadAttendance()
                } else showEmpty()
            }
            .addOnFailureListener { showEmpty() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Main Data Load
    // ─────────────────────────────────────────────────────────────────────
    private fun loadAttendance() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutSubjects.removeAllViews()

        // Step 1: Fetch all subjects for this semester
        db.collection("Subjects")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { subjectDocs ->
                if (subjectDocs.isEmpty) {
                    showEmpty()
                    return@addOnSuccessListener
                }

                // Build subject map: subjectId → (name, code)
                val subjectMap = mutableMapOf<String, Pair<String, String>>()
                subjectDocs.forEach { doc ->
                    val name = doc.getString("subjectName") ?: doc.getString("name") ?: "Subject"
                    val code = doc.getString("subjectCode") ?: doc.getString("code") ?: ""
                    subjectMap[doc.id] = Pair(name, code)
                }

                // Step 2: Fetch attendance record for this student
                fetchAttendanceData(subjectMap)
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                showEmpty()
            }
    }

    private fun fetchAttendanceData(subjectMap: Map<String, Pair<String, String>>) {
        val attDocId = "${studentDocId}_${semesterId}"
        db.collection("StudentAttendance").document(attDocId).get()
            .addOnSuccessListener { attDoc ->
                binding.progressBar.visibility = View.GONE

                val subjectsRaw = if (attDoc.exists())
                    (attDoc.get("subjects") as? Map<*, *>) ?: emptyMap<Any, Any>()
                else emptyMap<Any, Any>()

                // Build SubjectRow list
                allSubjects.clear()
                subjectMap.forEach { (subId, namePair) ->
                    val (name, code) = namePair
                    val raw = subjectsRaw[subId]

                    val (pct, present, absent, total) = parseAttendanceData(raw)

                    allSubjects.add(SubjectRow(
                        subjectId   = subId,
                        subjectName = name,
                        subjectCode = code,
                        percentage  = pct,
                        present     = present,
                        absent      = absent,
                        total       = total
                    ))
                }

                // Render all subjects
                if (allSubjects.isEmpty()) showEmpty()
                else {
                    binding.layoutEmpty.visibility = View.GONE
                    applyFilter("all")
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                showEmpty()
            }
    }

    private fun parseAttendanceData(raw: Any?): List<Int> {
        return when (raw) {
            is Map<*, *> -> {
                val p = (raw["present"] as? Long)?.toInt() ?: 0
                val a = (raw["absent"] as? Long)?.toInt() ?: 0
                val t = p + a
                val pc = if (t > 0) (p * 100 / t) else 0
                listOf(pc, p, a, t)
            }
            is Long   -> listOf(raw.toInt(), 0, 0, 0)
            is Double -> listOf(raw.toInt(), 0, 0, 0)
            else      -> listOf(0, 0, 0, 0)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Render Subjects (with filtering)
    // ─────────────────────────────────────────────────────────────────────
    private fun renderSubjects(subjects: List<SubjectRow>) {
        binding.layoutSubjects.removeAllViews()

        subjects.forEachIndexed { idx, row ->
            val colorScheme = colorSchemes[idx % colorSchemes.size]
            val (bgRes, colorHex, _) = colorScheme
            val tintColor = Color.parseColor(colorHex)

            // Inflate subject card using item_subject_attendance_student layout
            val card = LayoutInflater.from(this)
                .inflate(R.layout.item_subject_attendance_student, binding.layoutSubjects, false)

            // Set accent bar color
            card.findViewById<View>(R.id.subjectAccentBar).setBackgroundColor(tintColor)

            // Set icon background and initial letter
            card.findViewById<View>(R.id.subjectIconBox).setBackgroundResource(bgRes)
            val tvInitial = card.findViewById<TextView>(R.id.tvSubjectInitial)
            tvInitial.text = row.subjectName.firstOrNull()?.uppercase() ?: "S"
            tvInitial.setTextColor(tintColor)

            // Subject name and code
            card.findViewById<TextView>(R.id.tvSubjectName).text = row.subjectName
            card.findViewById<TextView>(R.id.tvSubjectCode).text = row.subjectCode.ifEmpty { "—" }

            // Attendance stats (P, A, T)
            card.findViewById<TextView>(R.id.tvPresent).text  = buildString { append("P: "); append(row.present) }
            card.findViewById<TextView>(R.id.tvAbsent).text   = buildString { append("A: "); append(row.absent) }
            card.findViewById<TextView>(R.id.tvTotalSub).text = buildString { append("T: "); append(row.total) }

            // Percentage badge with status
            val tvPct = card.findViewById<TextView>(R.id.tvSubjectPct)
            val tvStatus = card.findViewById<TextView>(R.id.tvSubjectStatus)
            val pbAttendance = card.findViewById<ProgressBar>(R.id.progressSubject)

            tvPct.text = buildString { append(row.percentage); append("%") }
            tvPct.setTextColor(getPercentageColor(row.percentage))

            tvStatus.text = getStatusText(row.percentage)
            tvStatus.setTextColor(getPercentageColor(row.percentage))

            pbAttendance.progress = 0

            // Add and animate
            card.alpha = 0f
            card.translationY = 40f
            binding.layoutSubjects.addView(card)

            val delay = idx * 80L
            card.postDelayed({
                card.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(380)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // Animate progress bar after card appears
                card.postDelayed({
                    ObjectAnimator.ofInt(pbAttendance, "progress", 0, row.percentage.coerceIn(0, 100))
                        .apply {
                            duration = 700
                            interpolator = DecelerateInterpolator()
                            start()
                        }
                }, 200)
            }, delay)

            // Click handler
            card.setOnClickListener { v -> animPress(v) { showSubjectDetail(row) } }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UI Helpers
    // ─────────────────────────────────────────────────────────────────────
    private fun getPercentageColor(pct: Int): Int = Color.parseColor(when {
        pct >= 75 -> "#059669"
        pct >= 60 -> "#D97706"
        else      -> "#E11D48"
    })

    private fun getStatusText(pct: Int): String = when {
        pct >= 85 -> "Excellent"
        pct >= 75 -> "Good"
        pct >= 60 -> "Average"
        pct > 0   -> "⚠ Low"
        else      -> "No Data"
    }

    private fun showSubjectDetail(row: SubjectRow) {
        val msg = buildString {
            append("📚 ${row.subjectName}\n")
            if (row.subjectCode.isNotEmpty()) append("Code: ${row.subjectCode}\n")
            append("Attendance: ${row.percentage}%\n")
            if (row.total > 0) {
                append("Present: ${row.present}  Absent: ${row.absent}  Total: ${row.total}\n")
            }
            if (row.percentage < 75 && row.total > 0) {
                val needed = calculateNeededClasses(row)
                append("Need $needed more classes to reach 75%\n")
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(row.subjectName)
            .setMessage(msg.trim())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun calculateNeededClasses(row: SubjectRow): Int {
        if (row.total == 0) return 0
        var n = 0
        while (n < 200) {
            val newPct = ((row.present + n) * 100) / (row.total + n)
            if (newPct >= 75) break
            n++
        }
        return n
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Filter Management
    // ─────────────────────────────────────────────────────────────────────
    private fun applyFilter(mode: String) {
        filterMode = mode

        // Update chip styles
        val chips = listOf(binding.chipAll, binding.chipLow, binding.chipGood)
        val modes = listOf("all", "low", "good")
        chips.forEachIndexed { i, chip ->
            if (modes[i] == mode) {
                chip.setBackgroundResource(R.drawable.bg_chip_active)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_inactive)
                chip.setTextColor(ContextCompat.getColor(this, android.R.color.tab_indicator_text))
            }
        }

        // Filter subjects
        val filtered = when (mode) {
            "low"  -> allSubjects.filter { it.percentage < 75 }
            "good" -> allSubjects.filter { it.percentage >= 75 }
            else   -> allSubjects
        }

        animateTextCount(binding.tvSubjectCountNum, "${filtered.size}")
        renderSubjects(filtered)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Animations
    // ─────────────────────────────────────────────────────────────────────
    private fun runEntranceAnimation() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()

        binding.layoutOverallCard.alpha = 0f
        binding.layoutOverallCard.translationY = 30f
        binding.layoutOverallCard.postDelayed({
            binding.layoutOverallCard.animate()
                .alpha(1f).translationY(0f)
                .setDuration(380)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, 180)
    }

    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }
                .start()
        }.start()
    }

    private fun animateTextCount(tv: TextView, newText: String) {
        tv.animate().alpha(0f).setDuration(150).withEndAction {
            tv.text = newText
            tv.animate().alpha(1f).setDuration(250).start()
        }.start()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { v ->
            animPress(v) { finish() }
        }
        binding.btnRefresh.setOnClickListener { v ->
            animPress(v) {
                v.animate().rotation(v.rotation + 360f).setDuration(500).start()
                loadAttendance()
            }
        }
        binding.chipAll.setOnClickListener  { v -> animPress(v) { applyFilter("all") } }
        binding.chipLow.setOnClickListener  { v -> animPress(v) { applyFilter("low") } }
        binding.chipGood.setOnClickListener { v -> animPress(v) { applyFilter("good") } }
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.layoutSubjects.visibility = View.GONE
    }
}