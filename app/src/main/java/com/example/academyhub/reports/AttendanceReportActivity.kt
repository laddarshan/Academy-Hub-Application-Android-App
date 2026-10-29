package com.example.academyhub.reports

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAttendanceReportBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class AttendanceReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceReportBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var filterHelper: ReportFilterHelper

    data class StudentAttRow(
        val studentName: String, val rollNo: String, val course: String,
        val overall: Int, val subjectPcts: LinkedHashMap<String, Int> = linkedMapOf()
    )
    data class SubjectAvgRow(val subjectName: String, val avgPct: Double, val count: Int)

    private val studentRows  = mutableListOf<StudentAttRow>()
    private val subjectRows  = mutableListOf<SubjectAvgRow>()
    private val subjectNames = mutableListOf<String>() // ordered

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterHelper = ReportFilterHelper(
            context = this, db = db,
            spinnerCourse   = binding.spinnerCourse,
            spinnerSession  = binding.spinnerSession,
            spinnerYear     = binding.spinnerYear,
            spinnerSemester = binding.spinnerSemester,
            onFilterChanged = {}
        )
        filterHelper.loadFilters()
        binding.btnBack.setOnClickListener { finish() }
        setupSwipeRefresh()

        binding.btnGenerateReport.setOnClickListener {
            animateBtn(it); it.postDelayed({ generateReport() }, 120)
        }
        binding.btnExportPdf.setOnClickListener {
            animateBtn(it); it.postDelayed({ exportPdf() }, 120)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"), Color.parseColor("#26A69A"), Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { generateReport(fromSwipe = true) }
    }

    private fun animateBtn(v: View) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    private fun generateReport(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        binding.summaryCard.visibility      = View.GONE
        binding.btnExportPdf.visibility     = View.GONE
        binding.subjectTableCard.visibility = View.GONE
        studentRows.clear(); subjectRows.clear(); subjectNames.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val studentsSnap   = filterHelper.buildStudentsQuery().get().await()
                val studentInfoMap = mutableMapOf<String, Triple<String, String, String>>()
                for (doc in studentsSnap) {
                    studentInfoMap[doc.id] = Triple(
                        doc.getString("name") ?: "",
                        doc.getString("rollNumber") ?: "",
                        doc.getString("courseName") ?: ""
                    )
                }

                val subjectNameMap = mutableMapOf<String, String>()
                db.collection("Subjects").get().await().forEach { doc ->
                    subjectNameMap[doc.id] = doc.getString("subjectName") ?: doc.id
                }

                val attSnap       = filterHelper.buildAttendanceQuery().get().await()
                val subjectTotals = mutableMapOf<String, MutableList<Int>>()

                for (doc in attSnap) {
                    val studentId = doc.getString("studentId") ?: continue
                    val info      = studentInfoMap[studentId] ?: continue
                    val overall   = (doc.getLong("overall") ?: 0L).toInt()
                    val rawMap    = doc.get("subjects") as? Map<*, *> ?: emptyMap<Any, Any>()

                    val subPcts = linkedMapOf<String, Int>()
                    rawMap.forEach { (k, v) ->
                        val pct   = when (v) { is Long -> v.toInt(); is Int -> v; is Double -> v.toInt(); else -> 0 }
                        val sName = subjectNameMap[k.toString()] ?: k.toString()
                        subPcts[sName] = pct
                        subjectTotals.getOrPut(k.toString()) { mutableListOf() }.add(pct)
                    }
                    studentRows.add(StudentAttRow(info.first, info.second, info.third, overall, subPcts))
                }
                studentRows.sortBy { it.overall }

                subjectTotals.forEach { (subId, pcts) ->
                    val name = subjectNameMap[subId] ?: subId
                    if (!subjectNames.contains(name)) subjectNames.add(name)
                    subjectRows.add(SubjectAvgRow(name, pcts.average(), pcts.size))
                }
                subjectNames.sort()
                subjectRows.sortBy { it.subjectName }

                val above75    = studentRows.count { it.overall >= 75 }
                val below75    = studentRows.count { it.overall < 75 }
                val avgOverall = if (studentRows.isNotEmpty()) studentRows.map { it.overall }.average() else 0.0

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.tvFilterInfo.text         = filterHelper.filterText()
                    binding.tvFilterInfo.visibility   = View.VISIBLE

                    animCount(binding.tvTotalStudents, studentRows.size)
                    animCount(binding.tvAbove75, above75)
                    animCount(binding.tvBelow75, below75)
                    binding.tvAvgAttendance.text = String.format("%.1f%%", avgOverall)

                    binding.summaryCard.alpha      = 0f
                    binding.summaryCard.visibility = View.VISIBLE
                    binding.summaryCard.animate().alpha(1f).setDuration(300)
                        .setInterpolator(DecelerateInterpolator()).start()

                    buildStudentTable()
                    buildSubjectTable()

                    if (studentRows.isNotEmpty()) {
                        binding.btnExportPdf.visibility = View.VISIBLE
                        binding.btnExportPdf.alpha      = 0f
                        binding.btnExportPdf.animate().alpha(1f).setDuration(250).start()
                    } else {
                        Toast.makeText(this@AttendanceReportActivity,
                            "Koi data nathi maliyo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@AttendanceReportActivity,
                        "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun animCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 600; addUpdateListener { tv.text = "${it.animatedValue}" }; start()
        }
    }

    // ── Build tables using LinearLayout rows (NOT TableLayout+weight) ─────────
    // This is the KEY fix — weight inside HorizontalScrollView does NOT work.
    // We use LinearLayout with fixed column widths instead.

    private val COL_NAME    by lazy { dp(130) }
    private val COL_ROLL    by lazy { dp(70) }
    private val COL_COURSE  by lazy { dp(80) }
    private val COL_OVERALL by lazy { dp(75) }
    private val COL_SUBJECT by lazy { dp(90) }

    private fun buildStudentTable() {
        binding.studentTableLayout.removeAllViews()
        binding.studentTableLayout.orientation = LinearLayout.VERTICAL

        // Header row
        val headers = mutableListOf("Name", "Roll", "Course", "Overall%")
        headers.addAll(subjectNames)
        val colWidths = mutableListOf(COL_NAME, COL_ROLL, COL_COURSE, COL_OVERALL)
        subjectNames.forEach { _ -> colWidths.add(COL_SUBJECT) }

        binding.studentTableLayout.addView(makeHeaderRow(headers, colWidths))
        binding.studentTableLayout.addView(divider())

        studentRows.forEachIndexed { i, r ->
            val color = when { r.overall >= 75 -> "#2E7D32"; r.overall >= 60 -> "#E65100"; else -> "#B71C1C" }
            val cols  = mutableListOf(r.studentName, r.rollNo, r.course, "${r.overall}%")
            subjectNames.forEach { sn ->
                cols.add(if (r.subjectPcts.containsKey(sn)) "${r.subjectPcts[sn]}%" else "-")
            }
            // highlight overall% col (index 3)
            val highlights = Array(cols.size) { if (it == 3) color else null }
            binding.studentTableLayout.addView(makeDataRow(cols, colWidths, highlights, i))
            binding.studentTableLayout.addView(divider())
        }
    }

    private fun buildSubjectTable() {
        if (subjectRows.isEmpty()) { binding.subjectTableCard.visibility = View.GONE; return }
        binding.subjectTableCard.visibility = View.VISIBLE
        binding.subjectTableLayout.removeAllViews()
        binding.subjectTableLayout.orientation = LinearLayout.VERTICAL

        val colW = listOf(dp(160), dp(80), dp(80))
        binding.subjectTableLayout.addView(makeHeaderRow(listOf("Subject", "Avg %", "Students"), colW))
        binding.subjectTableLayout.addView(divider())

        subjectRows.forEachIndexed { i, r ->
            val color = when { r.avgPct >= 75 -> "#2E7D32"; r.avgPct >= 60 -> "#E65100"; else -> "#B71C1C" }
            val cols  = listOf(r.subjectName, String.format("%.1f%%", r.avgPct), "${r.count}")
            val hi    = arrayOf<String?>(null, color, null)
            binding.subjectTableLayout.addView(makeDataRow(cols, colW, hi, i))
            binding.subjectTableLayout.addView(divider())
        }
    }

    private fun makeHeaderRow(cols: List<String>, widths: List<Int>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
        }
        cols.forEachIndexed { i, col ->
            row.addView(TextView(this).apply {
                text = col; textSize = 11.5f; setPadding(dp(10), dp(9), dp(10), dp(9))
                setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(widths[i], ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        return row
    }

    private fun makeDataRow(cols: List<String>, widths: List<Int>,
                            highlights: Array<String?>, rowIdx: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Dark-mode safe: use theme colorSurface for even, light teal tint for odd
            val bgColor = if (rowIdx % 2 == 0)
                resolveColor(com.google.android.material.R.attr.colorSurface)
            else
                blendWithSurface(Color.parseColor("#075E54"), 0.04f)
            setBackgroundColor(bgColor)
        }
        cols.forEachIndexed { i, col ->
            val tc = highlights[i]
            row.addView(TextView(this).apply {
                text = col; textSize = 11f; setPadding(dp(10), dp(8), dp(10), dp(8))
                if (tc != null) {
                    setTextColor(Color.parseColor(tc))
                    setTypeface(null, Typeface.BOLD)
                } else {
                    setTextColor(resolveColor(android.R.attr.textColorPrimary))
                }
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(widths[i], ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        return row
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(resolveColor(android.R.attr.colorControlHighlight))
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private fun blendWithSurface(color: Int, alpha: Float): Int {
        val surface = resolveColor(com.google.android.material.R.attr.colorSurface)
        val r = (Color.red(surface)   * (1 - alpha) + Color.red(color)   * alpha).toInt()
        val g = (Color.green(surface) * (1 - alpha) + Color.green(color) * alpha).toInt()
        val b = (Color.blue(surface)  * (1 - alpha) + Color.blue(color)  * alpha).toInt()
        return Color.rgb(r, g, b)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun exportPdf() {
        val headers = mutableListOf("Name", "Roll", "Course", "Overall%")
        headers.addAll(subjectNames)

        val intent = PdfReportHelper.generatePdf(
            context      = this,
            reportTitle  = "Attendance Report",
            subtitle     = filterHelper.filterText(),
            summaryItems = listOf(
                "Session"        to filterHelper.getCurrentFilter().sessionName,
                "Filter"         to filterHelper.filterText(),
                "Total Students" to binding.tvTotalStudents.text.toString(),
                "Average"        to binding.tvAvgAttendance.text.toString(),
                "Above 75%"      to binding.tvAbove75.text.toString(),
                "Below 75%"      to binding.tvBelow75.text.toString()
            ),
            tableHeaders = headers,
            tableRows = studentRows.map { r ->
                val cols = mutableListOf(r.studentName, r.rollNo, r.course, "${r.overall}%")
                subjectNames.forEach { sn ->
                    cols.add(if (r.subjectPcts.containsKey(sn)) "${r.subjectPcts[sn]}%" else "-")
                }
                PdfReportHelper.ReportRow(cols,
                    when { r.overall >= 75 -> "green"; r.overall >= 60 -> "orange"; else -> "red" })
            },
            fileName = "attendance_report_${System.currentTimeMillis()}.pdf"
        )
        if (intent != null) try { startActivity(intent) }
        catch (e: Exception) { Toast.makeText(this, "PDF viewer install karo", Toast.LENGTH_SHORT).show() }
    }
}
