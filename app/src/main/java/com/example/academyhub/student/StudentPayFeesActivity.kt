package com.example.academyhub.student

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityStudentPayFeesBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentPayFeesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentPayFeesBinding
    private val db = FirebaseFirestore.getInstance()

    private var studentDocId = ""
    private var studentName  = ""
    private var courseId     = ""
    private var courseName   = ""
    private var semesterId   = ""
    private var semesterName = ""
    private var totalFees    = 0.0
    private var paidFees     = 0.0
    private var pendingFees  = 0.0

    private var selectedMode = "Cash"   // Cash / UPI / Cheque

    companion object {
        fun start(
            context: Context,
            studentDocId: String,
            studentName: String,
            courseId: String,
            courseName: String,
            semesterId: String,
            semesterName: String,
            totalFees: Double,
            paidFees: Double,
            pendingFees: Double
        ) {
            Intent(context, StudentPayFeesActivity::class.java).apply {
                putExtra("studentDocId", studentDocId)
                putExtra("studentName",  studentName)
                putExtra("courseId",     courseId)
                putExtra("courseName",   courseName)
                putExtra("semesterId",   semesterId)
                putExtra("semesterName", semesterName)
                putExtra("totalFees",    totalFees)
                putExtra("paidFees",     paidFees)
                putExtra("pendingFees",  pendingFees)
                context.startActivity(this)
            }
        }

        // Start from student object
        fun start(context: Context, student: Student, pending: Double) {
            start(
                context      = context,
                studentDocId = student.id,
                studentName  = student.name,
                courseId     = student.courseId,
                courseName   = student.courseName,
                semesterId   = student.semesterId,
                semesterName = student.semesterName,
                totalFees    = student.totalFees,
                paidFees     = student.paidFees,
                pendingFees  = pending
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentPayFeesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentDocId = intent.getStringExtra("studentDocId") ?: ""
        studentName  = intent.getStringExtra("studentName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        totalFees    = intent.getDoubleExtra("totalFees",    0.0)
        paidFees     = intent.getDoubleExtra("paidFees",     0.0)
        pendingFees  = intent.getDoubleExtra("pendingFees",  0.0)

        setupHeader()
        setupModeSelector()
        setupAmountChips()
        setupDatePicker()
        setupClicks()
        runEntranceAnim()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Header
    // ─────────────────────────────────────────────────────────────────────
    private fun setupHeader() {
        binding.tvHeaderStudentName.text = "$studentName  ›  $courseName"
        binding.tvDueAmount.text = "₹${fmt(pendingFees)}"
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Attr color helper  ← FIX: getColor(R.attr.*) crash bando
    // ─────────────────────────────────────────────────────────────────────
    private fun resolveAttrColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Mode selector — Cash / UPI / Cheque
    // ─────────────────────────────────────────────────────────────────────
    private fun setupModeSelector() {
        val modes = listOf(
            binding.modeCash   to "Cash",
            binding.modeUpi    to "UPI",
            binding.modeCheque to "Cheque"
        )
        // Set default Cash as active
        setModeStyle(binding.modeCash, true)
        setModeStyle(binding.modeUpi, false)
        setModeStyle(binding.modeCheque, false)

        modes.forEach { (view, mode) ->
            view.setOnClickListener { v ->
                animPress(v)
                selectedMode = mode
                modes.forEach { (mv, _) -> setModeStyle(mv, false) }
                setModeStyle(view, true)
                // Update receipt hint
                binding.etReceiptNumber.hint = when (mode) {
                    "UPI"    -> "UPI Transaction ID"
                    "Cheque" -> "Cheque Number"
                    else     -> "Receipt Number"
                }
            }
        }
    }

    private fun setModeStyle(view: TextView, active: Boolean) {
        if (active) {
            view.setBackgroundResource(com.example.academyhub.R.drawable.bg_chip_active)
            view.setTextColor(Color.WHITE)
        } else {
            view.setBackgroundResource(com.example.academyhub.R.drawable.bg_chip_outline_white)
            // ✅ FIX: resolveAttrColor() use karo, getColor(R.attr.*) nahi
            view.setTextColor(resolveAttrColor(com.example.academyhub.R.attr.colorTeal2))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Quick amount chips
    // ─────────────────────────────────────────────────────────────────────
    private fun setupAmountChips() {
        val chips = listOf(
            binding.chipPayFull   to "full",
            binding.chipPayHalf   to "half",
            binding.chipPayCustom to "custom"
        )

        // Default: custom selected
        setChipStyle(binding.chipPayCustom, true)
        setChipStyle(binding.chipPayFull,   false)
        setChipStyle(binding.chipPayHalf,   false)

        binding.chipPayFull.setOnClickListener { v ->
            animPress(v)
            chips.forEach { (c, _) -> setChipStyle(c, false) }
            setChipStyle(binding.chipPayFull, true)
            binding.etAmount.setText(fmt(pendingFees))
        }
        binding.chipPayHalf.setOnClickListener { v ->
            animPress(v)
            chips.forEach { (c, _) -> setChipStyle(c, false) }
            setChipStyle(binding.chipPayHalf, true)
            binding.etAmount.setText(fmt(pendingFees / 2.0))
        }
        binding.chipPayCustom.setOnClickListener { v ->
            animPress(v)
            chips.forEach { (c, _) -> setChipStyle(c, false) }
            setChipStyle(binding.chipPayCustom, true)
            binding.etAmount.text?.clear()
            binding.etAmount.requestFocus()
        }
    }

    private fun setChipStyle(view: TextView, active: Boolean) {
        if (active) {
            view.setBackgroundResource(com.example.academyhub.R.drawable.bg_chip_active)
            view.setTextColor(Color.WHITE)
        } else {
            view.setBackgroundResource(com.example.academyhub.R.drawable.bg_chip_outline_white)
            // ✅ FIX: resolveAttrColor() use karo, getColor(R.attr.*) nahi
            view.setTextColor(resolveAttrColor(com.example.academyhub.R.attr.colorTeal2))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Date picker
    // ─────────────────────────────────────────────────────────────────────
    private fun setupDatePicker() {
        // Default: today
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        binding.etPaymentDate.setText(today)

        binding.etPaymentDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    binding.etPaymentDate.setText(
                        String.format("%02d/%02d/%04d", d, m + 1, y)
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Submit payment
    // ─────────────────────────────────────────────────────────────────────
    private fun submitPayment() {
        val amtStr  = binding.etAmount.text.toString().trim()
        val dateStr = binding.etPaymentDate.text.toString().trim()
        val receipt = binding.etReceiptNumber.text.toString().trim()
        val remarks = binding.etRemarks.text.toString().trim()

        // Validation
        if (amtStr.isEmpty()) {
            binding.etAmount.error = "Amount enter karo"
            binding.etAmount.requestFocus()
            return
        }
        val amount = amtStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            binding.etAmount.error = "Valid amount enter karo"
            return
        }
        if (amount > pendingFees) {
            binding.etAmount.error = "Amount due (₹${fmt(pendingFees)}) thi vahu nahi hoy"
            return
        }
        if (dateStr.isEmpty()) {
            Toast.makeText(this, "Payment date select karo", Toast.LENGTH_SHORT).show()
            return
        }

        // Confirm dialog
        AlertDialog.Builder(this)
            .setTitle("Payment Confirm")
            .setMessage(
                "Student: $studentName\n" +
                        "Amount:  ₹${fmt(amount)}\n" +
                        "Mode:    $selectedMode\n" +
                        "Date:    $dateStr\n\n" +
                        "Confirm karvanu chhe?"
            )
            .setPositiveButton("Confirm") { _, _ ->
                savePayment(amount, dateStr, receipt, remarks)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePayment(
        amount: Double, paymentDate: String, receipt: String, remarks: String
    ) {
        setLoading(true)

        val now        = System.currentTimeMillis()
        val newPaid    = paidFees + amount
        val newPending = (totalFees - newPaid).coerceAtLeast(0.0)
        val newStatus  = when {
            newPaid >= totalFees && totalFees > 0 -> "paid"
            newPaid > 0                           -> "partial"
            else                                  -> "pending"
        }

        // 1. Save to Payments collection
        val paymentDoc = hashMapOf(
            "studentId"     to studentDocId,
            "studentName"   to studentName,
            "courseId"      to courseId,
            "courseName"    to courseName,
            "semesterId"    to semesterId,
            "semesterName"  to semesterName,
            "amount"        to amount,
            "paymentDate"   to paymentDate,
            "paymentMode"   to selectedMode,
            "receiptNumber" to receipt,
            "remarks"       to remarks,
            "createdAt"     to now
        )

        db.collection("Payments").add(paymentDoc)
            .addOnSuccessListener {
                // 2. Update Students doc — paidFees, feesStatus
                db.collection("Students").document(studentDocId)
                    .update(
                        mapOf(
                            "paidFees"        to newPaid,
                            "feesStatus"      to newStatus,
                            "lastPaymentDate" to paymentDate,
                            "updatedAt"       to now
                        )
                    )
                    .addOnSuccessListener {
                        setLoading(false)
                        showSuccessAndFinish(amount, newPaid, newPending)
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Toast.makeText(
                            this,
                            "Student update error: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    this,
                    "Payment save error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Success dialog
    // ─────────────────────────────────────────────────────────────────────
    private fun showSuccessAndFinish(paid: Double, totalPaid: Double, remaining: Double) {
        AlertDialog.Builder(this)
            .setTitle("✅ Payment Successful!")
            .setMessage(
                "₹${fmt(paid)} payment record thayi!\n\n" +
                        "Total Paid:  ₹${fmt(totalPaid)}\n" +
                        "Remaining:   ₹${fmt(remaining)}"
            )
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ ->
                setResult(RESULT_OK)
                finish()
            }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Loading state
    // ─────────────────────────────────────────────────────────────────────
    private fun setLoading(loading: Boolean) {
        binding.progressSubmit.visibility    = if (loading) View.VISIBLE else View.GONE
        binding.ivSubmitIcon.visibility      = if (loading) View.GONE    else View.VISIBLE
        binding.tvSubmitLabel.text           = if (loading) "Saving..."  else "Submit Payment"
        binding.btnSubmitPayment.isClickable = !loading
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Animations + clicks
    // ─────────────────────────────────────────────────────────────────────
    private fun runEntranceAnim() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()
    }

    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { v -> animPress(v) { finish() } }

        binding.btnSubmitPayment.setOnClickListener { v ->
            animPress(v) { submitPayment() }
        }
    }

    private fun fmt(a: Double) = String.format("%,.0f", a)
}