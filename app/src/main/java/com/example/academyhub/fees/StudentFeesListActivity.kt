package com.example.academyhub.fees

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentFeesListBinding
import com.example.academyhub.databinding.ItemStudentFeesBinding
import com.google.firebase.firestore.FirebaseFirestore

data class StudentFeeItem(
    val studentId: String,
    val name: String,
    val rollNumber: String,
    val totalFees: Double,
    val paidFees: Double,
    val pendingFees: Double,
    val feesStatus: String,
    val courseId: String = "",
    val courseName: String = "",
    val semesterId: String = "",
    val semesterName: String = ""
)

class StudentFeesListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentFeesListBinding
    private lateinit var firestore: FirebaseFirestore

    private val allList      = mutableListOf<StudentFeeItem>()
    private val filteredList = mutableListOf<StudentFeeItem>()
    private var statusFilter = "all"

    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentFeesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvGroupBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        setupRecycler()
        setupSearch()
        setupChips()
        setupSwipeRefresh()

        binding.btnBack.setOnClickListener { finish() }
        loadStudents()
    }

    // ─── RecyclerView ──────────────────────────────────────────────────────────

    inner class FeesVH(val b: ItemStudentFeesBinding) : RecyclerView.ViewHolder(b.root)

    private val adapter = object : RecyclerView.Adapter<FeesVH>() {

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            FeesVH(ItemStudentFeesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = filteredList.size

        override fun onBindViewHolder(holder: FeesVH, pos: Int) {
            val item = filteredList[pos]
            val b    = holder.b

            b.tvStudentName.text = item.name
            b.tvRollNumber.text  = "Roll: ${item.rollNumber}"
            b.tvPendingFees.text = "₹${fmt(item.pendingFees)}"

            // Initials
            val initials = item.name.split(" ").filter { it.isNotEmpty() }
                .take(2).joinToString("") { it[0].uppercaseChar().toString() }.ifEmpty { "S" }
            b.tvInitials.text = initials

            // Progress
            val pct = if (item.totalFees > 0) ((item.paidFees / item.totalFees) * 100).toInt() else 0
            b.miniProgress.progress = pct
            b.tvPaidPercent.text    = "$pct%"

            // Status chip + bar color
            val statusText    = when (item.feesStatus) { "paid" -> "Paid"; "partial" -> "Partial"; else -> "Pending" }
            val statusColor   = when (item.feesStatus) { "paid" -> "#075E54"; "partial" -> "#E65100"; else -> "#C62828" }
            b.tvFeesStatus.text = statusText
            b.tvFeesStatus.setTextColor(Color.parseColor(statusColor))
            b.tvPendingFees.setTextColor(Color.parseColor(statusColor))
            b.viewStatusBar.setBackgroundColor(Color.parseColor(statusColor))

            // Stagger entrance
            b.root.alpha = 0f; b.root.translationY = 12f
            b.root.animate().alpha(1f).translationY(0f)
                .setDuration(200).setStartDelay(pos * 40L)
                .setInterpolator(DecelerateInterpolator()).start()

            // Click → details
            b.root.setOnClickListener {
                ObjectAnimator.ofFloat(it, "alpha", 1f, 0.7f, 1f).apply { duration = 160; start() }
                it.postDelayed({
                    startActivity(Intent(this@StudentFeesListActivity, StudentFeesDetailsActivity::class.java).apply {
                        putExtra("studentId",   item.studentId)
                        putExtra("studentName", item.name)
                        putExtra("courseId",    item.courseId)
                        putExtra("courseName",  item.courseName)
                        putExtra("semesterId",  item.semesterId)
                        putExtra("semesterName",item.semesterName)
                    })
                }, 130)
            }
        }
    }


    private fun setupRecycler() {
        binding.rvStudentFees.layoutManager = LinearLayoutManager(this)
        binding.rvStudentFees.adapter = adapter
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── Chips ─────────────────────────────────────────────────────────────────

    private fun setupChips() {
        val chips = listOf(
            binding.chipAll     to "all",
            binding.chipPaid    to "paid",
            binding.chipPartial to "partial",
            binding.chipPending to "pending"
        )
        chips.forEach { (chip, status) ->
            chip.setOnClickListener {
                statusFilter = status
                chips.forEach { (c, s) ->
                    if (s == status) {
                        c.setBackgroundResource(R.drawable.bg_day_chip_active)
                        c.setTextColor(Color.WHITE)
                    } else {
                        c.setBackgroundResource(R.drawable.bg_day_chip_inactive)
                        c.setTextColor(Color.parseColor("#AAFFEE"))
                    }
                }
                applyFilter()
            }
        }
    }

    // ─── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadStudents(fromSwipe = true) }
    }

    // ─── Load ──────────────────────────────────────────────────────────────────

    private fun loadStudents(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Students")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allList.clear()

                var totalCollected = 0.0; var totalPending = 0.0

                for (doc in docs) {
                    val status  = doc.getString("active") ?: doc.getString("status") ?: ""
                    if (status == "blocked") continue   // skip blocked

                    val name     = doc.getString("name")         ?: ""
                    val roll     = doc.getString("rollNumber")   ?: ""
                    val total    = doc.getDouble("totalFees")    ?: 0.0
                    val paid     = doc.getDouble("paidFees")     ?: 0.0
                    val pending  = (total - paid).coerceAtLeast(0.0)
                    val fStatus  = doc.getString("feesStatus")   ?: "pending"
                    val cId      = doc.getString("courseId")     ?: courseId
                    val cName    = doc.getString("courseName")   ?: courseName
                    val sId      = doc.getString("semesterId")   ?: semesterId
                    val sName    = doc.getString("semesterName") ?: semesterName

                    allList.add(StudentFeeItem(doc.id, name, roll, total, paid, pending, fStatus,
                        cId, cName, sId, sName))
                    totalCollected += paid; totalPending += pending
                }
                allList.sortWith(compareBy { it.name })

                // ValueAnimator summary
                animateSummary(allList.size, totalCollected, totalPending)
                applyFilter()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animateSummary(count: Int, collected: Double, pending: Double) {
        ValueAnimator.ofInt(0, count).apply {
            duration = 600
            addUpdateListener { binding.tvTotalStudents.text = "${it.animatedValue}" }
            start()
        }
        ValueAnimator.ofFloat(0f, collected.toFloat()).apply {
            duration = 700
            addUpdateListener { binding.tvTotalCollected.text = "₹${fmt((it.animatedValue as Float).toDouble())}" }
            start()
        }
        ValueAnimator.ofFloat(0f, pending.toFloat()).apply {
            duration = 700
            addUpdateListener { binding.tvTotalPending.text = "₹${fmt((it.animatedValue as Float).toDouble())}" }
            start()
        }
    }

    // ─── Filter ────────────────────────────────────────────────────────────────

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(
            allList.filter { item ->
                val matchStatus = statusFilter == "all" || item.feesStatus == statusFilter
                val matchSearch = q.isEmpty() || item.name.lowercase().contains(q) ||
                        item.rollNumber.lowercase().contains(q)
                matchStatus && matchSearch
            }
        )

        binding.layoutEmpty.visibility     = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvStudentFees.visibility   = if (filteredList.isEmpty()) View.GONE    else View.VISIBLE

        if (filteredList.isEmpty()) {
            binding.tvNoData.text = if (q.isNotEmpty()) "No results for \"$q\""
            else "No students found for this filter"
        }

        adapter.notifyDataSetChanged()
        binding.rvStudentFees.alpha = 0f
        binding.rvStudentFees.animate().alpha(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun fmt(a: Double) = if (a >= 100000)
        String.format("%.1fL", a / 100000)
    else String.format("%,.0f", a)

    override fun onResume() { super.onResume(); loadStudents() }
}