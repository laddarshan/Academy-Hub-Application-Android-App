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
import com.example.academyhub.databinding.ActivityFeesReportBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class FeesReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeesReportBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var filterHelper: ReportFilterHelper

    data class FeesRow(
        val name: String, val rollNo: String, val course: String,
        val semester: String, val totalFees: Double,
        val paidFees: Double, val pendingFees: Double, val feesStatus: String
    )
    private val rowList = mutableListOf<FeesRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeesReportBinding.inflate(layoutInflater)
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
                var totalCollection = 0.0; var totalPending = 0.0
                var paidCount = 0; var partialCount = 0; var pendingCount = 0

                for (doc in snap) {
                    val totalFees = doc.getDouble("totalFees") ?: 0.0
                    if (totalFees <= 0.0) continue
                    val paidFees   = doc.getDouble("paidFees") ?: 0.0
                    val pending    = (totalFees - paidFees).coerceAtLeast(0.0)
                    val feesStatus = doc.getString("feesStatus") ?: "pending"
                    totalCollection += paidFees; totalPending += pending
                    when (feesStatus) { "paid" -> paidCount++; "partial" -> partialCount++; else -> pendingCount++ }
                    rowList.add(FeesRow(
                        doc.getString("name") ?: "", doc.getString("rollNumber") ?: "",
                        doc.getString("courseName") ?: "", doc.getString("semesterName") ?: "",
                        totalFees, paidFees, pending, feesStatus
                    ))
                }
                rowList.sortWith(compareBy { when (it.feesStatus) { "pending" -> 0; "partial" -> 1; else -> 2 } })

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.tvFilterInfo.text         = filterHelper.filterText()
                    binding.tvFilterInfo.visibility   = View.VISIBLE

                    animCount(binding.tvTotalStudents, rowList.size)
                    binding.tvTotalCollection.text = "₹ ${String.format("%,.0f", totalCollection)}"
                    binding.tvTotalPending.text    = "₹ ${String.format("%,.0f", totalPending)}"
                    animCount(binding.tvPaidCount, paidCount)
                    animCount(binding.tvPartialCount, partialCount)
                    animCount(binding.tvPendingCount, pendingCount)

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
                        Toast.makeText(this@FeesReportActivity, "Koi data nathi maliyo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@FeesReportActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun animCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 600; addUpdateListener { tv.text = "${it.animatedValue}" }; start()
        }
    }

    // Fixed-width LinearLayout rows - works in HorizontalScrollView
    private val W_NAME   by lazy { dp(120) }
    private val W_ROLL   by lazy { dp(65) }
    private val W_SEM    by lazy { dp(80) }
    private val W_TOTAL  by lazy { dp(75) }
    private val W_PAID   by lazy { dp(75) }
    private val W_PEND   by lazy { dp(75) }
    private val W_STATUS by lazy { dp(75) }

    private fun buildTable() {
        binding.tableLayout.removeAllViews()
        binding.tableLayout.orientation = LinearLayout.VERTICAL
        val cols  = listOf("Name", "Roll", "Semester", "Total", "Paid", "Pending", "Status")
        val widths = listOf(W_NAME, W_ROLL, W_SEM, W_TOTAL, W_PAID, W_PEND, W_STATUS)
        binding.tableLayout.addView(makeHeaderRow(cols, widths))
        binding.tableLayout.addView(divider())
        rowList.forEachIndexed { i, r ->
            val color = when (r.feesStatus) { "paid" -> "#2E7D32"; "partial" -> "#E65100"; else -> "#B71C1C" }
            val data  = listOf(r.name, r.rollNo, r.semester,
                "₹${r.totalFees.toInt()}", "₹${r.paidFees.toInt()}",
                "₹${r.pendingFees.toInt()}", r.feesStatus.replaceFirstChar { it.uppercase() })
            val hi    = Array(data.size) { if (it == 6) color else null }
            binding.tableLayout.addView(makeDataRow(data, widths, hi, i))
            binding.tableLayout.addView(divider())
        }
    }

    private fun makeHeaderRow(cols: List<String>, widths: List<Int>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            cols.forEachIndexed { i, col ->
                addView(TextView(this@FeesReportActivity).apply {
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
                addView(TextView(this@FeesReportActivity).apply {
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
            context = this, reportTitle = "Fees Report",
            subtitle = filterHelper.filterText(),
            summaryItems = listOf(
                "Session"          to filterHelper.getCurrentFilter().sessionName,
                "Filter"           to filterHelper.filterText(),
                "Total Students"   to binding.tvTotalStudents.text.toString(),
                "Total Collection" to binding.tvTotalCollection.text.toString(),
                "Total Pending"    to binding.tvTotalPending.text.toString(),
                "Paid"             to binding.tvPaidCount.text.toString(),
                "Partial"          to binding.tvPartialCount.text.toString(),
                "Pending"          to binding.tvPendingCount.text.toString()
            ),
            tableHeaders = listOf("Name", "Roll", "Semester", "Total", "Paid", "Pending", "Status"),
            tableRows = rowList.map { r ->
                PdfReportHelper.ReportRow(
                    listOf(r.name, r.rollNo, r.semester,
                        "₹${r.totalFees.toInt()}", "₹${r.paidFees.toInt()}",
                        "₹${r.pendingFees.toInt()}", r.feesStatus.replaceFirstChar { it.uppercase() }),
                    when (r.feesStatus) { "paid" -> "green"; "partial" -> "orange"; else -> "red" }
                )
            },
            fileName = "fees_report_${System.currentTimeMillis()}.pdf"
        )
        if (intent != null) try { startActivity(intent) }
        catch (e: Exception) { Toast.makeText(this, "PDF viewer install karo", Toast.LENGTH_SHORT).show() }
    }
}
