package com.example.academyhub.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityMarkAttendanceBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.models.TeacherAttendance
import com.example.academyhub.teacher.adapters.MarkAttendanceAdapter
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MarkAttendanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMarkAttendanceBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: MarkAttendanceAdapter
    private val teachersList = mutableListOf<Teacher>()
    private val calendar     = Calendar.getInstance()
    private val dateFormat   = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dayFormat    = SimpleDateFormat("EEEE", Locale.getDefault())
    private val monthFormat  = SimpleDateFormat("MMMM", Locale.getDefault())
    private var selectedDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityMarkAttendanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        selectedDate = dateFormat.format(calendar.time)

        setupRecyclerView()
        setupClickListeners()
        updateDateDisplay()
        loadActiveTeachers()   // Load teachers first, then attendance
    }

    private fun setupRecyclerView() {
        adapter = MarkAttendanceAdapter(
            teachers        = teachersList,
            selectedDate    = selectedDate,
            onStatusChanged = { teacher, status -> autoSaveAttendance(teacher, status) }
        )
        binding.rvTeachers.apply {
            layoutManager = LinearLayoutManager(this@MarkAttendanceActivity)
            adapter = this@MarkAttendanceActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Date picker - can select any past or current date
        binding.btnSelectDate.setOnClickListener {
            val dpd = DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDate = dateFormat.format(calendar.time)
                    updateDateDisplay()
                    adapter.updateDate(selectedDate)
                    loadExistingAttendanceForDate()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            // Only allow up to today (no future dates)
            dpd.datePicker.maxDate = System.currentTimeMillis()
            dpd.show()
        }

        binding.btnMarkAllPresent.setOnClickListener {
            if (teachersList.isEmpty()) {
                Toast.makeText(this, "No teachers loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            adapter.markAllAs("present")
            Toast.makeText(this, "All marked as PRESENT", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveAttendance.setOnClickListener { saveAllAttendance() }
    }

    private fun updateDateDisplay() {
        binding.tvSelectedDate.text = selectedDate
        binding.tvDay.text = dayFormat.format(calendar.time)
    }

    // ─────────────────────────────────────────────────────
    // FIX 1: Load active teachers using correct field "status"
    // (Teacher model stores: status = "active" / "inactive")
    // ManageCourses stores: isActive = true/false (that's Course model, different!)
    // ─────────────────────────────────────────────────────
    private fun loadActiveTeachers() {
        binding.progressBar.visibility  = View.VISIBLE
        binding.tvNoTeachers.visibility = View.GONE

        firestore.collection("Teachers")
            .whereEqualTo("status", "active")   // Teacher model uses "status" field
            .orderBy("name")
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE

                teachersList.clear()
                for (doc in snapshot.documents) {
                    val teacher = doc.toObject(Teacher::class.java)
                    teacher?.let { teachersList.add(it) }
                }

                if (teachersList.isEmpty()) {
                    binding.tvNoTeachers.text    = "No active teachers found"
                    binding.tvNoTeachers.visibility = View.VISIBLE
                    binding.rvTeachers.visibility   = View.GONE
                    return@addOnSuccessListener
                }

                binding.tvTeacherCount.text = "${teachersList.size} Active Teachers"
                adapter.updateTeachers(teachersList)
                binding.rvTeachers.visibility   = View.VISIBLE
                binding.tvNoTeachers.visibility = View.GONE

                // Load existing attendance for today
                loadExistingAttendanceForDate()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────────────────────────────────
    // FIX 2: Load existing attendance and pass to adapter
    // ─────────────────────────────────────────────────────
    private fun loadExistingAttendanceForDate() {
        if (teachersList.isEmpty()) return

        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("TeacherAttendance")
            .whereEqualTo("date", selectedDate)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE

                val attendanceMap = mutableMapOf<String, TeacherAttendance>()
                for (doc in snapshot.documents) {
                    val att = doc.toObject(TeacherAttendance::class.java)
                    att?.let { attendanceMap[it.teacherId] = it }
                }

                // Pass existing attendance to adapter so radio buttons reflect saved state
                adapter.updateExistingAttendance(attendanceMap)

                val count = attendanceMap.size
                if (count > 0) {
                    binding.tvAttendanceStatus.text = "$count of ${teachersList.size} already marked"
                    binding.tvAttendanceStatus.visibility = View.VISIBLE
                } else {
                    binding.tvAttendanceStatus.text = "No attendance marked yet for $selectedDate"
                    binding.tvAttendanceStatus.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Auto-save when teacher clicks a radio button
    private fun autoSaveAttendance(teacher: Teacher, status: String) {
        val attId = buildAttendanceId(teacher.id, selectedDate)
        val att   = buildAttendanceObject(teacher, status, attId)

        firestore.collection("TeacherAttendance").document(attId).set(att)
            // No toast on auto-save, silent save
            .addOnFailureListener { e ->
                Toast.makeText(this, "Auto-save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveAllAttendance() {
        if (teachersList.isEmpty()) {
            Toast.makeText(this, "No teachers to save", Toast.LENGTH_SHORT).show()
            return
        }

        val data = adapter.getAttendanceData() // Map<Teacher, String>
        if (data.isEmpty()) {
            Toast.makeText(this, "Please mark attendance for at least one teacher", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSaveAttendance.isEnabled = false

        var saved = 0; var failed = 0
        val total = data.size

        data.forEach { (teacher, status) ->
            val attId = buildAttendanceId(teacher.id, selectedDate)
            val att   = buildAttendanceObject(teacher, status, attId)

            firestore.collection("TeacherAttendance").document(attId).set(att)
                .addOnSuccessListener {
                    saved++
                    if (saved + failed == total) onSaveDone(saved, failed, total)
                }
                .addOnFailureListener {
                    failed++
                    if (saved + failed == total) onSaveDone(saved, failed, total)
                }
        }
    }

    private fun onSaveDone(saved: Int, failed: Int, total: Int) {
        binding.progressBar.visibility = View.GONE
        binding.btnSaveAttendance.isEnabled = true

        if (failed == 0) {
            Toast.makeText(this, "Attendance saved for all $saved teachers!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Saved: $saved, Failed: $failed of $total", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildAttendanceId(teacherId: String, date: String): String {
        return "${teacherId}_${date.replace("/", "")}"
    }

    private fun buildAttendanceObject(
        teacher: Teacher, status: String, attId: String
    ): TeacherAttendance {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return TeacherAttendance(
            attendanceId = attId,
            teacherId    = teacher.id,
            teacherName  = teacher.name,
            date         = selectedDate,
            day          = dayFormat.format(calendar.time),
            month        = monthFormat.format(calendar.time),
            year         = calendar.get(Calendar.YEAR),
            status       = status,
            checkInTime  = if (status == "present" || status == "late")
                timeFormat.format(Date()) else "",
            checkOutTime = "",
            totalHours   = 0.0,
            markedBy     = "Admin",
            timestamp    = System.currentTimeMillis()
        )
    }
}
