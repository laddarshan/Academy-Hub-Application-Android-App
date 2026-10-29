package com.example.academyhub.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityTeacherSalaryBinding
import com.example.academyhub.models.Salary
import com.example.academyhub.models.Teacher
import com.example.academyhub.teacher.adapters.SalaryAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class TeacherSalaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherSalaryBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var salaryAdapter: SalaryAdapter
    private val salaryList = mutableListOf<Salary>()
    private var teacherId  = ""
    private var teacher: Teacher? = null

    private val MONTH_NAMES = arrayOf(
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityTeacherSalaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        if (teacherId.isEmpty()) { finish(); return }

        setupRecyclerView()
        loadTeacherInfo()
        loadSalaryRecords()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        salaryAdapter = SalaryAdapter(
            salaries        = salaryList,
            onItemClick     = { showSalaryDetailsDialog(it) },
            onMarkPaidClick = { markSalaryAsPaid(it) }
        )
        binding.rvSalary.apply {
            layoutManager = LinearLayoutManager(this@TeacherSalaryActivity)
            adapter = salaryAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddSalary.setOnClickListener { showMonthPickerThenSalaryDialog() }
    }

    private fun loadTeacherInfo() {
        firestore.collection("Teachers").document(teacherId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    teacher = doc.toObject(Teacher::class.java)
                    teacher?.let {
                        binding.tvTeacherName.text = it.name
                        binding.tvBasicSalary.text = "Basic: ₹${String.format("%,.0f", it.basicSalary)}"
                        binding.tvAllowances.text   = "Allow: ₹${String.format("%,.0f", it.allowances)}"
                        binding.tvTotalSalary.text  = "Total: ₹${String.format("%,.0f", it.currentSalary)}"
                    }
                }
            }
    }

    private fun loadSalaryRecords() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Salaries")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("year", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                salaryList.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val s = doc.toObject(Salary::class.java)
                        s?.let { salaryList.add(it) }
                    }
                    // Sort by year desc, then month number desc
                    salaryList.sortWith(compareByDescending<Salary> { it.year }
                        .thenByDescending { it.monthNumber })

                    salaryAdapter.updateList(salaryList)
                    updateStats()
                    binding.rvSalary.visibility    = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                } else {
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvSalary.visibility   = View.GONE
                }
            }
    }

    private fun updateStats() {
        val totalPaid    = salaryList.filter { it.status == "paid" }.sumOf { it.netSalary }
        val totalPending = salaryList.filter { it.status == "pending" }.sumOf { it.netSalary }
        val paidCount    = salaryList.count { it.status == "paid" }
        val pendingCount = salaryList.count { it.status == "pending" }

        binding.tvTotalPaid.text    = "Paid: ₹${String.format("%,.0f", totalPaid)} ($paidCount)"
        binding.tvTotalPending.text = "Pending: ₹${String.format("%,.0f", totalPending)} ($pendingCount)"
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 1: Month picker (any past month, not just current)
    // ─────────────────────────────────────────────────────────────
    private fun showMonthPickerThenSalaryDialog() {
        // Build last 24 months (including current)
        val monthYearLabels = mutableListOf<String>()
        val monthYearData   = mutableListOf<Pair<String, Int>>() // month name, year

        for (i in 0..23) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -i)
            val m = cal.get(Calendar.MONTH)
            val y = cal.get(Calendar.YEAR)
            monthYearLabels.add("${MONTH_NAMES[m]} $y")
            monthYearData.add(Pair(MONTH_NAMES[m], y))
        }

        var selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("Select Month for Salary")
            .setSingleChoiceItems(monthYearLabels.toTypedArray(), 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Next") { _, _ ->
                val (selectedMonth, selectedYear) = monthYearData[selectedIndex]

                // Check if salary already added for this month
                val alreadyExists = salaryList.find {
                    it.month == selectedMonth && it.year == selectedYear
                }
                if (alreadyExists != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Already Exists")
                        .setMessage("Salary for $selectedMonth $selectedYear is already added.\n\nStatus: ${alreadyExists.status.uppercase()}\nNet: ₹${String.format("%,.0f", alreadyExists.netSalary)}")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }

                // STEP 2: Show salary details form
                showSalaryDetailsForm(selectedMonth, selectedYear)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // STEP 2: Salary details form
    // ─────────────────────────────────────────────────────────────
    private fun showSalaryDetailsForm(month: String, year: Int) {
        val view = LayoutInflater.from(this).inflate(
            com.example.academyhub.R.layout.dialog_add_salary, null
        )

        val etBasicSalary = view.findViewById<TextInputEditText>(com.example.academyhub.R.id.etBasicSalary)
        val etAllowances  = view.findViewById<TextInputEditText>(com.example.academyhub.R.id.etAllowances)
        val etBonuses     = view.findViewById<TextInputEditText>(com.example.academyhub.R.id.etBonuses)
        val etDeductions  = view.findViewById<TextInputEditText>(com.example.academyhub.R.id.etDeductions)
        val etRemarks     = view.findViewById<TextInputEditText>(com.example.academyhub.R.id.etRemarks)

        // Pre-fill from teacher profile
        teacher?.let {
            etBasicSalary.setText(it.basicSalary.toString())
            etAllowances.setText(it.allowances.toString())
        }

        AlertDialog.Builder(this)
            .setTitle("Salary — $month $year")
            .setView(view)
            .setPositiveButton("Add Record") { _, _ ->
                val basic      = etBasicSalary.text.toString().toDoubleOrNull() ?: 0.0
                val allowances = etAllowances.text.toString().toDoubleOrNull() ?: 0.0
                val bonuses    = etBonuses.text.toString().toDoubleOrNull() ?: 0.0
                val deductions = etDeductions.text.toString().toDoubleOrNull() ?: 0.0
                val remarks    = etRemarks.text.toString().trim()

                if (basic <= 0.0) {
                    Toast.makeText(this, "Basic salary required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                addSalaryRecord(month, year, basic, allowances, bonuses, deductions, remarks)
            }
            .setNegativeButton("Back") { _, _ ->
                // Go back to month picker
                showMonthPickerThenSalaryDialog()
            }
            .show()
    }

    private fun addSalaryRecord(
        month: String, year: Int,
        basic: Double, allowances: Double,
        bonuses: Double, deductions: Double, remarks: String
    ) {
        val salaryId  = firestore.collection("Salaries").document().id
        val netSalary = basic + allowances + bonuses - deductions

        val salary = Salary(
            salaryId    = salaryId,
            teacherId   = teacherId,
            teacherName = teacher?.name ?: "",
            month       = month,
            year        = year,
            monthNumber = MONTH_NAMES.indexOf(month) + 1,
            basicSalary = basic,
            allowances  = allowances,
            bonuses     = bonuses,
            deductions  = deductions,
            netSalary   = netSalary,
            status      = "pending",
            remarks     = remarks,
            createdAt   = System.currentTimeMillis()
        )

        firestore.collection("Salaries").document(salaryId).set(salary)
            .addOnSuccessListener {
                Toast.makeText(this, "Salary for $month $year added", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun markSalaryAsPaid(salary: Salary) {
        AlertDialog.Builder(this)
            .setTitle("Mark as Paid")
            .setMessage("Mark ${salary.month} ${salary.year} salary as paid?\nNet Amount: ₹${String.format("%,.0f", salary.netSalary)}")
            .setPositiveButton("Yes, Mark Paid") { _, _ ->
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val updates = hashMapOf<String, Any>(
                    "status"      to "paid",
                    "paymentDate" to dateStr,
                    "paidAt"      to System.currentTimeMillis(),
                    "paidBy"      to "Admin"
                )
                firestore.collection("Salaries").document(salary.salaryId).update(updates)
                    .addOnSuccessListener {
                        // Update lastSalaryDate on teacher
                        firestore.collection("Teachers").document(teacherId)
                            .update("lastSalaryDate", dateStr)
                        Toast.makeText(this, "Salary marked as paid", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSalaryDetailsDialog(salary: Salary) {
        val msg = """
            Month: ${salary.month} ${salary.year}
            ─────────────────────────
            Basic Salary : ₹${String.format("%,.2f", salary.basicSalary)}
            Allowances   : ₹${String.format("%,.2f", salary.allowances)}
            Bonuses      : ₹${String.format("%,.2f", salary.bonuses)}
            Deductions   : ₹${String.format("%,.2f", salary.deductions)}
            ─────────────────────────
            Net Salary   : ₹${String.format("%,.2f", salary.netSalary)}
            
            Status: ${salary.status.uppercase()}
            ${if (salary.status == "paid") "Payment Date: ${salary.paymentDate}" else ""}
            ${if (salary.remarks.isNotEmpty()) "Remarks: ${salary.remarks}" else ""}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Salary Details")
            .setMessage(msg)
            .setPositiveButton("OK", null).show()
    }
}
