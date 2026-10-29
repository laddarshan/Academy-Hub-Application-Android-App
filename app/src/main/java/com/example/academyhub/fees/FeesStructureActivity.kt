package com.example.academyhub.fees

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityFeesStructureBinding
import com.example.academyhub.models.FeesStructure
import com.google.firebase.firestore.FirebaseFirestore

class FeesStructureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeesStructureBinding
    private lateinit var firestore: FirebaseFirestore

    private var courseId    = ""
    private var courseName  = ""
    private var yearId      = ""
    private var yearName    = ""
    private var semesterId  = ""
    private var semesterName = ""
    private var existingStructureId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeesStructureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvGroupBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        setupAutoCalculate()
        loadExistingStructure()
        setupSaveButton()
        setupDeleteButton()

        binding.btnBack.setOnClickListener { finish() }

        // Entrance animations
        binding.btnSave.alpha = 0f; binding.btnSave.translationY = 24f
        binding.btnSave.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200)
            .setInterpolator(OvershootInterpolator(0.8f)).start()
    }

    // ─── Auto calculate ────────────────────────────────────────────────────────

    private fun setupAutoCalculate() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val total = binding.etTotalFees.text.toString().toDoubleOrNull() ?: 0.0
                val count = binding.etInstallments.text.toString().toIntOrNull() ?: 0
                if (total > 0 && count > 0) {
                    val per = total / count
                    binding.tvCalculated.text = "Each installment: ₹${String.format("%,.0f", per)}"
                } else {
                    binding.tvCalculated.text = ""
                }
            }
        }
        binding.etTotalFees.addTextChangedListener(watcher)
        binding.etInstallments.addTextChangedListener(watcher)
    }

    // ─── Load existing ─────────────────────────────────────────────────────────

    private fun loadExistingStructure() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("FeesStructure")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                if (!docs.isEmpty) {
                    val doc = docs.documents[0]
                    val s   = doc.toObject(FeesStructure::class.java)!!
                    existingStructureId = doc.id

                    // Show existing card with animation
                    binding.cardCurrentStructure.visibility = View.VISIBLE
                    binding.cardCurrentStructure.alpha = 0f
                    binding.cardCurrentStructure.animate().alpha(1f).setDuration(380).start()

                    // Count-up animation for total
                    ValueAnimator.ofFloat(0f, s.totalFees.toFloat()).apply {
                        duration = 700
                        addUpdateListener {
                            binding.tvCurrentTotal.text = "₹${String.format("%,.0f", (it.animatedValue as Float).toDouble())}"
                        }
                        start()
                    }
                    binding.tvCurrentInstallments.text = "${s.installmentCount}"
                    binding.tvCurrentInstAmt.text      = "₹${String.format("%,.0f", s.installmentAmount)}"

                    if (s.description.isNotEmpty()) {
                        binding.tvCurrentDescription.text    = s.description
                        binding.tvCurrentDescription.visibility = View.VISIBLE
                    }

                    // Pre-fill form for editing
                    binding.etTotalFees.setText(s.totalFees.toInt().toString())
                    binding.etInstallments.setText(s.installmentCount.toString())
                    binding.etDescription.setText(s.description)
                    binding.tvFormTitle.text  = "Update Fees Structure"
                    binding.tvSaveLabel.text  = "Update Fees Structure"
                    binding.tvTitle.text      = "Edit Fees Structure"

                    // Show delete button
                    binding.btnDelete.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    // ─── Save ──────────────────────────────────────────────────────────────────

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            pulseBtn(binding.btnSave)
            binding.btnSave.postDelayed({ saveOrUpdate() }, 130)
        }
    }

    private fun saveOrUpdate() {
        val totalStr = binding.etTotalFees.text.toString().trim()
        val instStr  = binding.etInstallments.text.toString().trim()

        if (totalStr.isEmpty()) { binding.etTotalFees.error = "Enter total fees"; return }
        if (instStr.isEmpty())  { binding.etInstallments.error = "Enter installments"; return }

        val total = totalStr.toDouble()
        val count = instStr.toInt()

        if (total <= 0) { binding.etTotalFees.error = "Invalid amount"; return }
        if (count <= 0) { binding.etInstallments.error = "Invalid number"; return }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        val id = existingStructureId.ifEmpty {
            firestore.collection("FeesStructure").document().id
        }
        val structure = FeesStructure(
            id                = id,
            courseId          = courseId,
            courseName        = courseName,
            semesterId        = semesterId,
            semesterName      = semesterName,
            totalFees         = total,
            installmentCount  = count,
            installmentAmount = total / count,
            description       = binding.etDescription.text.toString().trim(),
            updatedAt         = System.currentTimeMillis()
        )

        firestore.collection("FeesStructure").document(id).set(structure)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                updateStudentsTotalFees(total)
                Toast.makeText(this,
                    if (existingStructureId.isNotEmpty()) "Fees structure updated!"
                    else "Fees structure saved!",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    private fun setupDeleteButton() {
        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Fees Structure")
                .setMessage("$courseName - $semesterName ni fees structure delete karvasho? Students ni total fees zero thai jase.")
                .setPositiveButton("Delete") { _, _ -> deleteStructure() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun deleteStructure() {
        if (existingStructureId.isEmpty()) return
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("FeesStructure").document(existingStructureId).delete()
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                // Reset students' totalFees to 0
                updateStudentsTotalFees(0.0)
                Toast.makeText(this, "Fees structure deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Update students ───────────────────────────────────────────────────────

    private fun updateStudentsTotalFees(newTotal: Double) {
        firestore.collection("Students")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                val batch = firestore.batch()
                for (doc in docs) {
                    val paid    = doc.getDouble("paidFees") ?: 0.0
                    val pending = (newTotal - paid).coerceAtLeast(0.0)
                    val status  = when {
                        newTotal <= 0       -> "pending"
                        paid >= newTotal    -> "paid"
                        paid > 0            -> "partial"
                        else                -> "pending"
                    }
                    batch.update(doc.reference, mapOf(
                        "totalFees"  to newTotal,
                        "feesStatus" to status,
                        "updatedAt"  to System.currentTimeMillis()
                    ))
                }
                batch.commit()
            }
    }

    private fun pulseBtn(v: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.94f, 1f).apply { duration = 130; interpolator = OvershootInterpolator() },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.94f, 1f).apply { duration = 130; interpolator = OvershootInterpolator() }
            ); start()
        }
    }
}
