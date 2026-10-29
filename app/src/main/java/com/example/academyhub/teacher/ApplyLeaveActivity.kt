package com.example.academyhub.teacher

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityApplyLeaveBinding
import com.example.academyhub.models.Leave
import com.example.academyhub.models.Teacher
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ApplyLeaveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApplyLeaveBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var teacherId: String = ""
    private var teacher: Teacher? = null
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var fromDate = ""
    private var toDate = ""
    private var documentUri: Uri? = null

    // Document picker launcher
    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            documentUri = it
            binding.tvDocumentName.text = getFileName(it)
            binding.tvDocumentName.visibility = View.VISIBLE
            Toast.makeText(this, "Document selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplyLeaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        if (teacherId.isEmpty()) {
            Toast.makeText(this, "Invalid teacher ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupDropdowns()
        loadTeacherInfo()
        setupClickListeners()
    }

    private fun setupDropdowns() {
        val leaveTypes = arrayOf("Casual Leave", "Sick Leave", "Emergency Leave")
        val leaveTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, leaveTypes)
        binding.spinnerLeaveType.setAdapter(leaveTypeAdapter)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.etFromDate.setOnClickListener {
            showDatePicker(true)
        }

        binding.etToDate.setOnClickListener {
            showDatePicker(false)
        }

        binding.btnUploadDocument.setOnClickListener {
            documentPickerLauncher.launch("*/*")
        }

        binding.checkboxHalfDay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.etToDate.setText(binding.etFromDate.text.toString())
                binding.etToDate.isEnabled = false
            } else {
                binding.etToDate.isEnabled = true
            }
            calculateTotalDays()
        }

        binding.btnSubmit.setOnClickListener {
            if (validateInputs()) {
                submitLeaveRequest()
            }
        }
    }

    private fun loadTeacherInfo() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Teachers")
            .document(teacherId)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE

                if (document.exists()) {
                    teacher = document.toObject(Teacher::class.java)
                    teacher?.let {
                        binding.tvTeacherName.text = it.name
                        binding.tvEmail.text = it.email
                        binding.tvMobile.text = it.mobile
                        binding.tvCasualLeaveBalance.text = "Available: ${it.getRemainingCasualLeave()}"
                        binding.tvSickLeaveBalance.text = "Available: ${it.getRemainingSickLeave()}"
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker(isFromDate: Boolean) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                val selectedDate = dateFormat.format(calendar.time)

                if (isFromDate) {
                    fromDate = selectedDate
                    binding.etFromDate.setText(selectedDate)
                    if (binding.checkboxHalfDay.isChecked) {
                        toDate = selectedDate
                        binding.etToDate.setText(selectedDate)
                    }
                } else {
                    toDate = selectedDate
                    binding.etToDate.setText(selectedDate)
                }
                calculateTotalDays()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun calculateTotalDays() {
        if (fromDate.isNotEmpty() && toDate.isNotEmpty()) {
            try {
                val from = dateFormat.parse(fromDate)
                val to = dateFormat.parse(toDate)

                if (from != null && to != null) {
                    val diff = to.time - from.time
                    val days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) + 1

                    binding.tvTotalDays.text = if (binding.checkboxHalfDay.isChecked) {
                        "0.5 days"
                    } else {
                        "$days day(s)"
                    }
                    binding.tvTotalDays.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (binding.spinnerLeaveType.text.toString().isEmpty()) {
            Toast.makeText(this, "Please select leave type", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.etFromDate.text.toString().isEmpty()) {
            binding.etFromDate.error = "Required"
            return false
        }
        if (binding.etToDate.text.toString().isEmpty()) {
            binding.etToDate.error = "Required"
            return false
        }
        if (binding.etReason.text.toString().trim().isEmpty()) {
            binding.etReason.error = "Reason is required"
            return false
        }

        val leaveType = binding.spinnerLeaveType.text.toString()
        val totalDays = calculateDaysCount().toDouble()

        teacher?.let {
            if (leaveType.contains("Casual") && totalDays > it.getRemainingCasualLeave()) {
                Toast.makeText(this, "Insufficient casual leave balance", Toast.LENGTH_LONG).show()
                return false
            }
            if (leaveType.contains("Sick") && totalDays > it.getRemainingSickLeave()) {
                Toast.makeText(this, "Insufficient sick leave balance", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun calculateDaysCount(): Int {
        if (binding.checkboxHalfDay.isChecked) return 1 // Logic treats half day as 1 for balance check, or adjust as needed
        return try {
            val from = dateFormat.parse(fromDate)
            val to = dateFormat.parse(toDate)
            if (from != null && to != null) {
                val diff = to.time - from.time
                (TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS) + 1).toInt()
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun submitLeaveRequest() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val leaveId = firestore.collection("Leaves").document().id
        val leaveTypeStr = binding.spinnerLeaveType.text.toString()
        val leaveType = when {
            leaveTypeStr.contains("Casual") -> "casual"
            leaveTypeStr.contains("Sick") -> "sick"
            else -> "emergency"
        }

        val leave = Leave(
            leaveId = leaveId,
            teacherId = teacherId,
            teacherName = teacher?.name ?: "",
            teacherEmail = teacher?.email ?: "",
            teacherMobile = teacher?.mobile ?: "",
            leaveType = leaveType,
            fromDate = fromDate,
            toDate = toDate,
            totalDays = calculateDaysCount(),
            halfDay = binding.checkboxHalfDay.isChecked,
            reason = binding.etReason.text.toString().trim(),
            status = "pending",
            appliedAt = System.currentTimeMillis()
        )

        if (documentUri != null) {
            uploadDocument(leave)
        } else {
            saveLeaveRequest(leave)
        }
    }

    private fun uploadDocument(leave: Leave) {
        documentUri?.let { uri ->
            val fileName = "leave_${leave.leaveId}_${System.currentTimeMillis()}"
            val storageRef = storage.reference.child("leave_documents/$fileName")

            storageRef.putFile(uri)
                .addOnSuccessListener { taskSnapshot ->
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        leave.documentUrl = downloadUri.toString()
                        leave.documentName = getFileName(uri)
                        saveLeaveRequest(leave)
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this, "Document upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    saveLeaveRequest(leave)
                }
        }
    }

    private fun saveLeaveRequest(leave: Leave) {
        firestore.collection("Leaves")
            .document(leave.leaveId)
            .set(leave)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
                Toast.makeText(this, "Leave request submitted successfully", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "document"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    // સુધારેલું: Exhaustive when error fix
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}