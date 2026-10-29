package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAddStudentBinding
import com.example.academyhub.models.*
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class AddStudentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddStudentBinding
    private lateinit var firestore: FirebaseFirestore

    private var selectedImageBase64 = ""
    private var hasPhoto            = false
    private var photoRemoved        = false
    private var isEditMode          = false
    private var studentId           = ""

    // Prefill from RoleActivity
    private var prefillMode         = false
    private var prefillSessionId    = ""; private var prefillSessionName   = ""
    private var prefillCourseId     = ""; private var prefillCourseName    = ""
    private var prefillYearId       = ""; private var prefillYearName      = ""
    private var prefillSemesterId   = ""; private var prefillSemesterName  = ""

    // Selected models
    private var selectedSession:  Session?      = null
    private var selectedCourse:   Course?       = null
    private var selectedYear:     AcademicYear? = null
    private var selectedSemester: Semester?     = null
    private var selectedBatch:    Batch?        = null

    // Lists
    private val sessionsList  = mutableListOf<Session>()
    private val coursesList   = mutableListOf<Course>()
    private val yearsList     = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()
    private val batchesList   = mutableListOf<Batch>()

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageBase64 = ImageUtil.convertImageToBase64Optimized(this, it) ?: ""
            if (selectedImageBase64.isNotEmpty()) {
                binding.ivStudentProfile.setImageURI(it)
                hasPhoto = true
                photoRemoved = false
                binding.tvRemovePhoto.visibility = View.VISIBLE
                animatePhotoChange()
                Toast.makeText(this, "Photo selected", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        isEditMode = intent.getBooleanExtra("isEdit", false)
        studentId  = intent.getStringExtra("studentId") ?: ""

        prefillMode         = intent.getBooleanExtra("prefillMode", false)
        prefillSessionId    = intent.getStringExtra("prefillSessionId")    ?: ""
        prefillSessionName  = intent.getStringExtra("prefillSessionName")  ?: ""
        prefillCourseId     = intent.getStringExtra("prefillCourseId")     ?: ""
        prefillCourseName   = intent.getStringExtra("prefillCourseName")   ?: ""
        prefillYearId       = intent.getStringExtra("prefillYearId")       ?: ""
        prefillYearName     = intent.getStringExtra("prefillYearName")     ?: ""
        prefillSemesterId   = intent.getStringExtra("prefillSemesterId")   ?: ""
        prefillSemesterName = intent.getStringExtra("prefillSemesterName") ?: ""

        if (isEditMode) {
            binding.tvTitle.text  = "Edit Student"
            binding.btnSave.text  = "Update Student"
        }

        setupDropdowns()
        setupScrollWheelDatePickers()
        setupPhotoSection()
        setupClickListeners()

        loadSessions()
        loadCourses()
        loadBatches()

        if (prefillMode) applyPrefill()
        if (isEditMode)  loadStudentData()
    }

    // -------------------------------------------------------
    //  Photo
    // -------------------------------------------------------
    private fun setupPhotoSection() {
        binding.btnSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.tvRemovePhoto.setOnClickListener {
            binding.ivStudentProfile.setImageResource(R.drawable.ic_student_default)
            selectedImageBase64 = ""
            hasPhoto = false
            photoRemoved = true
            binding.tvRemovePhoto.visibility = View.GONE
        }
    }

    private fun animatePhotoChange() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleX", 0.8f, 1.05f, 1f)
                    .apply { duration = 320 },
                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleY", 0.8f, 1.05f, 1f)
                    .apply { duration = 320; interpolator = OvershootInterpolator() }
            )
            start()
        }
    }

    // -------------------------------------------------------
    //  Scroll Wheel Date Picker
    // -------------------------------------------------------
    private fun setupScrollWheelDatePickers() {
        binding.btnDOB.setOnClickListener          { showWheelPicker("dob") }
        binding.btnAdmissionDate.setOnClickListener { showWheelPicker("admission") }
    }

    private fun showWheelPicker(type: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setContentView(R.layout.dialog_date_wheel_picker)

        val npDay   = dialog.findViewById<NumberPicker>(R.id.npDay)
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)
        val npYear  = dialog.findViewById<NumberPicker>(R.id.npYear)

        val cal = Calendar.getInstance()
        val existing = if (type == "dob") binding.etDOB.text.toString()
        else binding.etAdmissionDate.text.toString()

        if (existing.length == 10 && existing.contains("/")) {
            try {
                val p = existing.split("/")
                cal.set(p[2].toInt(), p[1].toInt() - 1, p[0].toInt())
            } catch (_: Exception) {}
        }

        npDay.minValue = 1; npDay.maxValue = 31
        npDay.value    = cal.get(Calendar.DAY_OF_MONTH)
        npDay.wrapSelectorWheel = true

        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
        npMonth.minValue = 0; npMonth.maxValue = 11
        npMonth.displayedValues = months
        npMonth.value  = cal.get(Calendar.MONTH)
        npMonth.wrapSelectorWheel = true

        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        npYear.minValue = if (type == "dob") 1940 else nowYear - 10
        npYear.maxValue = if (type == "dob") nowYear else nowYear + 5
        npYear.value    = cal.get(Calendar.YEAR)
        npYear.wrapSelectorWheel = false

        dialog.findViewById<View>(R.id.btnDateOk).setOnClickListener {
            val formatted = "%02d/%02d/%04d".format(npDay.value, npMonth.value + 1, npYear.value)
            if (type == "dob") {
                binding.etDOB.text = formatted
                binding.etDOB.setTextColor(getColor(android.R.color.black))
                clearFieldError(binding.btnDOB)
            } else {
                binding.etAdmissionDate.text = formatted
                binding.etAdmissionDate.setTextColor(getColor(android.R.color.black))
            }
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnDateCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // -------------------------------------------------------
    //  Dropdowns
    // -------------------------------------------------------
    private fun setupDropdowns() {
        val genders = listOf("Male", "Female", "Other")
        binding.spinnerGender.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders))
        binding.spinnerGender.setOnClickListener { binding.spinnerGender.showDropDown() }
        binding.spinnerGender.setOnItemClickListener { _, _, _, _ -> clearFieldError(binding.layoutGender) }

        val statuses = listOf("active", "inactive", "blocked", "passout")
        binding.spinnerStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses))
        binding.spinnerStatus.setText("active", false)
        binding.spinnerStatus.setOnClickListener { binding.spinnerStatus.showDropDown() }

        binding.spinnerCourse.setOnClickListener { binding.spinnerCourse.showDropDown() }
        binding.spinnerCourse.setOnItemClickListener { _, _, pos, _ ->
            selectedCourse = coursesList[pos]
            selectedYear = null
            selectedSemester = null
            binding.spinnerYear.setText("", false)
            binding.spinnerSemester.setText("", false)
            val yearParent = binding.spinnerYear.parent.parent as? View
            enableDropdown(yearParent)
            binding.spinnerYear.isEnabled = true
            binding.spinnerSemester.isEnabled = false
            binding.spinnerSemester.alpha = 0.5f
            loadYears(selectedCourse!!.id)
            clearFieldError(binding.spinnerCourse.parent.parent as? View)
        }

        binding.spinnerYear.setOnClickListener {
            if (binding.spinnerYear.isEnabled) binding.spinnerYear.showDropDown()
        }
        binding.spinnerYear.setOnItemClickListener { _, _, pos, _ ->
            selectedYear = yearsList[pos]
            selectedSemester = null
            binding.spinnerSemester.setText("", false)
            enableDropdown(binding.spinnerSemester.parent.parent as? View)
            binding.spinnerSemester.isEnabled = true
            loadSemesters(selectedYear!!.id)
            clearFieldError(binding.spinnerYear.parent.parent as? View)
        }

        binding.spinnerSemester.setOnClickListener {
            if (binding.spinnerSemester.isEnabled) binding.spinnerSemester.showDropDown()
        }
        binding.spinnerSemester.setOnItemClickListener { _, _, pos, _ ->
            selectedSemester = semestersList[pos]
            clearFieldError(binding.spinnerSemester.parent.parent as? View)
        }

        binding.spinnerSession.setOnClickListener { binding.spinnerSession.showDropDown() }
        binding.spinnerSession.setOnItemClickListener { _, _, pos, _ ->
            selectedSession = sessionsList[pos]
            clearFieldError(binding.spinnerSession.parent.parent as? View)
        }

        binding.spinnerBatch.setOnClickListener { binding.spinnerBatch.showDropDown() }
        binding.spinnerBatch.setOnItemClickListener { _, _, pos, _ ->
            selectedBatch = batchesList[pos]
            clearFieldError(binding.spinnerBatch.parent.parent as? View)
        }
    }

    private fun enableDropdown(view: View?) {
        view?.animate()?.alpha(1f)?.setDuration(200)?.start()
    }

    // -------------------------------------------------------
    //  Click Listeners
    // -------------------------------------------------------
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }
        binding.btnSave.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.btnSave, "scaleX", 1f, 0.96f, 1f).apply { duration = 150 },
                    ObjectAnimator.ofFloat(binding.btnSave, "scaleY", 1f, 0.96f, 1f).apply { duration = 150 }
                )
                start()
            }
            if (isEditMode) updateStudent() else saveStudent()
        }
    }

    // -------------------------------------------------------
    //  Validation
    // -------------------------------------------------------
    private fun validateInputs(): Boolean {
        var valid = true

        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            shakeField(binding.etName.parent as View)
            binding.etName.error = "Required"
            valid = false
        }

        val mobile = binding.etMobile.text.toString().trim()
        if (mobile.isEmpty()) {
            shakeField(binding.etMobile.parent as View)
            binding.etMobile.error = "Required"
            valid = false
        } else if (mobile.length < 10) {
            shakeField(binding.etMobile.parent as View)
            binding.etMobile.error = "Enter valid 10 digit number"
            valid = false
        }

        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            shakeField(binding.etEmail.parent as View)
            binding.etEmail.error = "Required"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            shakeField(binding.etEmail.parent as View)
            binding.etEmail.error = "Enter valid email"
            valid = false
        }

        if (selectedSession == null) {
            shakeField(binding.spinnerSession.parent.parent as View)
            showToastError("Select a session")
            valid = false
        }

        if (selectedCourse == null) {
            shakeField(binding.spinnerCourse.parent.parent as View)
            if (valid) showToastError("Select a course")
            valid = false
        }

        if (selectedYear == null) {
            shakeField(binding.spinnerYear.parent.parent as View)
            if (valid) showToastError("Select a year")
            valid = false
        }

        if (selectedSemester == null) {
            shakeField(binding.spinnerSemester.parent.parent as View)
            if (valid) showToastError("Select a semester")
            valid = false
        }

        if (selectedBatch == null) {
            shakeField(binding.spinnerBatch.parent.parent as View)
            if (valid) showToastError("Select a batch")
            valid = false
        }

        return valid
    }

    private fun shakeField(view: View) {
        view.setBackgroundResource(R.drawable.bg_input_field_focused)
        ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -8f, 8f, -4f, 0f).apply {
            duration = 400; start()
        }
    }

    private fun clearFieldError(view: View?) {
        view?.setBackgroundResource(R.drawable.bg_input_field)
    }

    private fun showToastError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------
    //  Firestore Load
    // -------------------------------------------------------
    private fun loadSessions() {
        firestore.collection("Sessions").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                sessionsList.clear()
                for (d in docs) sessionsList.add(d.toObject(Session::class.java))
                sessionsList.sortByDescending { it.startYear }
                binding.spinnerSession.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line, sessionsList.map { it.sessionName }))
            }
    }

    private fun loadCourses() {
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                coursesList.clear()
                for (d in docs) coursesList.add(d.toObject(Course::class.java))
                binding.spinnerCourse.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line, coursesList.map { it.courseName }))
            }
    }

    private fun loadYears(courseId: String) {
        firestore.collection("Years")
            .whereEqualTo("courseId", courseId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                yearsList.clear()
                for (d in docs) yearsList.add(d.toObject(AcademicYear::class.java))
                yearsList.sortBy { it.yearNumber }
                binding.spinnerYear.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line, yearsList.map { it.yearName }))
            }
    }

    private fun loadSemesters(yearId: String) {
        firestore.collection("Semesters")
            .whereEqualTo("yearId", yearId).whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                semestersList.clear()
                for (d in docs) semestersList.add(d.toObject(Semester::class.java))
                semestersList.sortBy { it.semesterNumber }
                binding.spinnerSemester.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line, semestersList.map { it.semesterName }))
            }
    }

    private fun loadBatches() {
        firestore.collection("Batches").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                batchesList.clear()
                for (d in docs) batchesList.add(d.toObject(Batch::class.java))
                binding.spinnerBatch.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line, batchesList.map { it.batchName }))
            }
    }

    // -------------------------------------------------------
    //  Prefill from Role screen
    // -------------------------------------------------------
    private fun applyPrefill() {
        selectedSession  = Session(id = prefillSessionId,  sessionName  = prefillSessionName)
        selectedCourse   = Course(id  = prefillCourseId,   courseName   = prefillCourseName)
        selectedYear     = AcademicYear(id = prefillYearId, yearName    = prefillYearName)
        selectedSemester = Semester(id = prefillSemesterId, semesterName = prefillSemesterName)

        binding.spinnerSession.setText(prefillSessionName,   false)
        binding.spinnerCourse.setText(prefillCourseName,     false)
        binding.spinnerYear.setText(prefillYearName,         false)
        binding.spinnerSemester.setText(prefillSemesterName, false)

        binding.spinnerSession.isEnabled  = false
        binding.spinnerCourse.isEnabled   = false
        binding.spinnerYear.isEnabled     = false
        binding.spinnerSemester.isEnabled = false

        if (prefillYearId.isNotEmpty())   loadSemesters(prefillYearId)
        if (prefillCourseId.isNotEmpty()) loadYears(prefillCourseId)
    }

    // -------------------------------------------------------
    //  Load existing student for edit
    // -------------------------------------------------------
    private fun loadStudentData() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Students").document(studentId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                val s = doc.toObject(Student::class.java) ?: return@addOnSuccessListener

                binding.etName.setText(s.name)
                binding.etMobile.setText(s.mobile)
                binding.etEmail.setText(s.email)
                binding.spinnerGender.setText(s.gender, false)

                if (s.dateOfBirth.isNotEmpty()) {
                    binding.etDOB.text = s.dateOfBirth
                    binding.etDOB.setTextColor(getColor(android.R.color.black))
                }
                binding.etAddress.setText(s.address)
                binding.etRollNumber.setText(s.rollNumber)

                if (s.admissionDate.isNotEmpty()) {
                    binding.etAdmissionDate.text = s.admissionDate
                    binding.etAdmissionDate.setTextColor(getColor(android.R.color.black))
                }
                binding.spinnerStatus.setText(s.status, false)

                binding.spinnerSession.setText(s.sessionName,   false)
                binding.spinnerCourse.setText(s.courseName,     false)
                binding.spinnerYear.setText(s.yearName,         false)
                binding.spinnerSemester.setText(s.semesterName, false)
                binding.spinnerBatch.setText(s.batchName,       false)

                selectedSession  = Session(id = s.sessionId,   sessionName  = s.sessionName)
                selectedCourse   = Course(id  = s.courseId,    courseName   = s.courseName)
                selectedYear     = AcademicYear(id = s.yearId, yearName     = s.yearName)
                selectedSemester = Semester(id = s.semesterId, semesterName = s.semesterName)
                selectedBatch    = Batch(id    = s.batchId,    batchName    = s.batchName)

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        ImageUtil.loadImageFromChunks(
                            firestore = firestore, userId = studentId,
                            collection = "Students", imageView = binding.ivStudentProfile,
                            progressBar = null,
                            onComplete = {
                                hasPhoto = true
                                binding.tvRemovePhoto.visibility = View.VISIBLE
                            }
                        )
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------
    //  Save — with Mobile Duplicate Check
    // -------------------------------------------------------
    private fun saveStudent() {
        if (!validateInputs()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled      = false

        val mobile = binding.etMobile.text.toString().trim()

        // ── Duplicate mobile check ──────────────────────────────
        firestore.collection("Students")
            .whereEqualTo("mobile", mobile)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    // Mobile already exists — block save
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled      = true

                    val existingName = docs.documents[0].getString("name") ?: "another student"
                    shakeField(binding.etMobile.parent as View)
                    binding.etMobile.error = "Number already registered"

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Duplicate Mobile Number")
                        .setMessage("This mobile number is already registered for \"$existingName\".\n\nEk number ek j student mate vापray shke chhe.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    // No duplicate — proceed to save
                    doSaveStudent()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled      = true
                Toast.makeText(this, "Error checking duplicate: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun doSaveStudent() {
        val newId = firestore.collection("Students").document().id
        val student = Student(
            id             = newId,
            name           = binding.etName.text.toString().trim(),
            email          = binding.etEmail.text.toString().trim(),
            mobile         = binding.etMobile.text.toString().trim(),
            gender         = binding.spinnerGender.text.toString(),
            dateOfBirth    = binding.etDOB.text.toString().let {
                if (it == "Select date of birth") "" else it },
            address        = binding.etAddress.text.toString().trim(),
            sessionId      = selectedSession?.id   ?: "",
            sessionName    = selectedSession?.sessionName ?: "",
            courseId       = selectedCourse?.id    ?: "",
            courseName     = selectedCourse?.courseName ?: "",
            yearId         = selectedYear?.id      ?: "",
            yearName       = selectedYear?.yearName ?: "",
            yearNumber     = selectedYear?.yearNumber ?: 1,
            semesterId     = selectedSemester?.id  ?: "",
            semesterName   = selectedSemester?.semesterName ?: "",
            semesterNumber = selectedSemester?.semesterNumber ?: 1,
            batchId        = selectedBatch?.id     ?: "",
            batchName      = selectedBatch?.batchName ?: "",
            rollNumber     = binding.etRollNumber.text.toString().trim(),
            admissionDate  = binding.etAdmissionDate.text.toString().let {
                if (it == "Select admission date") "" else it },
            status         = binding.spinnerStatus.text.toString().ifEmpty { "active" },
            profileImageBase64 = "",
            createdAt      = System.currentTimeMillis(),
            updatedAt      = System.currentTimeMillis()
        )

        firestore.collection("Students").document(newId).set(student)
            .addOnSuccessListener {
                if (selectedImageBase64.isNotEmpty()) uploadPhoto(newId, true)
                else onDone(true)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------
    //  Update — with Mobile Duplicate Check (skip own record)
    // -------------------------------------------------------
    private fun updateStudent() {
        if (!validateInputs()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled      = false

        val mobile = binding.etMobile.text.toString().trim()

        // ── Duplicate mobile check — except current student's own record ──
        firestore.collection("Students")
            .whereEqualTo("mobile", mobile)
            .get()
            .addOnSuccessListener { docs ->
                // Check karo — same number chhe pan different student ma
                val conflict = docs.documents.any { it.id != studentId }

                if (conflict) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled      = true

                    val conflictDoc = docs.documents.first { it.id != studentId }
                    val existingName = conflictDoc.getString("name") ?: "another student"
                    shakeField(binding.etMobile.parent as View)
                    binding.etMobile.error = "Number already registered"

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Duplicate Mobile Number")
                        .setMessage("This mobile number is already registered for \"$existingName\".\n\nEk number ek j student mate vापray shke chhe.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    doUpdateStudent()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled      = true
                Toast.makeText(this, "Error checking duplicate: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun doUpdateStudent() {
        val updates = hashMapOf<String, Any>(
            "name"         to binding.etName.text.toString().trim(),
            "email"        to binding.etEmail.text.toString().trim(),
            "mobile"       to binding.etMobile.text.toString().trim(),
            "gender"       to binding.spinnerGender.text.toString(),
            "dateOfBirth"  to binding.etDOB.text.toString().let {
                if (it == "Select date of birth") "" else it },
            "address"      to binding.etAddress.text.toString().trim(),
            "sessionId"    to (selectedSession?.id   ?: ""),
            "sessionName"  to (selectedSession?.sessionName ?: ""),
            "courseId"     to (selectedCourse?.id    ?: ""),
            "courseName"   to (selectedCourse?.courseName ?: ""),
            "yearId"       to (selectedYear?.id      ?: ""),
            "yearName"     to (selectedYear?.yearName ?: ""),
            "semesterId"   to (selectedSemester?.id  ?: ""),
            "semesterName" to (selectedSemester?.semesterName ?: ""),
            "batchId"      to (selectedBatch?.id     ?: ""),
            "batchName"    to (selectedBatch?.batchName ?: ""),
            "rollNumber"   to binding.etRollNumber.text.toString().trim(),
            "admissionDate" to binding.etAdmissionDate.text.toString().let {
                if (it == "Select admission date") "" else it },
            "status"       to binding.spinnerStatus.text.toString().ifEmpty { "active" },
            "updatedAt"    to System.currentTimeMillis()
        )

        firestore.collection("Students").document(studentId).update(updates)
            .addOnSuccessListener {
                when {
                    selectedImageBase64.isNotEmpty() -> uploadPhoto(studentId, false)
                    photoRemoved -> deletePhotoChunks(studentId)
                    else -> onDone(false)
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // -------------------------------------------------------
    //  Delete photo chunks
    // -------------------------------------------------------
    private fun deletePhotoChunks(id: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled      = false

        val chunksRef = firestore.collection("Students").document(id).collection("photo_chunks")
        chunksRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) { onDone(false); return@addOnSuccessListener }
                val batch = firestore.batch()
                for (doc in snapshot.documents) batch.delete(doc.reference)
                batch.commit()
                    .addOnSuccessListener {
                        firestore.collection("Students").document(id)
                            .update("profileImageBase64", "")
                            .addOnSuccessListener { onDone(false) }
                            .addOnFailureListener { onDone(false) }
                    }
                    .addOnFailureListener { onDone(false) }
            }
            .addOnFailureListener { onDone(false) }
    }

    private fun uploadPhoto(id: String, isNew: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.uploadPhotoChunks(
                firestore = firestore, userId = id, collection = "Students",
                base64String = selectedImageBase64,
                onProgress = {},
                onSuccess  = { runOnUiThread { onDone(isNew) } },
                onFailure  = {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled = true
                        Toast.makeText(this@AddStudentActivity,
                            if (isNew) "Saved, but photo upload failed"
                            else "Updated, but photo upload failed",
                            Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            )
        }
    }

    private fun onDone(isNew: Boolean) {
        binding.progressBar.visibility = View.GONE
        binding.btnSave.isEnabled = true
        Toast.makeText(this, if (isNew) "Student added!" else "Student updated!", Toast.LENGTH_SHORT).show()
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
    }
}