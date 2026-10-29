package com.example.academyhub.teacher

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAddTeacherBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddTeacherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTeacherBinding
    private lateinit var firestore: FirebaseFirestore
    private var selectedImageBase64: String = ""
    private var isEditMode = false
    private var teacherId: String = ""
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageBase64 = ImageUtil.convertImageToBase64Optimized(this, it) ?: ""
            if (selectedImageBase64.isNotEmpty()) {
                binding.ivTeacherProfile.setImageURI(it)
                Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTeacherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        isEditMode = intent.getBooleanExtra("isEdit", false)
        teacherId  = intent.getStringExtra("teacherId") ?: ""

        if (isEditMode) {
            binding.tvTitle.text   = "Edit Teacher"
            binding.btnSave.text   = "Update Teacher"
            loadTeacherData()
        }

        setupDropdowns()
        setupDatePickers()
        setupClickListeners()
    }

    private fun setupDropdowns() {
        val genderOptions = arrayOf("Male", "Female", "Other")
        binding.spinnerGender.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        )

        val statusOptions = arrayOf("Active", "Inactive", "Blocked")
        binding.spinnerStatus.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusOptions)
        )
        binding.spinnerStatus.setText("Active", false)
    }

    private fun setupDatePickers() {
        // Date of Birth
        binding.etDOB.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -25)
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.etDOB.setText(dateFormat.format(calendar.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Joining Date
        binding.etJoiningDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.etJoiningDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                if (isEditMode) updateTeacher() else saveTeacher()
            }
        }
    }

    private fun loadTeacherData() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Teachers")
            .document(teacherId)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE

                if (document.exists()) {
                    val teacher = document.toObject(Teacher::class.java)
                    teacher?.let {
                        // Personal Information
                        binding.etName.setText(it.name)
                        binding.etMobile.setText(it.mobile)
                        binding.etEmail.setText(it.email)
                        binding.spinnerGender.setText(it.gender, false)
                        binding.etDOB.setText(it.dateOfBirth)
                        binding.etAddress.setText(it.address)

                        // Professional Information
                        binding.etSubject.setText(it.subject)
                        binding.etQualification.setText(it.qualification)
                        binding.etExperience.setText(it.experience.toString())
                        binding.etJoiningDate.setText(it.joiningDate)

                        // Salary Information
                        binding.etBasicSalary.setText(it.basicSalary.toString())
                        binding.etAllowances.setText(it.allowances.toString())
                        binding.etAccountNumber.setText(it.accountNumber)
                        binding.etIfscCode.setText(it.ifscCode)

                        // ─── FIX: capitalize() deprecated → replaceFirstChar ───
                        val statusDisplay = it.status
                            .replaceFirstChar { c -> c.uppercaseChar() }
                        binding.spinnerStatus.setText(statusDisplay, false)

                        // Load profile image
                        loadProfileImageFromChunks(teacherId)
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImageFromChunks(teacherId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                ImageUtil.loadImageFromChunks(
                    firestore = firestore,
                    userId = teacherId,
                    collection = "Teachers",
                    imageView = binding.ivTeacherProfile,
                    progressBar = binding.progressBar,
                    onComplete = { binding.progressBar.visibility = View.GONE }
                )
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                e.printStackTrace()
            }
        }
    }

    private fun saveTeacher() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        val newTeacherId = firestore.collection("Teachers").document().id

        val teacher = Teacher(
            id            = newTeacherId,
            name          = binding.etName.text.toString().trim(),
            mobile        = binding.etMobile.text.toString().trim(),
            email         = binding.etEmail.text.toString().trim(),
            gender        = binding.spinnerGender.text.toString(),
            dateOfBirth   = binding.etDOB.text.toString(),
            address       = binding.etAddress.text.toString().trim(),
            subject       = binding.etSubject.text.toString().trim(),
            qualification = binding.etQualification.text.toString().trim(),
            experience    = binding.etExperience.text.toString().toIntOrNull() ?: 0,
            joiningDate   = binding.etJoiningDate.text.toString(),
            basicSalary   = binding.etBasicSalary.text.toString().toDoubleOrNull() ?: 0.0,
            allowances    = binding.etAllowances.text.toString().toDoubleOrNull() ?: 0.0,
            accountNumber = binding.etAccountNumber.text.toString().trim(),
            ifscCode      = binding.etIfscCode.text.toString().trim(),
            status        = binding.spinnerStatus.text.toString().lowercase(),
            createdAt     = System.currentTimeMillis(),
            updatedAt     = System.currentTimeMillis()
        )

        teacher.currentSalary = teacher.basicSalary + teacher.allowances

        firestore.collection("Teachers")
            .document(newTeacherId)
            .set(teacher)
            .addOnSuccessListener {
                if (selectedImageBase64.isNotEmpty()) {
                    uploadPhotoWithProgress(newTeacherId)
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, "Teacher added successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadPhotoWithProgress(teacherId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.uploadPhotoChunks(
                firestore     = firestore,
                userId        = teacherId,
                collection    = "Teachers",
                base64String  = selectedImageBase64,
                onProgress    = {},
                onSuccess = {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled = true
                        Toast.makeText(
                            this@AddTeacherActivity,
                            if (isEditMode) "Teacher updated with photo!" else "Teacher added with photo!",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                },
                onFailure = {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled = true
                        Toast.makeText(
                            this@AddTeacherActivity,
                            if (isEditMode) "Teacher updated but photo upload failed"
                            else "Teacher added but photo upload failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            )
        }
    }

    private fun updateTeacher() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        val basicSalary = binding.etBasicSalary.text.toString().toDoubleOrNull() ?: 0.0
        val allowances  = binding.etAllowances.text.toString().toDoubleOrNull() ?: 0.0

        val updates = hashMapOf<String, Any>(
            "name"          to binding.etName.text.toString().trim(),
            "email"         to binding.etEmail.text.toString().trim(),
            "mobile"        to binding.etMobile.text.toString().trim(),
            "gender"        to binding.spinnerGender.text.toString(),
            "dateOfBirth"   to binding.etDOB.text.toString(),
            "address"       to binding.etAddress.text.toString().trim(),
            "subject"       to binding.etSubject.text.toString().trim(),
            "qualification" to binding.etQualification.text.toString().trim(),
            "experience"    to (binding.etExperience.text.toString().toIntOrNull() ?: 0),
            "joiningDate"   to binding.etJoiningDate.text.toString(),
            "basicSalary"   to basicSalary,
            "allowances"    to allowances,
            "currentSalary" to (basicSalary + allowances),
            "accountNumber" to binding.etAccountNumber.text.toString().trim(),
            "ifscCode"      to binding.etIfscCode.text.toString().trim(),
            "status"        to binding.spinnerStatus.text.toString().lowercase(),
            "updatedAt"     to System.currentTimeMillis()
        )

        firestore.collection("Teachers")
            .document(teacherId)
            .update(updates)
            .addOnSuccessListener {
                if (selectedImageBase64.isNotEmpty()) {
                    uploadPhotoWithProgress(teacherId)
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this, "Teacher updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun validateInputs(): Boolean {
        // Name
        if (binding.etName.text.toString().trim().isEmpty()) {
            binding.etName.error = "Name is required"
            binding.etName.requestFocus()
            return false
        }

        // Mobile
        val mobile = binding.etMobile.text.toString().trim()
        if (mobile.isEmpty()) {
            binding.etMobile.error = "Mobile is required"
            binding.etMobile.requestFocus()
            return false
        }
        if (mobile.length != 10 || !mobile.all { it.isDigit() }) {
            binding.etMobile.error = "Enter valid 10-digit mobile number"
            binding.etMobile.requestFocus()
            return false
        }

        // Email
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Enter valid email"
            binding.etEmail.requestFocus()
            return false
        }

        // Gender
        if (binding.spinnerGender.text.toString().isEmpty()) {
            Toast.makeText(this, "Please select gender", Toast.LENGTH_SHORT).show()
            return false
        }

        // DOB
        if (binding.etDOB.text.toString().isEmpty()) {
            binding.etDOB.error = "Date of birth is required"
            binding.etDOB.requestFocus()
            return false
        }

        // Subject
        if (binding.etSubject.text.toString().trim().isEmpty()) {
            binding.etSubject.error = "Subject is required"
            binding.etSubject.requestFocus()
            return false
        }

        // Qualification
        if (binding.etQualification.text.toString().trim().isEmpty()) {
            binding.etQualification.error = "Qualification is required"
            binding.etQualification.requestFocus()
            return false
        }

        // Experience
        val experience = binding.etExperience.text.toString().trim()
        if (experience.isEmpty()) {
            binding.etExperience.error = "Experience is required"
            binding.etExperience.requestFocus()
            return false
        }
        if (experience.toIntOrNull() == null || experience.toInt() < 0) {
            binding.etExperience.error = "Enter valid experience"
            binding.etExperience.requestFocus()
            return false
        }

        // Joining Date
        if (binding.etJoiningDate.text.toString().isEmpty()) {
            binding.etJoiningDate.error = "Joining date is required"
            binding.etJoiningDate.requestFocus()
            return false
        }

        return true
    }
}
