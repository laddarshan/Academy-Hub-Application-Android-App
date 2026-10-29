package com.example.academyhub.student

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityStudentFeesBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore

class StudentFeesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentFeesBinding
    private val db    = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    private var studentDocId = ""
    private var sessionId    = ""
    private var courseId     = ""
    private var courseName   = ""
    private var yearName     = ""
    private var semesterId   = ""
    private var semesterName = ""
    private var studentName  = ""

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, StudentFeesActivity::class.java).also { i ->
                i.putExtra("studentDocId", student.id)
                i.putExtra("sessionId",    student.sessionId)
                i.putExtra("courseId",     student.courseId)
                i.putExtra("courseName",   student.courseName)
                i.putExtra("yearName",     student.yearName)
                i.putExtra("semesterId",   student.semesterId)
                i.putExtra("semesterName", student.semesterName)
                i.putExtra("studentName",  student.name)
                context.startActivity(i)
            }
        }

        fun start(context: Context) {
            context.startActivity(Intent(context, StudentFeesActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentFeesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentDocId = intent.getStringExtra("studentDocId") ?: prefs.getString("studentDocId","") ?: ""
        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        studentName  = intent.getStringExtra("studentName")  ?: ""

        setupHeader()
        setupClicks()
        runEntranceAnim()

        if (studentDocId.isEmpty()) loadStudentThenData()
        else                        loadData()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Header
    // ─────────────────────────────────────────────────────────────────────
    private fun setupHeader() {
        binding.tvHeaderBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  ›  ")
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load student from prefs if no studentDocId
    // ─────────────────────────────────────────────────────────────────────
    private fun loadStudentThenData() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = prefs.getString("studentDocId","") ?: ""
        if (docId.isNotEmpty()) {
            db.collection("Students").document(docId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        studentDocId = doc.id
                        sessionId    = doc.getString("sessionId")    ?: ""
                        courseId     = doc.getString("courseId")     ?: ""
                        courseName   = doc.getString("courseName")   ?: ""
                        yearName     = doc.getString("yearName")     ?: ""
                        semesterId   = doc.getString("semesterId")   ?: ""
                        semesterName = doc.getString("semesterName") ?: ""
                        studentName  = doc.getString("name")         ?: ""
                        setupHeader()
                        loadData()
                    } else loadByPhone()
                }
                .addOnFailureListener { loadByPhone() }
        } else loadByPhone()
    }

    private fun loadByPhone() {
        val phone = prefs.getString("userPhone","") ?: ""
        if (phone.isEmpty()) { doneLoading(); showEmpty(); return }

        db.collection("Students").whereEqualTo("mobile", phone).limit(1).get()
            .addOnSuccessListener { q ->
                val doc = q.documents.firstOrNull()
                if (doc != null) {
                    studentDocId = doc.id
                    sessionId    = doc.getString("sessionId")    ?: ""
                    courseId     = doc.getString("courseId")     ?: ""
                    courseName   = doc.getString("courseName")   ?: ""
                    yearName     = doc.getString("yearName")     ?: ""
                    semesterId   = doc.getString("semesterId")   ?: ""
                    semesterName = doc.getString("semesterName") ?: ""
                    studentName  = doc.getString("name")         ?: ""
                    prefs.edit().putString("studentDocId", doc.id).apply()
                    setupHeader()
                    loadData()
                } else { doneLoading(); showEmpty() }
            }
            .addOnFailureListener { doneLoading(); showEmpty() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load fees + payment history for this student
    // ─────────────────────────────────────────────────────────────────────
    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("Students").document(studentDocId).get()
            .addOnSuccessListener { doc ->
                doneLoading()
                if (!doc.exists()) { showEmpty(); return@addOnSuccessListener }

                val totalFees   = doc.getDouble("totalFees")   ?: 0.0
                val paidFees    = doc.getDouble("paidFees")    ?: 0.0
                val pendingFees = (totalFees - paidFees).coerceAtLeast(0.0)

                if (totalFees <= 0) { showEmpty(); return@addOnSuccessListener }

                val feesStatus = when {
                    paidFees >= totalFees -> "paid"
                    paidFees > 0          -> "partial"
                    else                  -> "pending"
                }

                val paidPct = if (totalFees > 0)
                    ((paidFees / totalFees) * 100).toInt() else 0

                // ── Header mini chips ──
                binding.tvHeaderTotal.text = "₹${fmt(totalFees)}"
                binding.tvHeaderPaid.text  = "₹${fmt(paidFees)}"
                binding.tvHeaderDue.text   = "₹${fmt(pendingFees)}"

                // ── Summary card ──
                binding.cardSummary.visibility = View.VISIBLE
                binding.layoutEmpty.visibility = View.GONE

                binding.tvCourseSemBadge.text = listOf(courseName, semesterName)
                    .filter { it.isNotEmpty() }.joinToString("  ›  ")

                binding.tvTotalFees.text   = "₹${fmt(totalFees)}"
                binding.tvPaidFees.text    = "₹${fmt(paidFees)}"
                binding.tvPendingFees.text = "₹${fmt(pendingFees)}"

                val (badgeText, badgeColor) = when (feesStatus) {
                    "paid"    -> Pair("✓ PAID",  "#059669")
                    "partial" -> Pair("PARTIAL", "#D97706")
                    else      -> Pair("DUE",     "#E11D48")
                }
                binding.tvFeesStatusBadge.text = badgeText
                try { binding.tvFeesStatusBadge.setBackgroundColor(Color.parseColor(badgeColor)) }
                catch (_: Exception) {}

                binding.tvPaidPercent.text = "$paidPct% paid"
                binding.tvDuePercent.text  = "${100 - paidPct}% due"
                ObjectAnimator.ofInt(binding.progressFees, "progress", 0, paidPct)
                    .apply { duration = 800; interpolator = DecelerateInterpolator() }
                    .start()

                // Due alert
                if (pendingFees > 0) {
                    binding.layoutDueAlert.visibility = View.VISIBLE
                    binding.tvDueAlertText.text = "₹${fmt(pendingFees)} fees baaki chhe. Niche thi payment karo."
                } else {
                    binding.layoutDueAlert.visibility = View.GONE
                }

                // ── Pay Now button on summary card (show if pending) ──
                if (pendingFees > 0) {
                    binding.btnPayNow.visibility = View.VISIBLE
                    binding.btnPayNow.setOnClickListener { v ->
                        animPress(v) {
                            StudentPayFeesActivity.start(
                                context      = this,
                                studentDocId = studentDocId,
                                studentName  = studentName,
                                courseId     = courseId,
                                courseName   = courseName,
                                semesterId   = semesterId,
                                semesterName = semesterName,
                                totalFees    = totalFees,
                                paidFees     = paidFees,
                                pendingFees  = pendingFees
                            )
                        }
                    }
                } else {
                    binding.btnPayNow.visibility = View.GONE
                }

                // Animate card
                binding.cardSummary.alpha = 0f; binding.cardSummary.translationY = 24f
                binding.cardSummary.animate().alpha(1f).translationY(0f)
                    .setDuration(380).setInterpolator(DecelerateInterpolator()).start()

                loadPaymentHistory(totalFees, paidFees, pendingFees)
            }
            .addOnFailureListener { e ->
                doneLoading()
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmpty()
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Payment history
    // ─────────────────────────────────────────────────────────────────────
    private fun loadPaymentHistory(
        totalFees: Double, paidFees: Double, pendingFees: Double
    ) {
        db.collection("Payments")
            .whereEqualTo("studentId", studentDocId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                binding.cardPaymentHistory.visibility = View.VISIBLE
                val docs = snap.documents
                binding.tvPaymentCount.text = "${docs.size} payments"

                if (docs.isEmpty()) {
                    binding.layoutNoPayments.visibility = View.VISIBLE
                    binding.layoutPayments.visibility   = View.GONE
                } else {
                    binding.layoutNoPayments.visibility = View.GONE
                    binding.layoutPayments.visibility   = View.VISIBLE
                    binding.layoutPayments.removeAllViews()

                    docs.forEachIndexed { i, doc ->
                        val amt     = doc.getDouble("amount")        ?: 0.0
                        val date    = doc.getString("paymentDate")   ?: "—"
                        val mode    = doc.getString("paymentMode")   ?: "—"
                        val receipt = doc.getString("receiptNumber") ?: "—"
                        val sem     = doc.getString("semesterName")  ?: semesterName
                        addPaymentRow(amt, date, mode, receipt, sem, i, i == docs.size - 1)
                    }
                }

                binding.cardPaymentHistory.alpha = 0f; binding.cardPaymentHistory.translationY = 24f
                binding.cardPaymentHistory.animate().alpha(1f).translationY(0f)
                    .setDuration(380).setStartDelay(140)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
            .addOnFailureListener {
                binding.cardPaymentHistory.visibility = View.VISIBLE
                binding.layoutNoPayments.visibility   = View.VISIBLE
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Dynamic payment row
    // ─────────────────────────────────────────────────────────────────────
    private fun addPaymentRow(
        amount: Double, date: String, mode: String,
        receipt: String, semName: String, index: Int, isLast: Boolean
    ) {
        val row = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(0, if (index == 0) 0 else dpToPx(12), 0, if (isLast) 0 else dpToPx(12))
        }

        // Icon
        val icon = android.widget.TextView(this).apply {
            text     = getModeIcon(mode)
            textSize = 20f
            gravity  = Gravity.CENTER
            val lp = android.widget.LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            lp.marginEnd = dpToPx(14)
            layoutParams = lp
        }
        row.addView(icon)

        // Center
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        col.addView(android.widget.TextView(this).apply {
            text = "₹${fmt(amount)}"
            textSize = 15f
            setTextColor(Color.parseColor("#059669"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        col.addView(android.widget.TextView(this).apply {
            text = "$date  •  $mode"
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, dpToPx(2), 0, 0)
        })
        if (receipt.isNotEmpty() && receipt != "—") {
            col.addView(android.widget.TextView(this).apply {
                text = "#$receipt  •  $semName"
                textSize = 10f
                setTextColor(Color.parseColor("#BDBDBD"))
                setPadding(0, dpToPx(1), 0, 0)
            })
        }
        row.addView(col)
        binding.layoutPayments.addView(row)

        if (!isLast) {
            binding.layoutPayments.addView(android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                )
                setBackgroundColor(Color.parseColor("#0F000000"))
            })
        }

        // Entrance anim
        row.alpha = 0f; row.translationX = -20f
        row.postDelayed({
            row.animate().alpha(1f).translationX(0f)
                .setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        }, 200L + index * 60L)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────
    private fun getModeIcon(mode: String) = when (mode.lowercase()) {
        "cash"             -> "💵"
        "upi","gpay",
        "phonepe","paytm"  -> "📱"
        "cheque","check"   -> "🏦"
        "online","neft",
        "rtgs","imps"      -> "💳"
        else               -> "💰"
    }

    private fun showEmpty() {
        binding.layoutEmpty.visibility        = View.VISIBLE
        binding.cardSummary.visibility        = View.GONE
        binding.cardPaymentHistory.visibility = View.GONE
        binding.layoutDueAlert.visibility     = View.GONE
    }

    private fun doneLoading() { binding.progressBar.visibility = View.GONE }
    private fun fmt(a: Double) = String.format("%,.0f", a)
    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun animPress(view: android.view.View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    private fun runEntranceAnim() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { v -> animPress(v) { finish() } }

        binding.btnRefresh.setOnClickListener { v ->
            v.animate().rotation(v.rotation + 360f).setDuration(500).start()
            binding.layoutPayments.removeAllViews()
            binding.cardSummary.visibility        = View.GONE
            binding.cardPaymentHistory.visibility = View.GONE
            binding.layoutEmpty.visibility        = View.GONE
            binding.layoutDueAlert.visibility     = View.GONE
            if (studentDocId.isEmpty()) loadStudentThenData() else loadData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (studentDocId.isNotEmpty()) {
            binding.layoutPayments.removeAllViews()
            loadData()
        }
    }
}