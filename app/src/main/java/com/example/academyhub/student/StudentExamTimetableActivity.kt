package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentExamTimetableBinding
import com.example.academyhub.models.ExamTimetable
import com.example.academyhub.models.Student
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class StudentExamTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentExamTimetableBinding
    private val db    = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    private var sessionId    = ""
    private var sessionName  = ""
    private var courseId     = ""
    private var courseName   = ""
    private var yearId       = ""
    private var yearName     = ""
    private var semesterId   = ""
    private var semesterName = ""
    private var studentDocId = ""

    private var internalDocId = ""
    private var externalDocId = ""
    private var currentTab    = "internal"  // internal / external

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, StudentExamTimetableActivity::class.java).also { i ->
                i.putExtra("studentDocId", student.id)
                i.putExtra("sessionId",    student.sessionId)
                i.putExtra("sessionName",  student.sessionName)
                i.putExtra("courseId",     student.courseId)
                i.putExtra("courseName",   student.courseName)
                i.putExtra("yearId",       student.yearId)
                i.putExtra("yearName",     student.yearName)
                i.putExtra("semesterId",   student.semesterId)
                i.putExtra("semesterName", student.semesterName)
                context.startActivity(i)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentExamTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read from intent OR from prefs
        studentDocId = intent.getStringExtra("studentDocId") ?: prefs.getString("studentDocId","") ?: ""
        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        binding.tvHeaderBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  ›  ")

        setupClicks()
        runEntranceAnim()

        // If semesterId empty, load student first
        if (semesterId.isEmpty()) loadStudentThenData()
        else                      loadTimetables()
    }

    // ── Load student if called without params ─────────────────────────────
    private fun loadStudentThenData() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = studentDocId.ifEmpty { prefs.getString("studentDocId","") ?: "" }

        if (docId.isNotEmpty()) {
            db.collection("Students").document(docId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        sessionId    = doc.getString("sessionId")    ?: ""
                        sessionName  = doc.getString("sessionName")  ?: ""
                        courseId     = doc.getString("courseId")     ?: ""
                        courseName   = doc.getString("courseName")   ?: ""
                        yearId       = doc.getString("yearId")       ?: ""
                        yearName     = doc.getString("yearName")     ?: ""
                        semesterId   = doc.getString("semesterId")   ?: ""
                        semesterName = doc.getString("semesterName") ?: ""

                        binding.tvHeaderBadge.text = listOf(courseName, yearName, semesterName)
                            .filter { it.isNotEmpty() }.joinToString("  ›  ")
                        loadTimetables()
                    } else loadByPhone()
                }
                .addOnFailureListener { loadByPhone() }
        } else loadByPhone()
    }

    private fun loadByPhone() {
        val phone = prefs.getString("userPhone","") ?: ""
        if (phone.isEmpty()) { showBothEmpty(); return }

        db.collection("Students").whereEqualTo("mobile", phone).limit(1).get()
            .addOnSuccessListener { q ->
                if (!q.isEmpty) {
                    val doc = q.documents[0]
                    studentDocId = doc.id
                    semesterId   = doc.getString("semesterId") ?: ""
                    sessionId    = doc.getString("sessionId")  ?: ""
                    semesterName = doc.getString("semesterName") ?: ""
                    courseName   = doc.getString("courseName")   ?: ""
                    yearName     = doc.getString("yearName")     ?: ""
                    prefs.edit().putString("studentDocId", doc.id).apply()
                    binding.tvHeaderBadge.text = listOf(courseName, yearName, semesterName)
                        .filter { it.isNotEmpty() }.joinToString("  ›  ")
                    loadTimetables()
                } else showBothEmpty()
            }
            .addOnFailureListener { showBothEmpty() }
    }

    // ── Load exam timetables ──────────────────────────────────────────────
    private fun loadTimetables() {
        binding.progressBar.visibility = View.VISIBLE
        internalDocId = ""; externalDocId = ""

        db.collection("ExamTimetables")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                var foundInternal = false
                var foundExternal = false

                for (doc in docs) {
                    val exam = doc.toObject(ExamTimetable::class.java)
                    if (exam.sessionId != sessionId || !exam.isActive) continue

                    when (exam.examType) {
                        "internal" -> {
                            internalDocId = exam.id
                            showPreview(exam, "internal")
                            foundInternal = true
                        }
                        "external" -> {
                            externalDocId = exam.id
                            showPreview(exam, "external")
                            foundExternal = true
                        }
                    }
                }
                if (!foundInternal) showEmpty("internal")
                if (!foundExternal) showEmpty("external")

                animateContentIn()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showBothEmpty()
            }
    }

    // ── Show file preview card ────────────────────────────────────────────
    private fun showPreview(exam: ExamTimetable, examType: String) {
        if (examType == "internal") {
            binding.layoutInternalEmpty.visibility  = View.GONE
            binding.cardInternalPreview.visibility  = View.VISIBLE

            if (exam.fileType == "image") {
                binding.ivInternalIcon.setImageResource(R.drawable.ic_image_file)
                binding.tvInternalFileType.text = "Image"
                binding.ivInternalThumbnail.visibility = View.VISIBLE
                loadThumbnail(exam.id, binding.ivInternalThumbnail)
            } else {
                binding.ivInternalIcon.setImageResource(R.drawable.ic_pdf)
                binding.tvInternalFileType.text = "PDF"
                binding.ivInternalThumbnail.visibility = View.GONE
            }
            binding.tvInternalFileName.text = exam.fileName.ifEmpty { "Internal Timetable" }

            // Animate card in
            binding.cardInternalPreview.alpha = 0f
            binding.cardInternalPreview.translationY = 20f
            binding.cardInternalPreview.animate()
                .alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(DecelerateInterpolator()).start()

        } else {
            binding.layoutExternalEmpty.visibility  = View.GONE
            binding.cardExternalPreview.visibility  = View.VISIBLE

            if (exam.fileType == "image") {
                binding.ivExternalIcon.setImageResource(R.drawable.ic_image_file)
                binding.tvExternalFileType.text = "Image"
                binding.ivExternalThumbnail.visibility = View.VISIBLE
                loadThumbnail(exam.id, binding.ivExternalThumbnail)
            } else {
                binding.ivExternalIcon.setImageResource(R.drawable.ic_pdf)
                binding.tvExternalFileType.text = "PDF"
                binding.ivExternalThumbnail.visibility = View.GONE
            }
            binding.tvExternalFileName.text = exam.fileName.ifEmpty { "External Timetable" }

            binding.cardExternalPreview.alpha = 0f
            binding.cardExternalPreview.translationY = 20f
            binding.cardExternalPreview.animate()
                .alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun showEmpty(examType: String) {
        if (examType == "internal") {
            binding.layoutInternalEmpty.visibility = View.VISIBLE
            binding.cardInternalPreview.visibility = View.GONE
        } else {
            binding.layoutExternalEmpty.visibility = View.VISIBLE
            binding.cardExternalPreview.visibility = View.GONE
        }
    }

    private fun showBothEmpty() {
        binding.progressBar.visibility = View.GONE
        showEmpty("internal"); showEmpty("external")
    }

    // ── Open file (view only — no edit) ───────────────────────────────────
    private fun openFile(examType: String) {
        val docId = if (examType == "internal") internalDocId else externalDocId
        if (docId.isEmpty()) {
            Toast.makeText(this, "File not available", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("ExamTimetables").document(docId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                val exam = doc.toObject(ExamTimetable::class.java) ?: return@addOnSuccessListener
                if (exam.fileType == "image") openImageFullScreen(docId)
                else openPdf(docId, exam.fileName)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Full screen image viewer ──────────────────────────────────────────
    private fun openImageFullScreen(docId: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.parseColor("#EE000000"))
        )

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(imageView)
        dialog.show()

        // Scale in animation
        imageView.alpha = 0f; imageView.scaleX = 0.85f; imageView.scaleY = 0.85f
        imageView.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(300).setInterpolator(OvershootInterpolator(1.2f)).start()

        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.loadImageFromChunks(
                firestore   = db,
                userId      = docId,
                collection  = "ExamTimetables",
                imageView   = imageView,
                progressBar = null,
                onComplete  = {}
            )
        }
    }

    // ── Open PDF ──────────────────────────────────────────────────────────
    private fun openPdf(docId: String, fileName: String) {
        val loading = AlertDialog.Builder(this)
            .setMessage("Loading PDF...").setCancelable(false).create()
        loading.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val chunksSnap = db.collection("ExamTimetables")
                    .document(docId).collection("photo_chunks")
                    .orderBy("index").get().await()

                val sb = StringBuilder()
                for (doc in chunksSnap.documents.sortedBy { it.getLong("index") ?: 0 }) {
                    sb.append(doc.getString("data") ?: "")
                }

                val pdfBytes = Base64.decode(sb.toString(), Base64.DEFAULT)
                val cacheFile = java.io.File(cacheDir, fileName.ifEmpty { "exam.pdf" })
                cacheFile.writeBytes(pdfBytes)

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@StudentExamTimetableActivity,
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
                        Toast.makeText(this@StudentExamTimetableActivity,
                            "No PDF viewer installed", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading.dismiss()
                    Toast.makeText(this@StudentExamTimetableActivity,
                        "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Load thumbnail ────────────────────────────────────────────────────
    private fun loadThumbnail(docId: String, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            ImageUtil.loadImageFromChunks(
                firestore   = db,
                userId      = docId,
                collection  = "ExamTimetables",
                imageView   = imageView,
                progressBar = null,
                onComplete  = {}
            )
        }
    }

    // ── Tab switch ────────────────────────────────────────────────────────
    private fun switchTab(tab: String) {
        currentTab = tab

        if (tab == "internal") {
            // Internal active
            binding.tabInternal.setTextColor(Color.WHITE)
            binding.tabInternal.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabExternal.setTextColor(Color.parseColor("#AAFFFFFF"))
            binding.tabExternal.setBackgroundResource(android.R.color.transparent)

            animateTabSwitch(binding.layoutInternal, binding.layoutExternal)
        } else {
            // External active
            binding.tabExternal.setTextColor(Color.WHITE)
            binding.tabExternal.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabInternal.setTextColor(Color.parseColor("#AAFFFFFF"))
            binding.tabInternal.setBackgroundResource(android.R.color.transparent)

            animateTabSwitch(binding.layoutExternal, binding.layoutInternal)
        }
    }

    private fun animateTabSwitch(showLayout: View, hideLayout: View) {
        // Hide current
        hideLayout.animate().alpha(0f).translationX(30f).setDuration(180)
            .withEndAction {
                hideLayout.visibility = View.GONE
                hideLayout.translationX = 0f
            }.start()

        // Show new
        showLayout.visibility = View.VISIBLE
        showLayout.alpha = 0f; showLayout.translationX = -30f
        showLayout.animate().alpha(1f).translationX(0f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ── Entrance animations ───────────────────────────────────────────────
    private fun runEntranceAnim() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()
    }

    private fun animateContentIn() {
        // Cards stagger in
        listOf(binding.layoutInternal, binding.layoutExternal)
            .forEachIndexed { i, v ->
                if (v.visibility == View.VISIBLE) {
                    v.alpha = 0f; v.translationY = 30f
                    v.postDelayed({
                        v.animate().alpha(1f).translationY(0f)
                            .setDuration(350).setInterpolator(DecelerateInterpolator()).start()
                    }, i * 100L)
                }
            }
    }

    // ── Setup clicks ──────────────────────────────────────────────────────
    private fun setupClicks() {
        binding.btnBack.setOnClickListener { v ->
            animPress(v) { finish() }
        }
        binding.btnRefresh.setOnClickListener { v ->
            v.animate().rotation(v.rotation + 360f).setDuration(500).start()
            loadTimetables()
        }
        binding.tabInternal.setOnClickListener { v ->
            animPress(v) { switchTab("internal") }
        }
        binding.tabExternal.setOnClickListener { v ->
            animPress(v) { switchTab("external") }
        }
        binding.cardInternalPreview.setOnClickListener { v ->
            animPress(v) { openFile("internal") }
        }
        binding.cardExternalPreview.setOnClickListener { v ->
            animPress(v) { openFile("external") }
        }
    }

    // ── Press animation ───────────────────────────────────────────────────
    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    // ── Coroutine await helper ────────────────────────────────────────────
    private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>.await()
            : com.google.firebase.firestore.QuerySnapshot {
        return suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(it) }
            addOnFailureListener { cont.resumeWithException(it) }
        }
    }
}
