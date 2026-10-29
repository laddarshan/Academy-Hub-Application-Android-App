package com.example.academyhub.timetable

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAddTimetableBinding
import com.example.academyhub.models.Batch
import com.example.academyhub.models.Course
import com.example.academyhub.models.Semester
import com.example.academyhub.models.Subject
import com.example.academyhub.models.Timetable
import com.example.academyhub.ui.PickerBottomSheet
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTimetableBinding
    private lateinit var firestore: FirebaseFirestore

    private var isEditMode  = false
    private var timetableId = ""
    private val cal = Calendar.getInstance()

    // State
    private var selectedCourseId     = ""; private var selectedCourseName   = ""
    private var selectedSemesterId   = ""; private var selectedSemesterName = ""
    private var selectedBatchId      = ""; private var selectedBatchName    = ""
    private var selectedTeacherId    = ""; private var selectedTeacherName  = ""
    private var selectedSubjectId    = ""; private var selectedSubjectName  = ""
    private var selectedDay          = ""
    private var selectedType         = ""
    private var startTimeStr         = ""
    private var endTimeStr           = ""

    // Data
    private val coursesList   = mutableListOf<Course>()
    private val semestersList = mutableListOf<Semester>()
    private val batchesList   = mutableListOf<Batch>()
    private val subjectsList  = mutableListOf<Subject>()
    private val teacherNames  = mutableListOf<String>()
    private val teacherIds    = mutableListOf<String>()
    private val days  = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    private val types = listOf("Regular","Online","Exam","Practical")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        isEditMode  = intent.getBooleanExtra("isEdit", false)
        timetableId = intent.getStringExtra("timetableId") ?: ""

        if (isEditMode) {
            binding.tvTitle.text     = "Edit Entry"
            binding.tvSaveLabel.text = "Update Entry"
            loadForEdit()
        } else {
            // Pre-fill from role/list screen
            selectedCourseId     = intent.getStringExtra("prefilledCourseId")     ?: ""
            selectedCourseName   = intent.getStringExtra("prefilledCourseName")   ?: ""
            selectedSemesterId   = intent.getStringExtra("prefilledSemesterId")   ?: ""
            selectedSemesterName = intent.getStringExtra("prefilledSemesterName") ?: ""
            selectedBatchId      = intent.getStringExtra("prefilledBatchId")      ?: ""
            selectedBatchName    = intent.getStringExtra("prefilledBatchName")    ?: ""

            if (selectedCourseName.isNotEmpty())   setLabel(binding.tvCourseLabel,   selectedCourseName,   true)
            if (selectedSemesterName.isNotEmpty()) setLabel(binding.tvSemesterLabel, selectedSemesterName, true)
            if (selectedBatchName.isNotEmpty())    setLabel(binding.tvBatchLabel,    selectedBatchName,    true)

            // Locked selectors are not clickable
            if (selectedCourseId.isNotEmpty())   binding.spinnerCourse.isEnabled   = false
            if (selectedSemesterId.isNotEmpty()) binding.spinnerSemester.isEnabled = false
            if (selectedBatchId.isNotEmpty())    binding.spinnerBatch.isEnabled    = false
        }

        loadCourses()
        loadTeachers()
        if (selectedSemesterId.isNotEmpty()) loadSubjects(selectedSemesterId)

        setupClicks()
        setupTimePickers()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            pulseBtn()
            binding.btnSave.postDelayed({ if (isEditMode) doUpdate() else doSave() }, 140)
        }
    }

    // ─── Selector clicks ──────────────────────────────────────────────────────

    private fun setupClicks() {
        binding.spinnerCourse.setOnClickListener {
            showPicker("Course", coursesList.map { it.courseName }) { pos ->
                val c = coursesList[pos]
                selectedCourseId = c.id; selectedCourseName = c.courseName
                setLabel(binding.tvCourseLabel, c.courseName)
                selectedSemesterId = ""; selectedSemesterName = ""
                selectedBatchId    = ""; selectedBatchName    = ""
                selectedSubjectId  = ""; selectedSubjectName  = ""
                setLabel(binding.tvSemesterLabel, "Select Semester")
                setLabel(binding.tvBatchLabel,    "Select Batch")
                setLabel(binding.tvSubjectLabel,  "Select Subject")
                subjectsList.clear()
                loadSemesters(c.id)
            }
        }
        binding.spinnerSemester.setOnClickListener {
            showPicker("Semester", semestersList.map { it.semesterName }) { pos ->
                val s = semestersList[pos]
                selectedSemesterId = s.id; selectedSemesterName = s.semesterName
                setLabel(binding.tvSemesterLabel, s.semesterName)
                selectedBatchId   = ""; selectedBatchName  = ""
                selectedSubjectId = ""; selectedSubjectName = ""
                setLabel(binding.tvBatchLabel,   "Select Batch")
                setLabel(binding.tvSubjectLabel, "Select Subject")
                loadBatches()
                loadSubjects(s.id)
            }
        }
        binding.spinnerBatch.setOnClickListener {
            showPicker("Batch", batchesList.map { "${it.batchName} (${it.startTime} - ${it.endTime})" }) { pos ->
                val b = batchesList[pos]
                selectedBatchId = b.id; selectedBatchName = b.batchName
                setLabel(binding.tvBatchLabel, "${b.batchName} (${b.startTime} - ${b.endTime})")
            }
        }
        binding.spinnerSubject.setOnClickListener {
            if (subjectsList.isEmpty()) {
                Toast.makeText(this, "Select semester first or no subjects found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPicker("Subject", subjectsList.map { "${it.subjectName} (${it.subjectCode})" }) { pos ->
                val s = subjectsList[pos]
                selectedSubjectId = s.id; selectedSubjectName = s.subjectName
                setLabel(binding.tvSubjectLabel, "${s.subjectName} (${s.subjectCode})")
                // Auto-fill subject name in etSubject
                binding.etSubject.setText(s.subjectName)
            }
        }
        binding.spinnerTeacher.setOnClickListener {
            showPicker("Teacher", teacherNames) { pos ->
                selectedTeacherId   = teacherIds[pos]
                selectedTeacherName = teacherNames[pos]
                setLabel(binding.tvTeacherLabel, selectedTeacherName)
            }
        }
        binding.spinnerDay.setOnClickListener {
            showPicker("Day", days) { pos ->
                selectedDay = days[pos]; setLabel(binding.tvDayLabel, selectedDay)
            }
        }
        binding.spinnerType.setOnClickListener {
            showPicker("Class Type", types) { pos ->
                selectedType = types[pos].lowercase(); setLabel(binding.tvTypeLabel, types[pos])
                binding.layoutOnlineLink.visibility = if (selectedType == "online") View.VISIBLE else View.GONE
            }
        }
    }

    private fun showPicker(title: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { Toast.makeText(this, "No $title available", Toast.LENGTH_SHORT).show(); return }
        PickerBottomSheet(title, items, -1, onPick).show(supportFragmentManager, "p_$title")
    }

    private fun setLabel(tv: android.widget.TextView, text: String, locked: Boolean = false) {
        tv.text = text
        tv.setTextColor(
            if (text.startsWith("Select")) Color.parseColor("#888888")
            else Color.parseColor("#075E54")
        )
        tv.alpha = if (locked) 0.7f else 1f
    }

    // ─── Time pickers ─────────────────────────────────────────────────────────

    private fun setupTimePickers() {
        binding.etStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
                startTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
                binding.tvStartTimeVal.text = startTimeStr
                binding.tvStartTimeVal.setTextColor(Color.parseColor("#075E54"))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }
        binding.etEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
                endTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
                binding.tvEndTimeVal.text = endTimeStr
                binding.tvEndTimeVal.setTextColor(Color.parseColor("#075E54"))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }
    }

    // ─── Firestore loaders ────────────────────────────────────────────────────

    private fun loadCourses() {
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                coursesList.clear()
                docs.forEach { val c = it.toObject(Course::class.java); if (c.isActive) coursesList.add(c) }
                coursesList.sortBy { it.courseName }
                if (selectedCourseId.isNotEmpty()) loadSemesters(selectedCourseId)
            }
    }

    private fun loadSemesters(courseId: String) {
        firestore.collection("Semesters").whereEqualTo("courseId", courseId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                semestersList.clear()
                docs.forEach { val s = it.toObject(Semester::class.java); if (s.isActive) semestersList.add(s) }
                semestersList.sortBy { it.semesterNumber }
                if (selectedSemesterId.isNotEmpty()) loadBatches()
            }
    }

    private fun loadBatches() {
        firestore.collection("Batches").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                batchesList.clear()
                docs.forEach { val b = it.toObject(Batch::class.java); if (b.isActive) batchesList.add(b) }
                batchesList.sortBy { it.batchName }
            }
    }

    private fun loadSubjects(semId: String) {
        if (semId.isEmpty()) return

        firestore.collection("Subjects")
            .whereEqualTo("semesterId", semId)
            .get()
            .addOnSuccessListener { docs ->
                subjectsList.clear()
                for (doc in docs) {
                    val s = doc.toObject(Subject::class.java)

                    if (s.isActive) {
                        subjectsList.add(s)
                    }
                }
                subjectsList.sortBy { it.subjectName  }
                if (subjectsList.isEmpty()) {
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTeachers() {
        firestore.collection("Teachers").get()
            .addOnSuccessListener { docs ->
                teacherNames.clear(); teacherIds.clear()
                for (doc in docs) {
                    val name = doc.getString("name") ?: ""; if (name.isNotEmpty()) {
                        teacherNames.add(name); teacherIds.add(doc.id)
                    }
                }
            }
    }

    // ─── Load for edit ────────────────────────────────────────────────────────

    private fun loadForEdit() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Timetable").document(timetableId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                if (!doc.exists()) return@addOnSuccessListener
                val t = doc.toObject(Timetable::class.java) ?: return@addOnSuccessListener

                selectedCourseId     = t.courseId;   selectedCourseName   = t.courseName
                selectedSemesterId   = t.semesterId; selectedSemesterName = t.semesterName
                selectedBatchId      = t.batchId;    selectedBatchName    = t.batchName
                selectedTeacherId    = t.teacherId;  selectedTeacherName  = t.teacherName
                selectedDay = t.day; selectedType = t.type
                startTimeStr = t.startTime; endTimeStr = t.endTime

                setLabel(binding.tvCourseLabel,   t.courseName)
                setLabel(binding.tvSemesterLabel, t.semesterName)
                setLabel(binding.tvBatchLabel,    t.batchName)
                setLabel(binding.tvTeacherLabel,  t.teacherName)
                setLabel(binding.tvDayLabel,      t.day)
                setLabel(binding.tvTypeLabel,     t.type.replaceFirstChar { c -> c.titlecase() })

                binding.tvStartTimeVal.text = t.startTime
                binding.tvStartTimeVal.setTextColor(Color.parseColor("#075E54"))
                binding.tvEndTimeVal.text = t.endTime
                binding.tvEndTimeVal.setTextColor(Color.parseColor("#075E54"))

                binding.etSubject.setText(t.subject)
                binding.etClassroom.setText(t.classroom)
                binding.etNotes.setText(t.notes)

                if (t.type == "online") {
                    binding.layoutOnlineLink.visibility = View.VISIBLE
                    binding.etOnlineLink.setText(t.onlineLink)
                }
                // load subjects for semester
                if (t.semesterId.isNotEmpty()) loadSubjects(t.semesterId)
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Save / Update ────────────────────────────────────────────────────────

    private fun doSave() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE; binding.btnSave.isEnabled = false

        val id = firestore.collection("Timetable").document().id
        val timetable = Timetable(
            id = id, courseId = selectedCourseId, courseName = selectedCourseName,
            semesterId = selectedSemesterId, semesterName = selectedSemesterName,
            batchId = selectedBatchId, batchName = selectedBatchName,
            subject = binding.etSubject.text.toString().trim(),
            teacherId = selectedTeacherId, teacherName = selectedTeacherName,
            day = selectedDay, startTime = startTimeStr, endTime = endTimeStr,
            classroom = binding.etClassroom.text.toString().trim(),
            onlineLink = if (selectedType == "online") binding.etOnlineLink.text.toString().trim() else "",
            duration = calcDuration(), type = selectedType, status = "active",
            notes = binding.etNotes.text.toString().trim(),
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
        )
        firestore.collection("Timetable").document(id).set(timetable)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE; binding.btnSave.isEnabled = true
                Toast.makeText(this, "Entry added!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE; binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun doUpdate() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE; binding.btnSave.isEnabled = false

        val updates = hashMapOf<String, Any>(
            "courseId" to selectedCourseId, "courseName" to selectedCourseName,
            "semesterId" to selectedSemesterId, "semesterName" to selectedSemesterName,
            "batchId" to selectedBatchId, "batchName" to selectedBatchName,
            "subject" to binding.etSubject.text.toString().trim(),
            "teacherId" to selectedTeacherId, "teacherName" to selectedTeacherName,
            "day" to selectedDay, "startTime" to startTimeStr, "endTime" to endTimeStr,
            "classroom" to binding.etClassroom.text.toString().trim(),
            "onlineLink" to if (selectedType == "online") binding.etOnlineLink.text.toString().trim() else "",
            "duration" to calcDuration(), "type" to selectedType,
            "notes" to binding.etNotes.text.toString().trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("Timetable").document(timetableId).update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE; binding.btnSave.isEnabled = true
                Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE; binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calcDuration(): Int {
        return try {
            val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val s = fmt.parse(startTimeStr); val e = fmt.parse(endTimeStr)
            if (s != null && e != null) ((e.time - s.time) / 60000).toInt() else 60
        } catch (ex: Exception) { 60 }
    }

    private fun validate(): Boolean {
        if (selectedCourseId.isEmpty())   { toast("Select a Course");     return false }
        if (selectedSemesterId.isEmpty()) { toast("Select a Semester");   return false }
        if (selectedBatchId.isEmpty())    { toast("Select a Batch");      return false }
        if (binding.etSubject.text.toString().trim().isEmpty()) {
            binding.etSubject.error = "Subject required"; binding.etSubject.requestFocus(); return false
        }
        if (selectedTeacherName.isEmpty()) { toast("Select a Teacher");   return false }
        if (selectedDay.isEmpty())         { toast("Select a Day");       return false }
        if (startTimeStr.isEmpty())        { toast("Select Start Time");  return false }
        if (endTimeStr.isEmpty())          { toast("Select End Time");    return false }
        if (selectedType.isEmpty())        { toast("Select Class Type");  return false }
        if (selectedType == "online" && binding.etOnlineLink.text.toString().trim().isEmpty()) {
            binding.etOnlineLink.error = "Online link required"; binding.etOnlineLink.requestFocus(); return false
        }
        return true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun pulseBtn() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnSave, "scaleX", 1f, 0.94f, 1f).apply { duration = 140; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(binding.btnSave, "scaleY", 1f, 0.94f, 1f).apply { duration = 140; interpolator = OvershootInterpolator() }
            ); start()
        }
    }
}