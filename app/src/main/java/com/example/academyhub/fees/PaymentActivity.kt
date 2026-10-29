package com.example.academyhub.fees

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityPaymentBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private lateinit var firestore: FirebaseFirestore

    private var studentId    = ""; private var studentName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var semesterId   = ""; private var semesterName = ""
    private var totalFees    = 0.0
    private var paidFees     = 0.0
    private var pendingFees  = 0.0
    private var paymentMethod = "cash"
    private var selectedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        studentId    = intent.getStringExtra("studentId")     ?: ""
        studentName  = intent.getStringExtra("studentName")   ?: ""
        courseId     = intent.getStringExtra("courseId")      ?: ""
        courseName   = intent.getStringExtra("courseName")    ?: ""
        semesterId   = intent.getStringExtra("semesterId")    ?: ""
        semesterName = intent.getStringExtra("semesterName")  ?: ""
        totalFees    = intent.getDoubleExtra("totalFees",    0.0)
        paidFees     = intent.getDoubleExtra("paidFees",     0.0)
        pendingFees  = intent.getDoubleExtra("pendingFees",  0.0)
        paymentMethod = intent.getStringExtra("paymentMethod") ?: "cash"

        binding.tvStudentBadge.text    = "$studentName  \u203A  $courseName  \u203A  $semesterName"
        binding.tvPendingAmount.text   = "₹${String.format("%,.0f", pendingFees)}"
        binding.tvPaymentMode.text     = paymentMethod.replaceFirstChar { it.titlecase() }

        setupMethodPanels()
        setupDatePicker()
        setupSaveButton()

        binding.btnBack.setOnClickListener { finish() }

        // Entrance animation
        binding.btnRecordPayment.alpha = 0f; binding.btnRecordPayment.translationY = 24f
        binding.btnRecordPayment.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200).setInterpolator(OvershootInterpolator(0.8f)).start()

        // Set today as default date
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        selectedDate = sdf.format(cal.time)
        binding.tvPaymentDate.text = selectedDate
        binding.tvPaymentDate.setTextColor(getColor(android.R.color.darker_gray).also {})
        binding.tvPaymentDate.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, com.example.academyhub.R.color.primary_color)
        )
    }

    private fun setupMethodPanels() {
        // Title
        val titleMap = mapOf(
            "cash"       to "Cash Payment",
            "qr"         to "Scan QR Code",
            "upi"        to "UPI Payment",
            "card"       to "Card Payment",
            "netbanking" to "Net Banking"
        )
        binding.tvTitle.text    = titleMap[paymentMethod] ?: "Payment"
        binding.tvFormLabel.text = "Record ${titleMap[paymentMethod] ?: "Payment"}"

        // Show online info banner for non-cash
        if (paymentMethod != "cash") {
            binding.layoutOnlineInfo.visibility = View.VISIBLE
        }

        when (paymentMethod) {
            "qr" -> {
                binding.layoutQr.visibility = View.VISIBLE
                // Admin can set QR image via Firebase Storage — placeholder shown
                // TODO: Load QR image from Firebase Storage
            }
            "upi" -> {
                binding.layoutUpi.visibility = View.VISIBLE
                // TODO: Load UPI ID from settings collection
                binding.tvUpiId.text = "college@upi"   // Admin sets this
            }
            "card" -> binding.layoutCard.visibility = View.VISIBLE
            "netbanking" -> {
                binding.layoutNetBanking.visibility = View.VISIBLE
                // TODO: Load bank details from settings
                binding.tvBankDetails.text = "Bank: State Bank\nAccount: XXXX XXXX\nIFSC: SBIN0000001"
            }
        }
    }

    private fun setupDatePicker() {
        binding.rowPaymentDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDate = String.format("%02d/%02d/%04d", d, m + 1, y)
                binding.tvPaymentDate.text = selectedDate
                binding.tvPaymentDate.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, com.example.academyhub.R.color.primary_color)
                )
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupSaveButton() {
        binding.btnRecordPayment.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
                ); start()
            }
            it.postDelayed({ recordPayment() }, 130)
        }
    }

    private fun recordPayment() {
        val amountStr = binding.etAmount.text.toString().trim()
        if (amountStr.isEmpty()) { binding.etAmount.error = "Enter amount"; return }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) { binding.etAmount.error = "Invalid amount"; return }
        if (amount > pendingFees && pendingFees > 0) {
            Toast.makeText(this, "Amount cannot exceed pending fees (₹${String.format("%,.0f", pendingFees)})", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDate.isEmpty()) { Toast.makeText(this, "Select payment date", Toast.LENGTH_SHORT).show(); return }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRecordPayment.isEnabled = false

        val paymentId = firestore.collection("Payments").document().id
        val payment = hashMapOf(
            "id"            to paymentId,
            "studentId"     to studentId,
            "studentName"   to studentName,
            "courseId"      to courseId,
            "semesterId"    to semesterId,
            "amount"        to amount,
            "paymentDate"   to selectedDate,
            "paymentMode"   to paymentMethod,
            "receiptNumber" to generateReceipt(),
            "notes"         to binding.etNotes.text.toString().trim(),
            "createdAt"     to System.currentTimeMillis()
        )

        firestore.collection("Payments").document(paymentId).set(payment)
            .addOnSuccessListener {
                // Update student paidFees
                val newPaid    = paidFees + amount
                val newPending = (totalFees - newPaid).coerceAtLeast(0.0)
                val newStatus  = when {
                    newPaid >= totalFees -> "paid"
                    newPaid > 0          -> "partial"
                    else                 -> "pending"
                }
                firestore.collection("Students").document(studentId).update(mapOf(
                    "paidFees"    to newPaid,
                    "feesStatus"  to newStatus,
                    "updatedAt"   to System.currentTimeMillis()
                )).addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this,
                        "Payment of ₹${String.format("%,.0f", amount)} recorded!",
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnRecordPayment.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateReceipt(): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return "RCP${sdf.format(Date())}${(1000..9999).random()}"
    }
}
