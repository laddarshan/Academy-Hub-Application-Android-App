package com.example.academyhub.students

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
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentStatsBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class StudentStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentStatsBinding
    private val db = FirebaseFirestore.getInstance()

    // All students data cache
    data class StudentInfo(
        val courseId: String, val courseName: String,
        val sessionName: String, val yearName: String,
        val semesterName: String, val status: String, val gender: String
    )

    private val allStudents   = mutableListOf<StudentInfo>()
    private val courseNames   = mutableListOf<String>() // for tabs
    private var selectedCourse = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        setupSwipeRefresh()
        loadStudents()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"), Color.parseColor("#26A69A"), Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadStudents(fromSwipe = true) }
    }

    // ── Load all students ────────────────────────────────────────────────────

    private fun loadStudents(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        allStudents.clear()
        courseNames.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snap = db.collection("Students").get().await()
                val courseSet = linkedSetOf<String>() // preserve insertion order

                for (doc in snap) {
                    val info = StudentInfo(
                        courseId     = doc.getString("courseId")     ?: "",
                        courseName   = doc.getString("courseName")   ?: "Unknown",
                        sessionName  = doc.getString("sessionName")  ?: "Unknown",
                        yearName     = doc.getString("yearName")     ?: "Unknown",
                        semesterName = doc.getString("semesterName") ?: "Unknown",
                        status       = doc.getString("status")       ?: "active",
                        gender       = doc.getString("gender")       ?: ""
                    )
                    allStudents.add(info)
                    if (info.courseName.isNotBlank()) courseSet.add(info.courseName)
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
                    Toast.makeText(this@StudentStatsActivity,
                        "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Course filter tabs ────────────────────────────────────────────────────

    private fun buildCourseTabs() {
        binding.llCourseTabs.removeAllViews()
        val tabs = listOf("ALL") + courseNames

        tabs.forEachIndexed { i, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), 0, dp(4), dp(8)) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedCourse = name
                    buildCourseTabs() // rebuild tabs to update active style
                    displayForCourse(name)
                    // Scale zoom + indicator
                    animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).withEndAction {
                        animate().scaleX(1f).scaleY(1f)
                            .setDuration(150).setInterpolator(OvershootInterpolator()).start()
                    }.start()
                }
            }
            if (name == selectedCourse) {
                chip.setBackgroundColor(Color.WHITE)
                chip.setTextColor(Color.parseColor("#075E54"))
            } else {
                chip.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                chip.setTextColor(Color.parseColor("#CCFFFFFF"))
            }
            binding.llCourseTabs.addView(chip)

            // Active indicator bar below selected
            if (name == selectedCourse) {
                val indicator = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }
                    setBackgroundColor(Color.WHITE)
                }
                // Add indicator below the chip in a wrapper
            }
        }
    }

    // ── Display breakdown for selected course ─────────────────────────────────

    private fun displayForCourse(course: String) {
        val filtered = if (course == "ALL") allStudents
                       else allStudents.filter { it.courseName == course }

        val total  = filtered.size
        val active = filtered.count { it.status == "active" }
        val male   = filtered.count { it.gender.equals("Male", ignoreCase = true) }
        val female = filtered.count { it.gender.equals("Female", ignoreCase = true) }

        // Update header + summary
        animCount(binding.tvTotalBadge, total)
        animCount(binding.tvSummaryTotal, total)
        animCount(binding.tvSummaryActive, active)
        animCount(binding.tvSummaryMale, male)
        animCount(binding.tvSummaryFemale, female)
        binding.tvHeaderSubtitle.text = if (course == "ALL") "All courses breakdown"
                                        else "$course — breakdown"

        // Group by Session → Course → Year → Semester
        // Display: "Session • Course • Year • Semester → N students"
        data class GroupKey(val session: String, val course: String, val year: String, val sem: String)

        val grouped = filtered.groupBy {
            GroupKey(it.sessionName, it.courseName, it.yearName, it.semesterName)
        }.entries.sortedWith(compareBy({ it.key.session }, { it.key.course },
                                       { it.key.year },    { it.key.sem }))

        binding.llBreakdown.removeAllViews()

        if (grouped.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            return
        }
        binding.layoutEmpty.visibility = View.GONE

        grouped.forEachIndexed { i, (key, students) ->
            val row = buildBreakdownRow(key.session, key.course, key.year, key.sem, students.size, i)
            binding.llBreakdown.addView(row)

            // Stagger entrance
            row.alpha = 0f; row.translationY = 16f
            row.animate().alpha(1f).translationY(0f)
                .setDuration(220).setStartDelay(i * 40L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun buildBreakdownRow(session: String, course: String,
                                  year: String, sem: String,
                                  count: Int, idx: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL

            // Left indicator bar
            addView(View(this@StudentStatsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    setMargins(0, dp(2), dp(12), dp(2))
                }
                setBackgroundColor(Color.parseColor("#075E54"))
            })

            // Info
            val info = LinearLayout(this@StudentStatsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, dp(12), dp(8), dp(12))
                }
            }

            info.addView(TextView(this@StudentStatsActivity).apply {
                text = "$session  •  $course"
                textSize = 13f; setTypeface(null, Typeface.BOLD)
                setTextColor(resolveColor(android.R.attr.textColorPrimary))
            })
            info.addView(TextView(this@StudentStatsActivity).apply {
                text = "$year  •  $sem"
                textSize = 11f; setTextColor(resolveColor(android.R.attr.textColorSecondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }
            })
            addView(info)

            // Count badge
            addView(TextView(this@StudentStatsActivity).apply {
                text = "$count"
                textSize = 18f; setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#075E54"))
                setPadding(dp(8), 0, dp(4), 0)
            })
            addView(TextView(this@StudentStatsActivity).apply {
                text = if (count == 1) "student" else "students"
                textSize = 10f
                setTextColor(resolveColor(android.R.attr.textColorSecondary))
                setPadding(0, 0, dp(4), 0)
            })

            // Card-like background
            val bg = if (idx % 2 == 0)
                resolveColor(com.google.android.material.R.attr.colorSurface)
            else
                blendWithSurface(Color.parseColor("#075E54"), 0.04f)
            setBackgroundColor(bg)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(1)) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun animCount(tv: TextView, target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 700; addUpdateListener { tv.text = "${it.animatedValue}" }; start()
        }
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
}
