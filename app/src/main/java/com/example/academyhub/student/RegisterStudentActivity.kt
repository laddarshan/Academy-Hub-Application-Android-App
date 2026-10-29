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
import com.example.academyhub.databinding.ActivityRegisterStudentBinding
import com.example.academyhub.models.*
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class RegisterStudentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterStudentBinding
    private lateinit var firestore: FirebaseFirestore

    private var selectedImageBase64 = ""
    private var hasPhoto            = false

    private var selectedSession:  Session?      = null
    private var selectedCourse:   Course?       = null
    private var selectedYear:     AcademicYear? = null
    private var selectedSemester: Semester?     = null

    private val sessionsList  = mutableListOf<Session>()
    private val coursesList   = mutableListOf<Course>()
    private val yearsList     = mutableListOf<AcademicYear>()
    private val semestersList = mutableListOf<Semester>()

    // DOB / Admission date selected values
    private var dobDay   = 1; private var dobMonth   = 0; private var dobYear   = 2000
    private var admDay   = 1; private var admMonth   = 0; private var admYear   = Calendar.getInstance().get(Calendar.YEAR)

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageBase64 = ImageUtil.convertImageToBase64Optimized(this, it) ?: ""
            if (selectedImageBase64.isNotEmpty()) {
                binding.ivStudentProfile.setImageURI(it)
                hasPhoto = true
                binding.tvRemovePhoto.visibility = View.VISIBLE
                animatePhotoChange()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Pre-fill phone if passed from LoginActivity
        val phoneFromLogin = intent.getStringExtra("phone") ?: ""
        if (phoneFromLogin.isNotEmpty()) {
            binding.etMobile.setText(phoneFromLogin)
        }

        setupDropdowns()
        setupScrollWheelDatePickers()
        setupPhotoSection()
        setupClickListeners()

        loadSessions()
        loadCourses()

        // Role data prefilled from RegisterRoleActivity
        val prefillSessionId   = intent.getStringExtra("sessionId")   ?: ""
        val prefillSessionName = intent.getStringExtra("sessionName")  ?: ""
        val prefillCourseId    = intent.getStringExtra("courseId")     ?: ""
        val prefillCourseName  = intent.getStringExtra("courseName")   ?: ""
        val prefillYearId      = intent.getStringExtra("yearId")       ?: ""
        val prefillYearName    = intent.getStringExtra("yearName")     ?: ""
        val prefillSemesterId  = intent.getStringExtra("semesterId")   ?: ""
        val prefillSemesterName= intent.getStringExtra("semesterName") ?: ""

        if (prefillSessionId.isNotEmpty()) {
            selectedSession  = com.example.academyhub.models.Session(id = prefillSessionId, sessionName = prefillSessionName)
            selectedCourse   = com.example.academyhub.models.Course(id  = prefillCourseId,  courseName  = prefillCourseName)
            selectedYear     = com.example.academyhub.models.AcademicYear(id = prefillYearId, yearName  = prefillYearName)
            selectedSemester = com.example.academyhub.models.Semester(id = prefillSemesterId, semesterName = prefillSemesterName)

            binding.spinnerSession.setText(prefillSessionName,  false)
            binding.spinnerCourse.setText(prefillCourseName,    false)
            binding.spinnerYear.setText(prefillYearName,        false)
            binding.spinnerSemester.setText(prefillSemesterName,false)

            // Lock these fields — already selected in role screen
            binding.spinnerSession.isEnabled  = false
            binding.spinnerCourse.isEnabled   = false
            binding.spinnerYear.isEnabled     = false
            binding.spinnerSemester.isEnabled = false
        }

        // Entrance animation
        binding.btnSave.alpha = 0f; binding.btnSave.translationY = 24f
        binding.btnSave.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f)).start()
    }

    // ── Photo ────────────────────────────────────────────────────────────────
    private fun setupPhotoSection() {
        binding.btnSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.tvRemovePhoto.setOnClickListener {
            binding.ivStudentProfile.setImageResource(R.drawable.ic_student_default)
            selectedImageBase64 = ""; hasPhoto = false
            binding.tvRemovePhoto.visibility = View.GONE
        }
    }

    private fun animatePhotoChange() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleX", 0.8f, 1.05f, 1f).apply { duration = 320 },
                ObjectAnimator.ofFloat(binding.ivStudentProfile, "scaleY", 0.8f, 1.05f, 1f).apply { duration = 320; interpolator = OvershootInterpolator() }
            ); start()
        }
    }

    // ── Date pickers ─────────────────────────────────────────────────────────
    private fun setupScrollWheelDatePickers() {
        binding.btnDOB.setOnClickListener          { showWheelPicker("dob") }
        binding.btnAdmissionDate.setOnClickListener { showWheelPicker("admission") }
    }

    private fun showWheelPicker(type: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_date_wheel_picker)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val npDay   = dialog.findViewById<NumberPicker>(R.id.npDay)
        val npMonth = dialog.findViewById<NumberPicker>(R.id.npMonth)
        val npYear  = dialog.findViewById<NumberPicker>(R.id.npYear)
        val months  = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val curYear = Calendar.getInstance().get(Calendar.YEAR)

        npDay.minValue = 1; npDay.maxValue = 31
        npMonth.minValue = 0; npMonth.maxValue = 11; npMonth.displayedValues = months
        npYear.minValue = 1990; npYear.maxValue = curYear

        if (type == "dob") {
            npDay.value = dobDay; npMonth.value = dobMonth; npYear.value = dobYear
        } else {
            npDay.value = admDay; npMonth.value = admMonth; npYear.value = admYear
        }

        dialog.findViewById<View>(R.id.btnDateOk)?.setOnClickListener {
            val formatted = "${npDay.value} ${months[npMonth.value]} ${npYear.value}"
            if (type == "dob") {
                dobDay = npDay.value; dobMonth = npMonth.value; dobYear = npYear.value
                binding.etDOB.text = formatted
                binding.etDOB.setTextColor(getColor(android.R.color.black))
            } else {
                admDay = npDay.value; admMonth = npMonth.value; admYear = npYear.value
                binding.etAdmissionDate.text = formatted
                binding.etAdmissionDate.setTextColor(getColor(android.R.color.black))
            }
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnDateCancel)?.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ── Dropdowns ────────────────────────────────────────────────────────────
    private fun setupDropdowns() {
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
            listOf("Male", "Female", "Other"))
        binding.spinnerGender.setAdapter(genderAdapter)
        // Force dropdown open on click (focusable=false workaround)
        binding.spinnerGender.setOnClickListener { binding.spinnerGender.showDropDown() }
        binding.layoutGender.setOnClickListener   { binding.spinnerGender.showDropDown() }

        binding.spinnerSession.setOnItemClickListener { _, _, pos, _ ->
            selectedSession = sessionsList.getOrNull(pos)
            selectedCourse = null; selectedYear = null; selectedSemester = null
        }
        binding.spinnerCourse.setOnItemClickListener { _, _, pos, _ ->
            selectedCourse = coursesList.getOrNull(pos)
            selectedYear = null; selectedSemester = null
            selectedCourse?.id?.let { loadYears(it) }
        }
        binding.spinnerYear.setOnItemClickListener { _, _, pos, _ ->
            selectedYear = yearsList.getOrNull(pos)
            selectedSemester = null
            selectedYear?.id?.let { loadSemesters(it) }
        }
        binding.spinnerSemester.setOnItemClickListener { _, _, pos, _ ->
            selectedSemester = semestersList.getOrNull(pos)
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }
        binding.btnSave.setOnClickListener { saveStudent() }
    }

    // ── Firestore load ────────────────────────────────────────────────────────
    private fun loadSessions() {
        firestore.collection("Sessions").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                sessionsList.clear()
                for (d in docs) sessionsList.add(d.toObject(Session::class.java))
                sessionsList.sortByDescending { it.startYear }
                binding.spinnerSession.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line,
                    sessionsList.map { it.sessionName }))
            }
    }

    private fun loadCourses() {
        firestore.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { docs ->
                coursesList.clear()
                for (d in docs) coursesList.add(d.toObject(Course::class.java))
                binding.spinnerCourse.setAdapter(ArrayAdapter(this,
                    android.R.layout.simple_dropdown_item_1line,
                    coursesList.map { it.courseName }))
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
                    android.R.layout.simple_dropdown_item_1line,
                    yearsList.map { it.yearName }))
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
                    android.R.layout.simple_dropdown_item_1line,
                    semestersList.map { it.semesterName }))
            }
    }

    // ── Validate ──────────────────────────────────────────────────────────────
    private fun validateInputs(): Boolean {
        var valid = true

        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            shakeField(binding.etName.parent as View)
            binding.etName.error = "Required"; valid = false
        }

        val mobile = binding.etMobile.text.toString().trim()
        if (mobile.isEmpty() || mobile.length < 10) {
            shakeField(binding.etMobile.parent as View)
            binding.etMobile.error = "Please enter a valid 10-digit mobile number."; valid = false
        }

        if (selectedSession == null) {
            shakeField(binding.spinnerSession.parent.parent as View)
            if (valid) Toast.makeText(this, "Session select karo", Toast.LENGTH_SHORT).show()
            valid = false
        }
        if (selectedCourse == null) {
            shakeField(binding.spinnerCourse.parent.parent as View)
            if (valid) Toast.makeText(this, "Course select karo", Toast.LENGTH_SHORT).show()
            valid = false
        }
        if (selectedYear == null) {
            shakeField(binding.spinnerYear.parent.parent as View)
            if (valid) Toast.makeText(this, "Year select karo", Toast.LENGTH_SHORT).show()
            valid = false
        }
        if (selectedSemester == null) {
            shakeField(binding.spinnerSemester.parent.parent as View)
            if (valid) Toast.makeText(this, "Semester select karo", Toast.LENGTH_SHORT).show()
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

    // ── Save ──────────────────────────────────────────────────────────────────
    private fun saveStudent() {
        if (!validateInputs()) return

        val mobile = binding.etMobile.text.toString().trim()
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled      = false

        // Pehla anonymous login karo — Firestore write mate auth jaruri chhe
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            checkDuplicateAndSave(mobile)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { checkDuplicateAndSave(mobile) }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled      = true
                    Toast.makeText(this, "Auth error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun checkDuplicateAndSave(mobile: String) {
        firestore.collection("Students")
            .whereEqualTo("mobile", mobile).limit(1).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled      = true
                    Toast.makeText(this,
                        "This number is already registered. Try logging in.",
                        Toast.LENGTH_LONG).show()
                } else {
                    doSave(mobile)
                }
            }
            .addOnFailureListener { doSave(mobile) }
    }

    private fun doSave(mobile: String) {
        val newId = firestore.collection("Students").document().id
        val student = Student(
            id             = newId,
            name           = binding.etName.text.toString().trim(),
            email          = binding.etEmail.text.toString().trim(),
            mobile         = mobile,
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
            batchId        = "pYzu3hKBpWKWiOTECfqI",
            batchName      = "Div 1",
            rollNumber     = "",
            admissionDate  = binding.etAdmissionDate.text.toString().let {
                if (it == "Select admission date") "" else it },
            status         = "active",
            profileImageBase64 = "",
            createdAt      = System.currentTimeMillis(),
            updatedAt      = System.currentTimeMillis()
        )

        firestore.collection("Students").document(newId).set(student)
            .addOnSuccessListener {
                if (selectedImageBase64.isNotEmpty()) uploadPhoto(newId, student)
                else onRegistered(newId, student)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled      = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadPhoto(id: String, student: Student) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.uploadPhotoChunks(
                firestore = firestore, userId = id, collection = "Students",
                base64String = selectedImageBase64,
                onProgress = {},
                onSuccess  = { runOnUiThread { onRegistered(id, student) } },
                onFailure  = { runOnUiThread { onRegistered(id, student) } }
            )
        }
    }

    private fun onRegistered(studentId: String, student: Student) {
        binding.progressBar.visibility = View.GONE
        binding.btnSave.isEnabled      = true
        Toast.makeText(this, "Registration successful! Login now.", Toast.LENGTH_LONG).show()

        // Anonymous session sign out karo — real OTP login clean state ma thay
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

        val intent = android.content.Intent(this, com.example.academyhub.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}