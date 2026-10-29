package com.example.academyhub.batch

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAddBatchBinding
import com.example.academyhub.models.Batch
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AddBatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBatchBinding
    private val db = FirebaseFirestore.getInstance()

    private var isEditMode = false
    private var batchId    = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddBatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode = intent.getBooleanExtra("isEdit", false)
        batchId    = intent.getStringExtra("batchId") ?: ""

        if (isEditMode) {
            binding.tvTitle.text     = "Edit Batch"
            binding.tvSaveLabel.text = "Update Batch"
            loadBatchData()
        }

        setupTimePickers()
        binding.btnBack.setOnClickListener { finish() }

        // Save button entrance animation
        binding.btnSave.alpha        = 0f
        binding.btnSave.translationY = 24f
        binding.btnSave.animate()
            .alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f)).start()

        binding.btnSave.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
                ); start()
            }
            it.postDelayed({ if (isEditMode) updateBatch() else saveBatch() }, 130)
        }
    }

    // ─── Time pickers — now on rowStartTime / rowEndTime ──────────────────────

    private fun setupTimePickers() {
        val showTimePicker: (onPicked: (String) -> Unit) -> Unit = { onPicked ->
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val h   = if (hour == 0 || hour == 12) 12 else hour % 12
                val ampm = if (hour >= 12) "PM" else "AM"
                onPicked(String.format("%02d:%02d %s", h, minute, ampm))
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }

        binding.rowStartTime.setOnClickListener {
            showTimePicker { time ->
                binding.etStartTime.text = time
                binding.etStartTime.setTextColor(Color.parseColor("#075E54"))
            }
        }
        binding.rowEndTime.setOnClickListener {
            showTimePicker { time ->
                binding.etEndTime.text = time
                binding.etEndTime.setTextColor(Color.parseColor("#075E54"))
            }
        }
    }

    // ─── Load existing batch data ──────────────────────────────────────────────

    private fun loadBatchData() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("Batches").document(batchId).get()
            .addOnSuccessListener { doc ->
                binding.progressBar.visibility = View.GONE
                val batch = doc.toObject(Batch::class.java) ?: return@addOnSuccessListener
                binding.etBatchName.setText(batch.batchName)
                binding.etBatchCode.setText(batch.batchCode)
                val sTime = batch.startTime
                val eTime = batch.endTime
                if (sTime.isNotEmpty()) {
                    binding.etStartTime.text = sTime
                    binding.etStartTime.setTextColor(Color.parseColor("#075E54"))
                }
                if (eTime.isNotEmpty()) {
                    binding.etEndTime.text = eTime
                    binding.etEndTime.setTextColor(Color.parseColor("#075E54"))
                }
                binding.etDescription.setText(batch.description)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Save new batch — HashMap with correct "active" field ─────────────────

    private fun saveBatch() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isClickable    = false

        val newId = db.collection("Batches").document().id

        val data = hashMapOf<String, Any>(
            "id"          to newId,
            "batchName"   to binding.etBatchName.text.toString().trim(),
            "batchCode"   to binding.etBatchCode.text.toString().trim().uppercase(),
            "startTime"   to binding.etStartTime.text.toString(),
            "endTime"     to binding.etEndTime.text.toString(),
            "description" to binding.etDescription.text.toString().trim(),
            "active"      to true,          // ✅ correct Firestore field name
            "createdAt"   to System.currentTimeMillis(),
            "createdBy"   to "admin",
            "courseId"    to "",
            "courseName"  to ""
        )

        db.collection("Batches").document(newId).set(data)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Batch created!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Update existing batch ─────────────────────────────────────────────────

    private fun updateBatch() {
        if (!validate()) return
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isClickable    = false

        val updates = hashMapOf<String, Any>(
            "batchName"   to binding.etBatchName.text.toString().trim(),
            "batchCode"   to binding.etBatchCode.text.toString().trim().uppercase(),
            "startTime"   to binding.etStartTime.text.toString(),
            "endTime"     to binding.etEndTime.text.toString(),
            "description" to binding.etDescription.text.toString().trim()
        )

        db.collection("Batches").document(batchId).update(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Batch updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isClickable    = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Validate ──────────────────────────────────────────────────────────────

    private fun validate(): Boolean {
        if (binding.etBatchName.text.toString().trim().isEmpty()) {
            binding.etBatchName.error = "Batch name jaruri chhe"; return false
        }
        if (binding.etBatchCode.text.toString().trim().isEmpty()) {
            binding.etBatchCode.error = "Batch code jaruri chhe"; return false
        }
        val startText = binding.etStartTime.text.toString()
        if (startText == "Select" || startText.isEmpty()) {
            Toast.makeText(this, "Start time select karo", Toast.LENGTH_SHORT).show(); return false
        }
        val endText = binding.etEndTime.text.toString()
        if (endText == "Select" || endText.isEmpty()) {
            Toast.makeText(this, "End time select karo", Toast.LENGTH_SHORT).show(); return false
        }
        return true
    }
}
