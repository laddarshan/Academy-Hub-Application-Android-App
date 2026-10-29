package com.example.academyhub.session

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAddSessionBinding
import com.example.academyhub.models.Session
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AddSessionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSessionBinding
    private val db  = FirebaseFirestore.getInstance()
    private val cal = Calendar.getInstance()
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Edit mode
    private var isEditMode  = false
    private var editSession = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode  = intent.getBooleanExtra("isEditMode", false)
        editSession = intent.getStringExtra("sessionId") ?: ""

        if (isEditMode) {
            binding.tvHeaderTitle.text = "Edit Session"
            binding.tvSaveLabel.text   = "Update Session"
            prefillEditData()
        }

        binding.btnBack.setOnClickListener { finish() }

        // Year TextWatchers → auto session name
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { autoSessionName() }
        }
        binding.etStartYear.addTextChangedListener(watcher)
        binding.etEndYear.addTextChangedListener(watcher)

        // Date pickers
        binding.rowStartDate.setOnClickListener { showDatePicker { date -> binding.etStartDate.text = date; binding.etStartDate.setTextColor(resources.getColor(android.R.color.black, null)) } }
        binding.rowEndDate.setOnClickListener   { showDatePicker { date -> binding.etEndDate.text = date; binding.etEndDate.setTextColor(resources.getColor(android.R.color.black, null)) } }

        // Set current checkbox via row tap
        binding.rowSetCurrent.setOnClickListener { binding.cbSetCurrent.toggle() }

        // Save button entrance animation
        binding.btnSave.alpha = 0f; binding.btnSave.translationY = 24f
        binding.btnSave.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200).setInterpolator(OvershootInterpolator(0.8f)).start()

        binding.btnSave.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
                ); start()
            }
            it.postDelayed({ saveSession() }, 130)
        }
    }

    private fun prefillEditData() {
        binding.etStartYear.setText(intent.getStringExtra("startYear") ?: "")
        binding.etEndYear.setText(intent.getStringExtra("endYear") ?: "")
        val sDate = intent.getStringExtra("startDate") ?: ""
        val eDate = intent.getStringExtra("endDate") ?: ""
        if (sDate.isNotEmpty()) { binding.etStartDate.text = sDate; binding.etStartDate.setTextColor(Color.BLACK) }
        if (eDate.isNotEmpty()) { binding.etEndDate.text = eDate; binding.etEndDate.setTextColor(Color.BLACK) }
        binding.etSessionName.setText(intent.getStringExtra("sessionName") ?: "")
        binding.cbSetCurrent.isChecked = intent.getBooleanExtra("isCurrent", false)
    }

    private fun autoSessionName() {
        val s = binding.etStartYear.text.toString()
        val e = binding.etEndYear.text.toString()
        if (s.length == 4 && e.length == 4) binding.etSessionName.setText("$s-$e")
    }

    private fun showDatePicker(onPicked: (String) -> Unit) {
        DatePickerDialog(this,
            { _, y, m, d ->
                cal.set(y, m, d)
                onPicked(fmt.format(cal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveSession() {
        val startYear = binding.etStartYear.text.toString().trim()
        val endYear   = binding.etEndYear.text.toString().trim()
        val startDate = binding.etStartDate.text.toString().trim()
        val endDate   = binding.etEndDate.text.toString().trim()

        if (startYear.length != 4) { binding.etStartYear.error = "Enter valid 4-digit year"; return }
        if (endYear.length != 4)   { binding.etEndYear.error   = "Enter valid 4-digit year"; return }
        if (startDate == "Select start date" || startDate.isEmpty()) {
            Toast.makeText(this, "Start date select karo", Toast.LENGTH_SHORT).show(); return
        }
        if (endDate == "Select end date" || endDate.isEmpty()) {
            Toast.makeText(this, "End date select karo", Toast.LENGTH_SHORT).show(); return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isClickable    = false

        val setAsCurrent = binding.cbSetCurrent.isChecked

        if (setAsCurrent) {
            // Unset all current sessions first, then save
            db.collection("Sessions").whereEqualTo("current", true).get()
                .addOnSuccessListener { docs ->
                    val batch = db.batch()
                    for (doc in docs) batch.update(doc.reference, "current", false)
                    batch.commit().addOnSuccessListener { persistSession(startYear, endYear, startDate, endDate, true) }
                        .addOnFailureListener { e -> showError(e.message) }
                }
                .addOnFailureListener { e -> showError(e.message) }
        } else {
            persistSession(startYear, endYear, startDate, endDate, false)
        }
    }

    private fun persistSession(startYear: String, endYear: String, startDate: String, endDate: String, isCurrent: Boolean) {
        val sessionName = "$startYear-$endYear"

        // Build map with correct Firestore field names ("current", "active")
        val data = hashMapOf(
            "sessionName" to sessionName,
            "startYear"   to startYear,
            "endYear"     to endYear,
            "startDate"   to startDate,
            "endDate"     to endDate,
            "current"     to isCurrent,        // @PropertyName("current")
            "active"      to true,             // @PropertyName("active")
            "createdAt"   to System.currentTimeMillis(),
            "createdBy"   to "admin"
        )

        if (isEditMode) {
            // UPDATE existing document — don't overwrite id/createdAt
            val updateData = hashMapOf<String, Any>(
                "sessionName" to sessionName,
                "startYear"   to startYear,
                "endYear"     to endYear,
                "startDate"   to startDate,
                "endDate"     to endDate,
                "current"     to isCurrent,
                "active"      to true
            )
            db.collection("Sessions").document(editSession)
                .update(updateData)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Session updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e -> showError(e.message) }
        } else {
            // CREATE new document
            val newRef = db.collection("Sessions").document()
            data["id"] = newRef.id
            newRef.set(data)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Session created!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e -> showError(e.message) }
        }
    }

    private fun showError(msg: String?) {
        binding.progressBar.visibility = View.GONE
        binding.btnSave.isClickable    = true
        Toast.makeText(this, "Error: $msg", Toast.LENGTH_SHORT).show()
    }
}