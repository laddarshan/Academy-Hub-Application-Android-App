package com.example.academyhub.announcements

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityCreateAnnouncementBinding
import com.example.academyhub.models.Announcement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CreateAnnouncementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateAnnouncementBinding
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedPdfUri: Uri? = null
    private var pdfFileName = ""
    private val CHUNK_SIZE  = 500 * 1024
    private val PDF_PICK    = 101

    private var isEditMode         = false
    private var editAnnouncementId = ""
    private var selectedPriority   = "normal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateAnnouncementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isEditMode         = intent.getBooleanExtra("isEditMode", false)
        editAnnouncementId = intent.getStringExtra("announcementId") ?: ""

        if (isEditMode) {
            binding.tvHeaderTitle.text = "Edit Announcement"
            binding.tvSendLabel.text   = "Update Announcement"
            loadExistingData()
        }

        binding.btnBack.setOnClickListener { finish() }

        // Send button entrance
        binding.btnSend.alpha = 0f; binding.btnSend.translationY = 24f
        binding.btnSend.animate().alpha(1f).translationY(0f)
            .setDuration(380).setStartDelay(200).setInterpolator(OvershootInterpolator(0.8f)).start()

        loadCourses()
        setupPriorityToggle()
        setupAllStudentsRow()

        // PDF
        binding.btnAttachPdf.setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/pdf" }, PDF_PICK)
        }
        binding.btnRemovePdf.setOnClickListener { clearPdf() }

        // Send
        binding.btnSend.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.94f, 1f).apply { duration = 130 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.94f, 1f).apply { duration = 130 }
                ); start()
            }
            it.postDelayed({ validateAndSend() }, 130)
        }
    }

    // ─── Priority Toggle ───────────────────────────────────────────────────────

    private fun setupPriorityToggle() {
        binding.btnNormal.setOnClickListener    { setPriority("normal")    }
        binding.btnImportant.setOnClickListener { setPriority("important") }
        setPriority("normal")
    }

    private fun setPriority(p: String) {
        selectedPriority      = p
        binding.rbNormal.isChecked    = (p == "normal")
        binding.rbImportant.isChecked = (p == "important")

        if (p == "normal") {
            binding.btnNormal.setBackgroundResource(R.drawable.bg_day_chip_active)
            binding.tvNormalLabel.setTextColor(android.graphics.Color.WHITE)
            binding.btnImportant.setBackgroundResource(R.drawable.bg_day_chip_inactive)
            binding.tvImportantLabel.setTextColor(android.graphics.Color.parseColor("#AAFFEE"))
        } else {
            binding.btnNormal.setBackgroundResource(R.drawable.bg_day_chip_inactive)
            binding.tvNormalLabel.setTextColor(android.graphics.Color.parseColor("#AAFFEE"))
            binding.btnImportant.setBackgroundResource(R.drawable.bg_day_chip_active)
            binding.btnImportant.setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
            binding.tvImportantLabel.setTextColor(android.graphics.Color.WHITE)
        }
    }

    // ─── All Students row ──────────────────────────────────────────────────────

    private fun setupAllStudentsRow() {
        binding.rowAllStudents.setOnClickListener { binding.cbAllStudents.toggle() }
        binding.cbAllStudents.setOnCheckedChangeListener { _, checked ->
            for (i in 0 until binding.llCourseCheckboxes.childCount) {
                val v = binding.llCourseCheckboxes.getChildAt(i)
                v.alpha      = if (checked) 0.4f else 1f
                v.isEnabled  = !checked
                if (checked) (v as? CheckBox)?.isChecked = false
            }
        }
    }

    // ─── PDF ───────────────────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PDF_PICK && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            selectedPdfUri = uri
            pdfFileName    = getFileName(uri)
            val sizeKb     = getFileSizeKb(uri)
            showPdfPreview(pdfFileName, sizeKb)
        }
    }

    private fun showPdfPreview(name: String, sizeKb: Long) {
        binding.cardPdfPreview.visibility = View.VISIBLE
        binding.btnAttachPdf.visibility   = View.GONE
        binding.tvPdfName.text  = name
        binding.tvPdfSize.text  = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
        // Slide up animation
        binding.cardPdfPreview.alpha        = 0f
        binding.cardPdfPreview.translationY = 20f
        binding.cardPdfPreview.animate().alpha(1f).translationY(0f)
            .setDuration(320).setInterpolator(OvershootInterpolator(0.8f)).start()
    }

    private fun clearPdf() {
        selectedPdfUri = null; pdfFileName = ""
        binding.cardPdfPreview.visibility = View.GONE
        binding.btnAttachPdf.visibility   = View.VISIBLE
    }

    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return "attachment.pdf"
    }

    private fun getFileSizeKb(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0) return it.getLong(idx) / 1024
            }
        }
        return 0L
    }

    // ─── Courses ───────────────────────────────────────────────────────────────

    private fun loadCourses() {
        db.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { snap ->
                binding.llCourseCheckboxes.removeAllViews()
                for (doc in snap) {
                    val name = doc.getString("courseName") ?: continue
                    val cb   = CheckBox(this).apply {
                        text     = name; tag = doc.id
                        textSize = 14f
                        buttonTintList = androidx.core.content.ContextCompat.getColorStateList(this@CreateAnnouncementActivity, android.R.color.darker_gray)
                        setPadding(0, 10, 0, 10)
                    }
                    binding.llCourseCheckboxes.addView(cb)
                }
                if (isEditMode) restoreEditSentTo()
            }
    }

    private fun restoreEditSentTo() {
        db.collection("announcements").document(editAnnouncementId).get()
            .addOnSuccessListener { doc ->
                val sentTo = doc.get("sentTo") as? List<*> ?: return@addOnSuccessListener
                for (i in 0 until binding.llCourseCheckboxes.childCount) {
                    val cb = binding.llCourseCheckboxes.getChildAt(i) as? CheckBox
                    if (sentTo.contains(cb?.tag.toString())) cb?.isChecked = true
                }
            }
    }

    // ─── Load existing (edit mode) ─────────────────────────────────────────────

    private fun loadExistingData() {
        db.collection("announcements").document(editAnnouncementId).get()
            .addOnSuccessListener { doc ->
                binding.etTitle.setText(doc.getString("title") ?: "")
                binding.etMessage.setText(doc.getString("message") ?: "")
                setPriority(doc.getString("priority") ?: "normal")
                val hasPdf = doc.getBoolean("hasPdf") ?: false
                if (hasPdf) {
                    pdfFileName = doc.getString("pdfFileName") ?: ""
                    showPdfPreview("$pdfFileName (existing)", 0L)
                }
                val sentTo = doc.get("sentTo") as? List<*> ?: listOf<String>()
                if (sentTo.contains("all")) binding.cbAllStudents.isChecked = true
            }
    }

    // ─── Send ──────────────────────────────────────────────────────────────────

    private fun validateAndSend() {
        val title   = binding.etTitle.text.toString().trim()
        val message = binding.etMessage.text.toString().trim()
        if (title.isEmpty())   { binding.etTitle.error   = "Title is required"; return }
        if (message.isEmpty()) { binding.etMessage.error = "Message is required"; return }

        val sentTo = mutableListOf<String>()
        if (binding.cbAllStudents.isChecked) {
            sentTo.add("all")
        } else {
            for (i in 0 until binding.llCourseCheckboxes.childCount) {
                val cb = binding.llCourseCheckboxes.getChildAt(i) as? CheckBox
                if (cb?.isChecked == true) sentTo.add(cb.tag.toString())
            }
        }
        if (sentTo.isEmpty()) {
            Toast.makeText(this, "Please select who to send this announcement to", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isClickable    = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val uid       = auth.currentUser?.uid ?: ""
                val adminName = db.collection("users").document(uid).get().await().getString("name") ?: "Admin"

                val annRef = if (isEditMode) db.collection("announcements").document(editAnnouncementId)
                else db.collection("announcements").document()

                val ann = Announcement(
                    id            = annRef.id,
                    title         = title, message = message,
                    priority      = selectedPriority, sentTo = sentTo,
                    hasPdf        = selectedPdfUri != null || (isEditMode && pdfFileName.isNotEmpty()),
                    pdfFileName   = pdfFileName,
                    createdBy     = uid, createdByName = adminName,
                    createdAt     = System.currentTimeMillis()
                )

                if (selectedPdfUri != null) {
                    if (isEditMode) {
                        val old = annRef.collection("pdfChunks").get().await()
                        for (c in old) c.reference.delete().await()
                    }
                    val b64    = Base64.encodeToString(
                        contentResolver.openInputStream(selectedPdfUri!!)?.readBytes() ?: byteArrayOf(),
                        Base64.NO_WRAP)
                    val chunks = b64.chunked(CHUNK_SIZE)
                    ann.pdfChunkCount = chunks.size
                    for ((idx, chunk) in chunks.withIndex()) {
                        annRef.collection("pdfChunks").document("chunk_$idx")
                            .set(hashMapOf("data" to chunk, "index" to idx)).await()
                    }
                }
                annRef.set(ann, com.google.firebase.firestore.SetOptions.merge()).await()
                if (!isEditMode) sendFcm(sentTo, annRef.id, title, message, selectedPriority)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@CreateAnnouncementActivity,
                        if (isEditMode) "Updated successfully!" else "Announcement sent successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSend.isClickable    = true
                    Toast.makeText(this@CreateAnnouncementActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendFcm(sentTo: List<String>, annId: String, title: String, msg: String, priority: String) {
        val serverKey = "YOUR_FCM_SERVER_KEY"
        val topics    = if (sentTo.contains("all")) listOf("all_students") else sentTo.map { "course_$it" }
        for (topic in topics) {
            try {
                val conn = (URL("https://fcm.googleapis.com/fcm/send").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "key=$serverKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val json = JSONObject().apply {
                    put("to", "/topics/$topic")
                    put("notification", JSONObject().apply { put("title", title); put("body", msg) })
                    put("data", JSONObject().apply {
                        put("type", "announcement"); put("announcementId", annId); put("priority", priority)
                    })
                }
                conn.outputStream.write(json.toString().toByteArray())
                conn.outputStream.flush(); conn.responseCode; conn.disconnect()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}