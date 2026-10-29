package com.example.academyhub.fees

import android.animation.*
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ActivityStudentFeesDetailsBinding
import com.example.academyhub.databinding.ItemPaymentHistoryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class StudentFeesDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentFeesDetailsBinding
    private lateinit var firestore: FirebaseFirestore

    private var studentId    = ""; private var studentName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var semesterId   = ""; private var semesterName = ""

    private var totalFees   = 0.0
    private var paidFees    = 0.0
    private var pendingFees = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentFeesDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        studentId    = intent.getStringExtra("studentId")    ?: ""
        studentName  = intent.getStringExtra("studentName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.btnBack.setOnClickListener { finish() }

        // Entrance animations for payment method cards
        val cards = listOf(binding.btnPayCash, binding.btnScanQr, binding.btnUpi,
                           binding.btnCard, binding.btnNetBanking)
        cards.forEachIndexed { idx, card ->
            card.alpha = 0f; card.translationY = 16f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(260).setStartDelay(200L + idx * 60L)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
        }

        setupPaymentButtons()
        setupPaymentHistory()
        loadStudentDetails()
    }

    // ─── Payment buttons ───────────────────────────────────────────────────────

    private fun setupPaymentButtons() {
        // Add Payment (Cash)
        binding.btnAddPayment.setOnClickListener {
            openPayment("cash")
        }
        binding.btnPayCash.setOnClickListener {
            animateTap(it) { openPayment("cash") }
        }
        binding.btnScanQr.setOnClickListener {
            animateTap(it) { openPayment("qr") }
        }
        binding.btnUpi.setOnClickListener {
            animateTap(it) { openPayment("upi") }
        }
        binding.btnCard.setOnClickListener {
            animateTap(it) { openPayment("card") }
        }
        binding.btnNetBanking.setOnClickListener {
            animateTap(it) { openPayment("netbanking") }
        }
    }

    private fun openPayment(method: String) {
        startActivity(Intent(this, PaymentActivity::class.java).apply {
            putExtra("studentId",    studentId)
            putExtra("studentName",  studentName)
            putExtra("courseId",     courseId)
            putExtra("courseName",   courseName)
            putExtra("semesterId",   semesterId)
            putExtra("semesterName", semesterName)
            putExtra("totalFees",    totalFees)
            putExtra("paidFees",     paidFees)
            putExtra("pendingFees",  pendingFees)
            putExtra("paymentMethod", method)
        })
    }

    private fun animateTap(v: View, action: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.95f, 1f).apply { duration = 130 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.95f, 1f).apply { duration = 130 }
            ); start()
        }
        v.postDelayed(action, 110)
    }

    // ─── Load student details ──────────────────────────────────────────────────

    private fun loadStudentDetails() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Students").document(studentId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                if (!doc.exists()) return@addOnSuccessListener

                val name    = doc.getString("name") ?: ""
                val roll    = doc.getString("rollNumber") ?: ""
                totalFees   = doc.getDouble("totalFees") ?: 0.0
                paidFees    = doc.getDouble("paidFees")  ?: 0.0
                pendingFees = (totalFees - paidFees).coerceAtLeast(0.0)
                val pct     = if (totalFees > 0) ((paidFees / totalFees) * 100).toInt() else 0

                binding.tvStudentName.text = name
                binding.tvRollCourse.text  = "Roll: $roll  |  $courseName  \u203A  $semesterName"

                // ValueAnimator for fees amounts
                ValueAnimator.ofFloat(0f, totalFees.toFloat()).apply {
                    duration = 700; interpolator = DecelerateInterpolator()
                    addUpdateListener { binding.tvTotalFees.text = "₹${fmt((it.animatedValue as Float).toDouble())}" }
                    start()
                }
                ValueAnimator.ofFloat(0f, paidFees.toFloat()).apply {
                    duration = 700; interpolator = DecelerateInterpolator()
                    addUpdateListener { binding.tvPaidFees.text = "₹${fmt((it.animatedValue as Float).toDouble())}" }
                    start()
                }
                ValueAnimator.ofFloat(0f, pendingFees.toFloat()).apply {
                    duration = 700; interpolator = DecelerateInterpolator()
                    addUpdateListener { binding.tvPendingFees.text = "₹${fmt((it.animatedValue as Float).toDouble())}" }
                    start()
                }

                // Animate progress bar
                ObjectAnimator.ofInt(binding.progressFees, "progress", 0, pct).apply {
                    duration = 800; interpolator = DecelerateInterpolator(); start()
                }
                binding.tvPaidPercent.text = "$pct% Paid"

                // Status
                val status = doc.getString("feesStatus") ?: "pending"
                val (statusText, statusColor) = when (status) {
                    "paid"    -> "Fully Paid"    to "#075E54"
                    "partial" -> "Partially Paid" to "#E65100"
                    else      -> "Pending"         to "#C62828"
                }
                binding.tvFeesStatus.text = statusText
                binding.tvFeesStatus.setTextColor(Color.parseColor(statusColor))
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading student", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Payment History ───────────────────────────────────────────────────────

    private fun setupPaymentHistory() {
        binding.rvPaymentHistory.layoutManager = LinearLayoutManager(this)

        firestore.collection("Payments")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("semesterId", semesterId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener

                val payments = snap?.documents?.mapNotNull { doc ->
                    val amt    = doc.getDouble("amount") ?: 0.0
                    val date   = doc.getString("paymentDate") ?: ""
                    val mode   = doc.getString("paymentMode") ?: ""
                    val rcpt   = doc.getString("receiptNumber") ?: ""
                    PaymentRecord(doc.id, amt, date, mode, rcpt)
                } ?: emptyList()

                if (payments.isEmpty()) {
                    binding.tvNoPayments.visibility     = View.VISIBLE
                    binding.rvPaymentHistory.visibility = View.GONE
                } else {
                    binding.tvNoPayments.visibility     = View.GONE
                    binding.rvPaymentHistory.visibility = View.VISIBLE
                    binding.rvPaymentHistory.adapter    = PaymentHistoryAdapter(payments)
                }
            }
    }

    // ─── Payment History Adapter ───────────────────────────────────────────────

    data class PaymentRecord(
        val id: String, val amount: Double, val date: String,
        val mode: String, val receiptNumber: String
    )

    inner class PaymentHistoryAdapter(private val list: List<PaymentRecord>) :
        RecyclerView.Adapter<PaymentHistoryAdapter.VH>() {

        inner class VH(val b: ItemPaymentHistoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            VH(ItemPaymentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val p = list[pos]
            holder.b.tvPayAmount.text = "₹${fmt(p.amount)}"
            holder.b.tvPayDate.text   = p.date
            holder.b.tvPayMode.text   = p.mode.replaceFirstChar { it.titlecase() }
            holder.b.tvPayReceipt.text = if (p.receiptNumber.isNotEmpty()) "Rcpt: ${p.receiptNumber}" else ""
            // stagger
            holder.b.root.alpha = 0f
            holder.b.root.animate().alpha(1f).setDuration(220).setStartDelay(pos * 50L).start()
        }
    }

    private fun fmt(a: Double) = String.format("%,.0f", a)

    override fun onResume() { super.onResume(); loadStudentDetails() }
}
