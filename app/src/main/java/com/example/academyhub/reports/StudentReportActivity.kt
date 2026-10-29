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
import com.example.academyhub.databinding.ActivityStudentReportBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class StudentReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentReportBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var filterHelper: ReportFilterHelper

    data class StudentRow(
        val name: String, val rollNo: String, val mobile: String,
        val gender: String, val course: String, val semesterName: String,
        val batchName: String, val admissionDate: String,
        val feesStatus: String, val status: String
    )
    private val rowList = mutableListOf<StudentRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterHelper = ReportFilterHelper(
            context = this, db = db,
            spinnerCourse   = binding.spinnerCourse, spinnerSession  = binding.spinnerSession,
            spinnerYear     = binding.spinnerYear,   spinnerSemester = binding.spinnerSemester,
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
        binding.summaryCard.visibility  = View.GONE
        binding.btnExportPdf.visibility = View.GONE
        rowList.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snap = filterHelper.buildStudentsQuery().get().await()
                var maleCount = 0; var femaleCount = 0
                var feesPaidCount = 0; var feesPartialCount = 0; var feesPendingCount = 0
                var activeCount = 0

                for (doc in snap) {
                    val gender     = doc.getString("gender") ?: ""
                    val feesStatus = doc.getString("feesStatus") ?: "pending"
                    val status     = doc.getString("status") ?: "active"
                    if (gender.equals("Male", ignoreCase = true)) maleCount++ else if (gender.equals("Female", ignoreCase = true)) femaleCount++
                    when (feesStatus) { "paid" -> feesPaidCount++; "partial" -> feesPartialCount++; else -> feesPendingCount++ }
                    if (status == "active") activeCount++
                    rowList.add(StudentRow(
                        doc.getString("name") ?: "", doc.getString("rollNumber") ?: "",
                        doc.getString("mobile") ?: "", gender,
                        doc.getString("courseName") ?: "", doc.getString("semesterName") ?: "",
                        doc.getString("batchName") ?: "", doc.getString("admissionDate") ?: "",
                        feesStatus, status
                    ))
                }
                rowList.sortBy { it.rollNo }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.tvFilterInfo.text         = filterHelper.filterText()
                    binding.tvFilterInfo.visibility   = View.VISIBLE

                    animCount(binding.tvTotalStudents, rowList.size)
                    animCount(binding.tvActiveCount, activeCount)
                    animCount(binding.tvMaleCount, maleCount)
                    animCount(binding.tvFemaleCount, femaleCount)
                    animCount(binding.tvFeesPaid, feesPaidCount)
                    animCount(binding.tvFeesPartial, feesPartialCount)
                    animCount(binding.tvFeesPending, feesPendingCount)

                    binding.summaryCard.alpha      = 0f
                    binding.summaryCard.visibility = View.VISIBLE
                    binding.summaryCard.animate().alpha(1f).setDuration(300)
                        .setInterpolator(DecelerateInterpolator()).start()

                    buildTable()

                    if (rowList.isNotEmpty()) {
                        binding.btnExportPdf.visibility = View.VISIBLE
                        binding.btnExportPdf.alpha      = 0f
                        binding.btnExportPdf.animate().alpha(1f).setDuration(250).start()
                    } else {
                        Toast.makeText(this@StudentReportActivity, "Koi data nathi maliyo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@StudentReportActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun animCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 600; addUpdateListener { tv.text = "${it.animatedValue}" }; start()
        }
    }

    private val W_NAME   by lazy { dp(180) }
    private val W_ROLL   by lazy { dp(60) }
    private val W_MOB    by lazy { dp(100) }
    private val W_SEM    by lazy { dp(80) }
    private val W_BATCH  by lazy { dp(90) }
    private val W_FEES   by lazy { dp(70) }
    private val W_STS    by lazy { dp(75) }

    private fun buildTable() {
        binding.tableLayout.removeAllViews()
        binding.tableLayout.orientation = LinearLayout.VERTICAL
        val cols   = listOf("Name", "Roll", "Mobile", "Semester", "Batch", "Fees", "Status")
        val widths = listOf(W_NAME, W_ROLL, W_MOB, W_SEM, W_BATCH, W_FEES, W_STS)
        binding.tableLayout.addView(makeHeaderRow(cols, widths))
        binding.tableLayout.addView(divider())
        rowList.forEachIndexed { i, r ->
            val feesColor = when (r.feesStatus) { "paid" -> "#2E7D32"; "partial" -> "#E65100"; else -> "#B71C1C" }
            val data = listOf(r.name, r.rollNo, r.mobile, r.semesterName, r.batchName,
                r.feesStatus.replaceFirstChar { it.uppercase() },
                r.status.replaceFirstChar { it.uppercase() })
            val hi = Array(data.size) { if (it == 5) feesColor else null }
            binding.tableLayout.addView(makeDataRow(data, widths, hi, i))
            binding.tableLayout.addView(divider())
        }
    }

    private fun makeHeaderRow(cols: List<String>, widths: List<Int>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            cols.forEachIndexed { i, col ->
                addView(TextView(this@StudentReportActivity).apply {
                    text = col; textSize = 11.5f; setPadding(dp(10), dp(9), dp(10), dp(9))
                    setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(widths[i], ViewGroup.LayoutParams.WRAP_CONTENT)
                })
            }
        }
    }

    private fun makeDataRow(cols: List<String>, widths: List<Int>,
                            hi: Array<String?>, rowIdx: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val bg = if (rowIdx % 2 == 0)
                resolveColor(com.google.android.material.R.attr.colorSurface)
            else
                blendWithSurface(Color.parseColor("#075E54"), 0.04f)
            setBackgroundColor(bg)
            cols.forEachIndexed { i, col ->
                addView(TextView(this@StudentReportActivity).apply {
                    text = col; textSize = 11f; setPadding(dp(10), dp(8), dp(10), dp(8))
                    val tc = hi[i]
                    if (tc != null) { setTextColor(Color.parseColor(tc)); setTypeface(null, Typeface.BOLD) }
                    else setTextColor(resolveColor(android.R.attr.textColorPrimary))
                    maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(widths[i], ViewGroup.LayoutParams.WRAP_CONTENT)
                })
            }
        }
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(resolveColor(android.R.attr.colorControlHighlight))
    }

    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data
    }
    private fun blendWithSurface(color: Int, alpha: Float): Int {
        val s = resolveColor(com.google.android.material.R.attr.colorSurface)
        return Color.rgb(
            (Color.red(s)   * (1-alpha) + Color.red(color)   * alpha).toInt(),
            (Color.green(s) * (1-alpha) + Color.green(color) * alpha).toInt(),
            (Color.blue(s)  * (1-alpha) + Color.blue(color)  * alpha).toInt()
        )
    }
    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun exportPdf() {
        val intent = PdfReportHelper.generatePdf(
            context = this, reportTitle = "Student Report",
            subtitle = filterHelper.filterText(),
            summaryItems = listOf(
                "Session"       to filterHelper.getCurrentFilter().sessionName,
                "Filter"        to filterHelper.filterText(),
                "Total"         to binding.tvTotalStudents.text.toString(),
                "Active"        to binding.tvActiveCount.text.toString(),
                "Male"          to binding.tvMaleCount.text.toString(),
                "Female"        to binding.tvFemaleCount.text.toString(),
                "Fees Paid"     to binding.tvFeesPaid.text.toString(),
                "Fees Partial"  to binding.tvFeesPartial.text.toString(),
                "Fees Pending"  to binding.tvFeesPending.text.toString()
            ),
            tableHeaders = listOf("Name", "Roll", "Mobile", "Semester", "Batch", "Fees", "Status"),
            tableRows = rowList.map { r ->
                PdfReportHelper.ReportRow(
                    listOf(r.name, r.rollNo, r.mobile, r.semesterName, r.batchName,
                        r.feesStatus.replaceFirstChar { it.uppercase() },
                        r.status.replaceFirstChar { it.uppercase() }),
                    when (r.feesStatus) { "paid" -> "green"; "partial" -> "orange"; else -> "normal" }
                )
            },
            fileName = "student_report_${System.currentTimeMillis()}.pdf"
        )
        if (intent != null) try { startActivity(intent) }
        catch (e: Exception) { Toast.makeText(this, "PDF viewer install karo", Toast.LENGTH_SHORT).show() }
    }
}
