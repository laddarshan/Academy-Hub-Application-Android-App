package com.example.academyhub.exam

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityExamTimetableBinding
import com.example.academyhub.models.ExamTimetable
import com.example.academyhub.utils.ImageUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ExamTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExamTimetableBinding
    private lateinit var firestore: FirebaseFirestore

    private val TAG = "ExamTimetable"
    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""

    private var currentUploadType = ""
    private var internalDocId     = ""
    private var externalDocId     = ""

    // File picker
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { handleFilePicked(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExamTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvGroupBadge.text =
            listOf(sessionName, courseName, yearName, semesterName)
                .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        setupSwipeRefresh()
        setupClickListeners()
        loadExistingFiles()
    }

    // ─────────────────────────────────────────
    //  SwipeRefresh
    // ─────────────────────────────────────────
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadExistingFiles(fromSwipe = true)
        }
    }

    // ─────────────────────────────────────────
    //  Click listeners
    // ─────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        // Upload buttons
        binding.btnUploadInternal.setOnClickListener {
            currentUploadType = "internal"
            animateButtonPulse(it)
            showFilePickerBottomSheet()
        }
        binding.btnUploadExternal.setOnClickListener {
            currentUploadType = "external"
            animateButtonPulse(it)
            showFilePickerBottomSheet()
        }

        // Preview tap - open file
        binding.cardInternalPreview.setOnClickListener {
            animateCardTap(it)
            it.postDelayed({ openFile("internal") }, 120)
        }
        binding.cardExternalPreview.setOnClickListener {
            animateCardTap(it)
            it.postDelayed({ openFile("external") }, 120)
        }

        // Delete
        binding.btnDeleteInternal.setOnClickListener { confirmDelete("internal") }
        binding.btnDeleteExternal.setOnClickListener { confirmDelete("external") }
    }

    // ─────────────────────────────────────────
    //  Bottom Sheet File Picker (Image / PDF)
    // ─────────────────────────────────────────
    private fun showFilePickerBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view   = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_file_picker, null)

        // Image option
        view.findViewById<View>(R.id.btnPickImage).setOnClickListener {
            // Scale animation
            animateCardTap(it)
            it.postDelayed({
                dialog.dismiss()
                filePicker.launch("image/*")
            }, 120)
        }

        // PDF option
        view.findViewById<View>(R.id.btnPickPdf).setOnClickListener {
            animateCardTap(it)
            it.postDelayed({
                dialog.dismiss()
                filePicker.launch("application/pdf")
            }, 120)
        }

        dialog.setContentView(view)
        dialog.show()

        // Animate sheet items in
        view.post {
            val btnImage = view.findViewById<View>(R.id.btnPickImage)
            val btnPdf   = view.findViewById<View>(R.id.btnPickPdf)
            listOf(btnImage, btnPdf).forEachIndexed { i, v ->
                v.alpha        = 0f
                v.translationX = -24f
                v.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(220)
                    .setStartDelay(i * 60L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    // ─────────────────────────────────────────
    //  Handle picked file
    // ─────────────────────────────────────────
    private fun handleFilePicked(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        val isImage  = mimeType.startsWith("image/")
        val isPdf    = mimeType == "application/pdf"

        if (!isImage && !isPdf) {
            Toast.makeText(this, "Only image or PDF allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = getFileName(uri)

        if (isImage) {
            showUploadProgress(currentUploadType, true)
            val base64 = ImageUtil.convertImageToBase64Optimized(this, uri) ?: ""
            if (base64.isEmpty()) {
                showUploadProgress(currentUploadType, false)
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                return
            }
            uploadFileChunks(base64, "image", fileName, currentUploadType)
        } else {
            showUploadProgress(currentUploadType, true)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val base64 = uriToBase64(uri)
                    withContext(Dispatchers.Main) {
                        uploadFileChunks(base64, "pdf", fileName, currentUploadType)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showUploadProgress(currentUploadType, false)
                        Toast.makeText(this@ExamTimetableActivity,
                            "Failed to read PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────
    //  Upload chunks
    // ─────────────────────────────────────────
    private fun uploadFileChunks(
        base64: String, fileType: String, fileName: String, examType: String
    ) {
        val docId = firestore.collection("ExamTimetables").document().id
        val meta  = ExamTimetable(
            id = docId, sessionId = sessionId, sessionName = sessionName,
            courseId = courseId, courseName = courseName,
            yearId = yearId, yearName = yearName,
            semesterId = semesterId, semesterName = semesterName,
            examType = examType, fileType = fileType,
            fileName = fileName, uploadedAt = System.currentTimeMillis(), isActive = true
        )
        val oldDocId = if (examType == "internal") internalDocId else externalDocId

        fun doUpload() {
            firestore.collection("ExamTimetables").document(docId).set(meta)
                .addOnSuccessListener {
                    CoroutineScope(Dispatchers.IO).launch {
                        ImageUtil.uploadPhotoChunks(
                            firestore    = firestore,
                            userId       = docId,
                            collection   = "ExamTimetables",
                            base64String = base64,
                            onProgress   = {},
                            onSuccess    = {
                                runOnUiThread {
                                    showUploadProgress(examType, false)
                                    if (examType == "internal") internalDocId = docId
                                    else externalDocId = docId
                                    Toast.makeText(this@ExamTimetableActivity,
                                        "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                                    loadExistingFiles()
                                }
                            },
                            onFailure    = { e ->
                                runOnUiThread {
                                    showUploadProgress(examType, false)
                                    Toast.makeText(this@ExamTimetableActivity,
                                        "Upload failed: ${e?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
                .addOnFailureListener { e ->
                    showUploadProgress(examType, false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        if (oldDocId.isNotEmpty()) deleteDocAndChunks(oldDocId) { doUpload() }
        else doUpload()
    }

    // ─────────────────────────────────────────
    //  Load existing files
    // ─────────────────────────────────────────
    private fun loadExistingFiles(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBarTop.visibility = View.VISIBLE

        firestore.collection("ExamTimetables")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBarTop.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                var foundInternal = false
                var foundExternal = false

                for (doc in docs) {
                    val exam = doc.toObject(ExamTimetable::class.java)
                    if (exam.sessionId != sessionId || !exam.isActive) continue
                    when (exam.examType) {
                        "internal" -> { internalDocId = exam.id; showFilePreview(exam, "internal"); foundInternal = true }
                        "external" -> { externalDocId = exam.id; showFilePreview(exam, "external"); foundExternal = true }
                    }
                }

                if (!foundInternal) showEmptyState("internal")
                if (!foundExternal) showEmptyState("external")

                // Animate content in
                animateContentIn()
            }
            .addOnFailureListener { e ->
                binding.progressBarTop.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Load error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptyState(examType: String) {
        if (examType == "internal") {
            binding.layoutInternalEmpty.visibility   = View.VISIBLE
            binding.cardInternalPreview.visibility   = View.GONE
            binding.btnDeleteInternal.visibility     = View.GONE
        } else {
            binding.layoutExternalEmpty.visibility   = View.VISIBLE
            binding.cardExternalPreview.visibility   = View.GONE
            binding.btnDeleteExternal.visibility     = View.GONE
        }
    }

    // ─────────────────────────────────────────
    //  Show preview after upload/load
    // ─────────────────────────────────────────
    private fun showFilePreview(exam: ExamTimetable, examType: String) {
        val isInternal = examType == "internal"

        if (isInternal) {
            binding.layoutInternalEmpty.visibility = View.GONE
            binding.cardInternalPreview.visibility = View.VISIBLE
            binding.btnDeleteInternal.visibility   = View.VISIBLE

            if (exam.fileType == "image") {
                binding.ivInternalIcon.setImageResource(R.drawable.ic_image_file)
                binding.tvInternalFileType.text = "Image"
                binding.ivInternalThumbnail.visibility = View.VISIBLE
                loadImageThumbnail(exam.id, binding.ivInternalThumbnail)
            } else {
                binding.ivInternalIcon.setImageResource(R.drawable.ic_pdf)
                binding.tvInternalFileType.text = "PDF Document"
                binding.ivInternalThumbnail.visibility = View.GONE
            }
            binding.tvInternalFileName.text = exam.fileName
        } else {
            binding.layoutExternalEmpty.visibility = View.GONE
            binding.cardExternalPreview.visibility = View.VISIBLE
            binding.btnDeleteExternal.visibility   = View.VISIBLE

            if (exam.fileType == "image") {
                binding.ivExternalIcon.setImageResource(R.drawable.ic_image_file)
                binding.tvExternalFileType.text = "Image"
                binding.ivExternalThumbnail.visibility = View.VISIBLE
                loadImageThumbnail(exam.id, binding.ivExternalThumbnail)
            } else {
                binding.ivExternalIcon.setImageResource(R.drawable.ic_pdf)
                binding.tvExternalFileType.text = "PDF Document"
                binding.ivExternalThumbnail.visibility = View.GONE
            }
            binding.tvExternalFileName.text = exam.fileName
        }

        // Animate preview card in
        val card = if (isInternal) binding.cardInternalPreview else binding.cardExternalPreview
        card.alpha = 0f; card.translationY = 16f
        card.animate()
            .alpha(1f).translationY(0f)
            .setDuration(280).setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ─────────────────────────────────────────
    //  Open file
    // ─────────────────────────────────────────
    private fun openFile(examType: String) {
        val docId = if (examType == "internal") internalDocId else externalDocId
        if (docId.isEmpty()) return

        firestore.collection("ExamTimetables").document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                val exam = doc.toObject(ExamTimetable::class.java) ?: return@addOnSuccessListener
                if (exam.fileType == "image") openImageFullScreen(docId)
                else openPdfFile(docId, exam.fileName)
            }
    }

    private fun openImageFullScreen(docId: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))

        val imageView = ImageView(this)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)

        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.loadImageFromChunks(
                firestore   = firestore,
                userId      = docId,
                collection  = "ExamTimetables",
                imageView   = imageView,
                progressBar = null,
                onComplete  = {}
            )
        }
        dialog.show()
    }

    private fun openPdfFile(docId: String, fileName: String) {
        val loading = AlertDialog.Builder(this)
            .setMessage("Loading PDF...").setCancelable(false).create()
        loading.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val chunksSnap = firestore.collection("ExamTimetables")
                    .document(docId).collection("photo_chunks")
                    .orderBy("index").get().await()

                val sb = StringBuilder()
                for (doc in chunksSnap.documents.sortedBy { it.getLong("index") ?: 0 }) {
                    sb.append(doc.getString("data") ?: "")
                }

                val pdfBytes = Base64.decode(sb.toString(), Base64.DEFAULT)
                val cacheFile = java.io.File(cacheDir, fileName.ifEmpty { "exam_timetable.pdf" })
                cacheFile.writeBytes(pdfBytes)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@ExamTimetableActivity,
                    "${packageName}.provider",
                    cacheFile
                )
                withContext(Dispatchers.Main) {
                    loading.dismiss()
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivity(intent) }
                    catch (e: Exception) {
                        Toast.makeText(this@ExamTimetableActivity,
                            "No PDF viewer found", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading.dismiss()
                    Toast.makeText(this@ExamTimetableActivity,
                        "Error loading PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadImageThumbnail(docId: String, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.loadImageFromChunks(
                firestore   = firestore,
                userId      = docId,
                collection  = "ExamTimetables",
                imageView   = imageView,
                progressBar = null,
                onComplete  = {}
            )
        }
    }

    // ─────────────────────────────────────────
    //  Delete
    // ─────────────────────────────────────────
    private fun confirmDelete(examType: String) {
        val typeName = if (examType == "internal") "Internal" else "External"
        AlertDialog.Builder(this)
            .setTitle("Delete $typeName Timetable")
            .setMessage("Are you sure you want to delete this file?")
            .setPositiveButton("Delete") { _, _ ->
                val docId = if (examType == "internal") internalDocId else externalDocId
                if (docId.isEmpty()) return@setPositiveButton
                deleteDocAndChunks(docId) {
                    if (examType == "internal") {
                        internalDocId = ""
                        showEmptyState("internal")
                    } else {
                        externalDocId = ""
                        showEmptyState("external")
                    }
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDocAndChunks(docId: String, onDone: () -> Unit) {
        val ref = firestore.collection("ExamTimetables").document(docId)
        ref.collection("photo_chunks").get()
            .addOnSuccessListener { chunks ->
                val batch = firestore.batch()
                for (chunk in chunks) batch.delete(chunk.reference)
                batch.delete(ref)
                batch.commit().addOnSuccessListener { onDone() }
            }
            .addOnFailureListener { onDone() }
    }

    // ─────────────────────────────────────────
    //  Upload progress
    // ─────────────────────────────────────────
    private fun showUploadProgress(examType: String, show: Boolean) {
        if (examType == "internal") {
            binding.progressInternal.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnUploadInternal.isEnabled = !show
            binding.btnUploadInternal.alpha     = if (show) 0.5f else 1f
        } else {
            binding.progressExternal.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnUploadExternal.isEnabled = !show
            binding.btnUploadExternal.alpha     = if (show) 0.5f else 1f
        }
    }

    // ─────────────────────────────────────────
    //  Animations
    // ─────────────────────────────────────────
    private fun animateContentIn() {
        binding.layoutContent.visibility = View.VISIBLE
        if (binding.layoutContent.alpha == 1f) return  // Already shown

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutContent, "alpha", 0f, 1f)
                    .apply { duration = 350 },
                ObjectAnimator.ofFloat(binding.layoutContent, "translationY", 40f, 0f)
                    .apply { duration = 380; interpolator = DecelerateInterpolator() }
            )
            start()
        }

        // Stagger cards
        val cards = listOf(binding.cardInternal, binding.cardExternal)
        cards.forEachIndexed { i, card ->
            card.alpha        = 0f
            card.translationY = 30f
            card.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300)
                .setStartDelay(i * 80L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun animateButtonPulse(v: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.93f, 1f).apply { duration = 200 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.93f, 1f).apply {
                    duration = 200; interpolator = OvershootInterpolator()
                }
            )
            start()
        }
    }

    private fun animateCardTap(v: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.97f, 1f).apply { duration = 130 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.97f, 1f).apply { duration = 130 }
            )
            start()
        }
    }

    // ─────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────
    private fun uriToBase64(uri: Uri): String {
        val inputStream: InputStream = contentResolver.openInputStream(uri)!!
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun getFileName(uri: Uri): String {
        var name = "file"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name
    }

    private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>.await()
            : com.google.firebase.firestore.QuerySnapshot {
        return suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> cont.resume(result) }
            addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}
