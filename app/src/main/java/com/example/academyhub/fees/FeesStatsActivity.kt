package com.example.academyhub.fees

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityFeesStatsBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class FeesStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeesStatsBinding
    private val db = FirebaseFirestore.getInstance()

    data class FeeInfo(
        val courseName: String, val sessionName: String,
        val yearName: String, val semesterName: String,
        val totalFees: Double, val paidFees: Double,
        val feesStatus: String
    )

    private val allFees     = mutableListOf<FeeInfo>()
    private val courseNames = mutableListOf<String>()
    private var selectedCourse = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeesStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        setupSwipeRefresh()
        loadFees()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"), Color.parseColor("#26A69A"), Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadFees(fromSwipe = true) }
    }

    private fun loadFees(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        allFees.clear(); courseNames.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snap     = db.collection("Students").get().await()
                val courseSet = linkedSetOf<String>()
                for (doc in snap) {
                    val total = doc.getDouble("totalFees") ?: 0.0
                    if (total <= 0.0) continue
                    val cn = doc.getString("courseName") ?: "Unknown"
                    allFees.add(FeeInfo(
                        courseName   = cn,
                        sessionName  = doc.getString("sessionName")  ?: "Unknown",
                        yearName     = doc.getString("yearName")     ?: "Unknown",
                        semesterName = doc.getString("semesterName") ?: "Unknown",
                        totalFees    = total,
                        paidFees     = doc.getDouble("paidFees")    ?: 0.0,
                        feesStatus   = doc.getString("feesStatus")   ?: "pending"
                    ))
                    courseSet.add(cn)
                }
                courseNames.addAll(courseSet.sorted())

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    buildCourseTabs()
                    displayForCourse(selectedCourse)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility    = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    Toast.makeText(this@FeesStatsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildCourseTabs() {
        binding.llCourseTabs.removeAllViews()
        val tabs = listOf("ALL") + courseNames
        tabs.forEach { name ->
            val chip = TextView(this).apply {
                text = name; textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), 0, dp(4), dp(8)) }
                isClickable = true; isFocusable = true
                if (name == selectedCourse) {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.parseColor("#075E54"))
                } else {
                    setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                    setTextColor(Color.parseColor("#CCFFFFFF"))
                }
                setOnClickListener {
                    selectedCourse = name
                    buildCourseTabs(); displayForCourse(name)
                    animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                        animate().scaleX(1f).scaleY(1f)
                            .setDuration(150).setInterpolator(OvershootInterpolator()).start()
                    }.start()
                }
            }
            binding.llCourseTabs.addView(chip)
        }
    }

    private fun displayForCourse(course: String) {
        val filtered = if (course == "ALL") allFees else allFees.filter { it.courseName == course }

        var totalAmt = 0.0; var paidAmt = 0.0
        var paidCnt = 0; var partialCnt = 0; var pendingCnt = 0
        filtered.forEach { f ->
            totalAmt += f.totalFees; paidAmt += f.paidFees
            when (f.feesStatus) { "paid" -> paidCnt++; "partial" -> partialCnt++; else -> pendingCnt++ }
        }
        val pendingAmt = (totalAmt - paidAmt).coerceAtLeast(0.0)

        animCountFees(binding.tvTotalAmt, totalAmt.toLong())
        animCountFees(binding.tvPaidAmt, paidAmt.toLong())
        animCountFees(binding.tvPendingAmt, pendingAmt.toLong())
        animCount(binding.tvPaidCount, paidCnt)
        animCount(binding.tvPartialCount, partialCnt)
        animCount(binding.tvPendingCount, pendingCnt)
        binding.tvHeaderSubtitle.text = if (course == "ALL") "All courses fees" else "$course — fees"

        // Group by Session → Course → Year → Semester
        data class GK(val session: String, val course: String, val year: String, val sem: String)
        val grouped = filtered.groupBy { GK(it.sessionName, it.courseName, it.yearName, it.semesterName) }
            .entries.sortedWith(compareBy({ it.key.session }, { it.key.course },
                                          { it.key.year },    { it.key.sem }))

        binding.llBreakdown.removeAllViews()
        if (grouped.isEmpty()) { binding.layoutEmpty.visibility = View.VISIBLE; return }
        binding.layoutEmpty.visibility = View.GONE

        grouped.forEachIndexed { i, (key, items) ->
            val groupTotal   = items.sumOf { it.totalFees }
            val groupPaid    = items.sumOf { it.paidFees }
            val groupPending = (groupTotal - groupPaid).coerceAtLeast(0.0)
            val row = buildRow(key.session, key.course, key.year, key.sem,
                items.size, groupPaid, groupPending, i)
            binding.llBreakdown.addView(row)
            row.alpha = 0f; row.translationY = 16f
            row.animate().alpha(1f).translationY(0f)
                .setDuration(220).setStartDelay(i * 40L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun buildRow(session: String, course: String, year: String, sem: String,
                         count: Int, paid: Double, pending: Double, idx: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL

            addView(View(this@FeesStatsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(0, dp(2), dp(12), dp(2))
                }
                val color = when {
                    pending <= 0.0 -> "#2E7D32"
                    pending < paid -> "#E65100"
                    else           -> "#B71C1C"
                }
                setBackgroundColor(Color.parseColor(color))
            })

            val info = LinearLayout(this@FeesStatsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, dp(12), dp(8), dp(12))
                }
            }
            info.addView(TextView(this@FeesStatsActivity).apply {
                text = "$session  •  $course"; textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(resolveColor(android.R.attr.textColorPrimary))
            })
            info.addView(TextView(this@FeesStatsActivity).apply {
                text = "$year  •  $sem  •  $count students"
                textSize = 11f; setTextColor(resolveColor(android.R.attr.textColorSecondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) }
            })
            info.addView(TextView(this@FeesStatsActivity).apply {
                text = "Paid: ₹${paid.toLong()}  |  Pending: ₹${pending.toLong()}"
                textSize = 11f
                setTextColor(if (pending <= 0.0) Color.parseColor("#2E7D32") else Color.parseColor("#B71C1C"))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            })
            addView(info)

            val bg = if (idx % 2 == 0) resolveColor(com.google.android.material.R.attr.colorSurface)
                     else blendWithSurface(Color.parseColor("#075E54"), 0.04f)
            setBackgroundColor(bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(1)) }
        }
    }

    private fun animCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 700; addUpdateListener { tv.text = "${it.animatedValue}" }; start()
        }
    }
    private fun animCountFees(tv: TextView, target: Long) {
        ValueAnimator.ofInt(0, target.toInt()).apply {
            duration = 800; addUpdateListener { tv.text = "₹${it.animatedValue}" }; start()
        }
    }
    private fun resolveColor(attr: Int): Int {
        val tv = TypedValue(); theme.resolveAttribute(attr, tv, true); return tv.data
    }
    private fun blendWithSurface(color: Int, alpha: Float): Int {
        val s = resolveColor(com.google.android.material.R.attr.colorSurface)
        return Color.rgb(
            (Color.red(s)*(1-alpha)+Color.red(color)*alpha).toInt(),
            (Color.green(s)*(1-alpha)+Color.green(color)*alpha).toInt(),
            (Color.blue(s)*(1-alpha)+Color.blue(color)*alpha).toInt()
        )
    }
    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
