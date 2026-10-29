package com.example.academyhub.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityLeaveManagementBinding
import com.example.academyhub.models.Leave
import com.example.academyhub.models.Teacher
import com.example.academyhub.teacher.adapters.LeaveAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaveManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaveManagementBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var leaveAdapter: LeaveAdapter
    private var leaveList = mutableListOf<Leave>()
    private var teacherId: String = ""
    private var teacher: Teacher? = null
    private var filterStatus = "all" // all, pending, approved, rejected

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaveManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        if (teacherId.isEmpty()) {
            Toast.makeText(this, "Invalid teacher ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        loadTeacherInfo()
        loadLeaveRecords()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        leaveAdapter = LeaveAdapter(
            leaves = leaveList,
            onItemClick = { leave ->
                showLeaveDetailsDialog(leave)
            },
            onApproveClick = { leave ->
                approveLeave(leave)
            },
            onRejectClick = { leave ->
                rejectLeave(leave)
            }
        )

        binding.rvLeaves.apply {
            layoutManager = LinearLayoutManager(this@LeaveManagementActivity)
            adapter = leaveAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnApplyLeave.setOnClickListener {
            val intent = Intent(this, ApplyLeaveActivity::class.java)
            intent.putExtra("teacherId", teacherId)
            startActivity(intent)
        }

        // Filter chips
        binding.chipAll.setOnClickListener {
            filterStatus = "all"
            updateFilterChips()
            loadLeaveRecords()
        }

        binding.chipPending.setOnClickListener {
            filterStatus = "pending"
            updateFilterChips()
            loadLeaveRecords()
        }

        binding.chipApproved.setOnClickListener {
            filterStatus = "approved"
            updateFilterChips()
            loadLeaveRecords()
        }

        binding.chipRejected.setOnClickListener {
            filterStatus = "rejected"
            updateFilterChips()
            loadLeaveRecords()
        }
    }

    private fun updateFilterChips() {
        binding.chipAll.isChecked = filterStatus == "all"
        binding.chipPending.isChecked = filterStatus == "pending"
        binding.chipApproved.isChecked = filterStatus == "approved"
        binding.chipRejected.isChecked = filterStatus == "rejected"
    }

    private fun loadTeacherInfo() {
        firestore.collection("Teachers")
            .document(teacherId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    teacher = document.toObject(Teacher::class.java)
                    teacher?.let {
                        binding.tvTeacherName.text = it.name
                        binding.tvCasualLeave.text = "CL: ${it.getRemainingCasualLeave()}/${it.casualLeaveBalance}"
                        binding.tvSickLeave.text = "SL: ${it.getRemainingSickLeave()}/${it.sickLeaveBalance}"
                    }
                }
            }
    }

    private fun loadLeaveRecords() {
        binding.progressBar.visibility = View.VISIBLE

        var query = firestore.collection("Leaves")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("appliedAt", Query.Direction.DESCENDING)

        // Apply filter
        if (filterStatus != "all") {
            query = query.whereEqualTo("status", filterStatus) as Query
        }

        query.addSnapshotListener { snapshot, error ->
            binding.progressBar.visibility = View.GONE

            if (error != null) {
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                leaveList.clear()
                for (document in snapshot.documents) {
                    val leave = document.toObject(Leave::class.java)
                    leave?.let { leaveList.add(it) }
                }

                leaveAdapter.updateList(leaveList)
                updateStats()

                binding.rvLeaves.visibility = View.VISIBLE
                binding.tvNoRecords.visibility = View.GONE
            } else {
                binding.tvNoRecords.visibility = View.VISIBLE
                binding.rvLeaves.visibility = View.GONE
            }
        }
    }

    private fun updateStats() {
        val totalLeaves = leaveList.size
        val pendingLeaves = leaveList.count { it.isPending() }
        val approvedLeaves = leaveList.count { it.isApproved() }
        val rejectedLeaves = leaveList.count { it.isRejected() }
        val totalDays = leaveList.filter { it.isApproved() }.sumOf { it.totalDays }

        binding.tvTotalLeaves.text = "Total: $totalLeaves"
        binding.tvPendingLeaves.text = "Pending: $pendingLeaves"
        binding.tvApprovedLeaves.text = "Approved: $approvedLeaves ($totalDays days)"
        binding.tvRejectedLeaves.text = "Rejected: $rejectedLeaves"
    }

    private fun approveLeave(leave: Leave) {
        AlertDialog.Builder(this)
            .setTitle("Approve Leave")
            .setMessage("Approve ${leave.teacherName}'s leave request for ${leave.getDaysLabel()}?")
            .setPositiveButton("Approve") { _, _ ->
                val updates = hashMapOf<String, Any>(
                    "status" to "approved",
                    "approvedBy" to "Admin",
                    "approvedByName" to "Admin",
                    "approvedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("Leaves")
                    .document(leave.leaveId)
                    .update(updates)
                    .addOnSuccessListener {
                        // Update teacher's leave balance
                        updateTeacherLeaveBalance(leave, true)
                        Toast.makeText(this, "Leave approved", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rejectLeave(leave: Leave) {
        val view = layoutInflater.inflate(com.example.academyhub.R.layout.dialog_reject_leave, null)
        val etReason = view.findViewById<com.google.android.material.textfield.TextInputEditText>(
            com.example.academyhub.R.id.etRejectionReason
        )

        AlertDialog.Builder(this)
            .setTitle("Reject Leave")
            .setView(view)
            .setPositiveButton("Reject") { _, _ ->
                val reason = etReason.text.toString().trim()
                
                val updates = hashMapOf<String, Any>(
                    "status" to "rejected",
                    "approvedBy" to "Admin",
                    "approvedByName" to "Admin",
                    "rejectionReason" to reason,
                    "rejectedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("Leaves")
                    .document(leave.leaveId)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Leave rejected", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTeacherLeaveBalance(leave: Leave, isApproved: Boolean) {
        teacher?.let { teacher ->
            val updates = hashMapOf<String, Any>()

            when (leave.leaveType) {
                "casual" -> {
                    if (isApproved) {
                        updates["casualLeaveUsed"] = teacher.casualLeaveUsed + leave.totalDays
                    }
                }
                "sick" -> {
                    if (isApproved) {
                        updates["sickLeaveUsed"] = teacher.sickLeaveUsed + leave.totalDays
                    }
                }
            }

            if (updates.isNotEmpty()) {
                firestore.collection("Teachers")
                    .document(teacherId)
                    .update(updates)
            }
        }
    }

    private fun showLeaveDetailsDialog(leave: Leave) {
        val message = """
            Type: ${leave.getLeaveTypeLabel()}
            Duration: ${leave.getDaysLabel()}
            Date: ${leave.getDateRange()}
            
            Reason: ${leave.reason}
            
            Status: ${leave.status.uppercase()}
            ${if (leave.isApproved()) "Approved by: ${leave.approvedByName}" else ""}
            ${if (leave.isRejected()) "Rejected: ${leave.rejectionReason}" else ""}
            
            Applied on: ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(leave.appliedAt)}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Leave Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadTeacherInfo()
    }
}
