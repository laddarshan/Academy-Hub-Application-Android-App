package com.example.academyhub.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityTeacherAttendanceBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.models.TeacherAttendance
import com.example.academyhub.teacher.adapters.AttendanceAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class TeacherAttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherAttendanceBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var attendanceAdapter: AttendanceAdapter
    private var attendanceList = mutableListOf<TeacherAttendance>()
    private var teacherId: String = ""
    private var teacher: Teacher? = null
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherAttendanceBinding.inflate(layoutInflater)
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
        loadAttendanceRecords()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        attendanceAdapter = AttendanceAdapter(
            attendances = attendanceList,
            onItemClick = { attendance ->
                showAttendanceDetails(attendance)
            }
        )

        binding.rvAttendance.apply {
            layoutManager = LinearLayoutManager(this@TeacherAttendanceActivity)
            adapter = attendanceAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnFilterByDate.setOnClickListener {
            showDatePickerDialog()
        }

        binding.btnFilterByMonth.setOnClickListener {
            showMonthPickerDialog()
        }

        binding.btnClearFilter.setOnClickListener {
            loadAttendanceRecords()
        }
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
                        binding.tvTeacherSubject.text = it.subject
                    }
                }
            }
    }

    private fun loadAttendanceRecords() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("TeacherAttendance")
            .whereEqualTo("teacherId", teacherId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    attendanceList.clear()
                    for (document in snapshot.documents) {
                        val attendance = document.toObject(TeacherAttendance::class.java)
                        attendance?.let { attendanceList.add(it) }
                    }

                    attendanceAdapter.updateList(attendanceList)
                    updateStats()

                    binding.rvAttendance.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                } else {
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvAttendance.visibility = View.GONE
                }
            }
    }

    private fun updateStats() {
        val totalDays = attendanceList.size
        val presentDays = attendanceList.count { it.isPresent() }
        val absentDays = attendanceList.count { it.isAbsent() }
        val lateDays = attendanceList.count { it.isLate }
        val leaveDays = attendanceList.count { it.isOnLeave() }

        val attendancePercent = if (totalDays > 0) {
            (presentDays.toDouble() / totalDays.toDouble()) * 100
        } else 0.0

        binding.tvTotalDays.text = "Total: $totalDays"
        binding.tvPresentDays.text = "Present: $presentDays"
        binding.tvAbsentDays.text = "Absent: $absentDays"
        binding.tvLateDays.text = "Late: $lateDays"
        binding.tvLeaveDays.text = "Leave: $leaveDays"
        binding.tvAttendancePercent.text = String.format("%.1f%%", attendancePercent)

        // Update teacher's attendance percentage in Firestore
        if (totalDays > 0) {
            firestore.collection("Teachers")
                .document(teacherId)
                .update(
                    mapOf(
                        "attendancePercentage" to attendancePercent,
                        "classesAttended" to presentDays,
                        "totalClasses" to totalDays
                    )
                )
        }
    }

    private fun showDatePickerDialog() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val selectedDate = dateFormat.format(calendar.time)
                filterByDate(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showMonthPickerDialog() {
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Month")
            .setItems(months) { _, which ->
                filterByMonth(months[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterByDate(date: String) {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("TeacherAttendance")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE

                attendanceList.clear()
                for (document in snapshot.documents) {
                    val attendance = document.toObject(TeacherAttendance::class.java)
                    attendance?.let { attendanceList.add(it) }
                }

                attendanceAdapter.updateList(attendanceList)

                if (attendanceList.isEmpty()) {
                    binding.tvNoRecords.text = "No attendance record for $date"
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvAttendance.visibility = View.GONE
                } else {
                    binding.rvAttendance.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterByMonth(month: String) {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("TeacherAttendance")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("month", month)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE

                attendanceList.clear()
                for (document in snapshot.documents) {
                    val attendance = document.toObject(TeacherAttendance::class.java)
                    attendance?.let { attendanceList.add(it) }
                }

                attendanceAdapter.updateList(attendanceList)
                updateStats()

                if (attendanceList.isEmpty()) {
                    binding.tvNoRecords.text = "No attendance records for $month"
                    binding.tvNoRecords.visibility = View.VISIBLE
                    binding.rvAttendance.visibility = View.GONE
                } else {
                    binding.rvAttendance.visibility = View.VISIBLE
                    binding.tvNoRecords.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAttendanceDetails(attendance: TeacherAttendance) {
        val message = """
            Date: ${attendance.date} (${attendance.day})
            Status: ${attendance.status.uppercase()}
            
            Check In: ${attendance.checkInTime}
            Check Out: ${attendance.checkOutTime}
            Total Hours: ${attendance.getFormattedHours()}
            
            ${if (attendance.isLate) "Late by: ${attendance.lateMinutes} minutes" else ""}
            ${if (attendance.remarks.isNotEmpty()) "Remarks: ${attendance.remarks}" else ""}
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Attendance Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
