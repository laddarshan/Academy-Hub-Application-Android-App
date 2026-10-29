package com.example.academyhub.student

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentResultBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class StudentResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentResultBinding
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

    private var currentTab = "internal"

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, StudentResultActivity::class.java).also { i ->
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

        fun start(context: Context) {
            context.startActivity(Intent(context, StudentResultActivity::class.java))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  onCreate
    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        studentDocId = intent.getStringExtra("studentDocId") ?: prefs.getString("studentDocId","") ?: ""
        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""

        setupHeader()
        setupClicks()
        runEntranceAnim()

        if (semesterId.isEmpty()) loadStudentThenData()
        else                      loadResults()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Header badge
    // ─────────────────────────────────────────────────────────────────────
    private fun setupHeader() {
        binding.tvHeaderBadge.text = listOf(courseName, yearName, semesterName)
            .filter { it.isNotEmpty() }.joinToString("  ›  ")
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load student if no params passed
    // ─────────────────────────────────────────────────────────────────────
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
                        setupHeader()
                        loadResults()
                    } else loadByPhone()
                }
                .addOnFailureListener { loadByPhone() }
        } else loadByPhone()
    }

    private fun loadByPhone() {
        val phone = prefs.getString("userPhone","") ?: ""
        if (phone.isEmpty()) { doneLoading(); showBothEmpty(); return }

        db.collection("Students").whereEqualTo("mobile", phone).limit(1).get()
            .addOnSuccessListener { q ->
                val doc = q.documents.firstOrNull()
                if (doc != null) {
                    studentDocId = doc.id
                    sessionId    = doc.getString("sessionId")    ?: ""
                    courseId     = doc.getString("courseId")     ?: ""
                    semesterId   = doc.getString("semesterId")   ?: ""
                    semesterName = doc.getString("semesterName") ?: ""
                    courseName   = doc.getString("courseName")   ?: ""
                    yearName     = doc.getString("yearName")     ?: ""
                    prefs.edit().putString("studentDocId", doc.id).apply()
                    setupHeader()
                    loadResults()
                } else { doneLoading(); showBothEmpty() }
            }
            .addOnFailureListener { doneLoading(); showBothEmpty() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load results  — doc: "{semesterId}_{sessionId}"
    // ─────────────────────────────────────────────────────────────────────
    private fun loadResults() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = "${semesterId}_${sessionId}"

        db.collection("Results").document(docId).get()
            .addOnSuccessListener { doc ->
                doneLoading()

                if (!doc.exists()) { showBothEmpty(); animateContent(); return@addOnSuccessListener }

                // ── Internal ──
                val internalUploaded  = doc.getBoolean("internalUploaded")    ?: false
                val internalFileName  = doc.getString("internalFileName")     ?: ""
                val internalUploadedAt = doc.getLong("internalUploadedAt")    ?: 0L

                if (internalUploaded && internalFileName.isNotEmpty()) {
                    showInternalResult(internalFileName, internalUploadedAt)
                } else showInternalEmpty()

                // ── External ──
                val externalSent = doc.getBoolean("externalNotificationSent") ?: false
                val externalAt   = doc.getLong("externalNotificationAt")       ?: 0L

                if (externalSent && externalAt > 0) showExternalAvailable(externalAt)
                else showExternalPending()

                animateContent()
            }
            .addOnFailureListener { e ->
                doneLoading()
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showBothEmpty()
            }
    }

    private fun doneLoading() { binding.progressBar.visibility = View.GONE }

    // ─────────────────────────────────────────────────────────────────────
    //  Internal UI states
    // ─────────────────────────────────────────────────────────────────────
    private fun showInternalResult(fileName: String, uploadedAt: Long) {
        binding.layoutInternalEmpty.visibility = View.GONE
        binding.cardInternalResult.visibility  = View.VISIBLE

        binding.tvInternalFileName.text = fileName

        val dateStr = if (uploadedAt > 0)
            "Upload: " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(uploadedAt))
        else ""
        binding.tvInternalUploadDate.text = dateStr

        // NEW badge — 7 days
        val isNew = uploadedAt > 0 &&
                (System.currentTimeMillis() - uploadedAt) < 7 * 24 * 60 * 60 * 1000L
        binding.tvInternalNewBadge.visibility = if (isNew) View.VISIBLE else View.GONE

        // PDF icon
        binding.ivInternalIcon.setImageResource(R.drawable.ic_pdf)
    }

    private fun showInternalEmpty() {
        binding.layoutInternalEmpty.visibility = View.VISIBLE
        binding.cardInternalResult.visibility  = View.GONE
        binding.tvInternalNewBadge.visibility  = View.GONE
    }

    // ─────────────────────────────────────────────────────────────────────
    //  External UI states
    // ─────────────────────────────────────────────────────────────────────
    private fun showExternalAvailable(declaredAt: Long) {
        binding.layoutExternalPending.visibility = View.GONE
        binding.cardExternalResult.visibility    = View.VISIBLE

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(declaredAt))
        binding.tvExternalDeclaredAt.text = "Declared: $dateStr"
    }

    private fun showExternalPending() {
        binding.layoutExternalPending.visibility = View.VISIBLE
        binding.cardExternalResult.visibility    = View.GONE
    }

    private fun showBothEmpty() {
        showInternalEmpty()
        showExternalPending()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Tab switch  — same as exam timetable pattern
    // ─────────────────────────────────────────────────────────────────────
    private fun switchTab(tab: String) {
        currentTab = tab

        if (tab == "internal") {
            // Internal active
            binding.tabInternal.setTextColor(Color.parseColor("#075E54"))
            binding.tabInternal.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabExternal.setTextColor(Color.parseColor("#AAFFFFFF"))
            binding.tabExternal.setBackgroundResource(android.R.color.transparent)
            slideTab(show = binding.layoutInternal, hide = binding.layoutExternal)
        } else {
            // External active
            binding.tabExternal.setTextColor(Color.parseColor("#075E54"))
            binding.tabExternal.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.tabInternal.setTextColor(Color.parseColor("#AAFFFFFF"))
            binding.tabInternal.setBackgroundResource(android.R.color.transparent)
            slideTab(show = binding.layoutExternal, hide = binding.layoutInternal)
        }
    }

    private fun slideTab(show: View, hide: View) {
        hide.animate().alpha(0f).translationX(30f).setDuration(160).withEndAction {
            hide.visibility = View.GONE
            hide.translationX = 0f
        }.start()

        show.visibility = View.VISIBLE
        show.alpha = 0f
        show.translationX = -30f
        show.animate().alpha(1f).translationX(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Open internal PDF — same chunk logic as admin
    // ─────────────────────────────────────────────────────────────────────
    private fun openInternalPdf() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = "${semesterId}_${sessionId}"

        // 1. Firestore માંથી ફાઇલનો પાથ (Path) મેળવો
        db.collection("Results").document(docId).get()
            .addOnSuccessListener { doc ->
                val filePath = doc.getString("internalFilePath") ?: ""
                val fileName = doc.getString("internalFileName") ?: "result.pdf"

                if (filePath.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "PDF available nathi", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 2. Firebase Storage માંથી ફાઇલ ડાઉનલોડ કરો
                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child(filePath)
                val localFile = File(cacheDir, fileName)

                storageRef.getFile(localFile)
                    .addOnSuccessListener {
                        // 3. ફાઇલ સફળતાપૂર્વક ડાઉનલોડ થયા પછી તેને ઓપન કરો
                        binding.progressBar.visibility = View.GONE
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${packageName}.provider",
                            localFile
                        )

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "PDF viewer app install karo", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Open university website
    // ─────────────────────────────────────────────────────────────────────
    private fun openExternalResult() {
        // Tamari university nu actual URL ahi set karo
        // Default: Google search for result
        val query = Uri.encode("$courseName $semesterName external result $sessionName")
        val url   = "https://www.google.com/search?q=$query"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Browser open nahi thayo", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Animations
    // ─────────────────────────────────────────────────────────────────────
    private fun runEntranceAnim() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()
    }

    private fun animateContent() {
        val current = if (currentTab == "internal") binding.layoutInternal
                      else binding.layoutExternal
        current.alpha = 0f; current.translationY = 20f
        current.animate().alpha(1f).translationY(0f)
            .setDuration(340).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Setup clicks
    // ─────────────────────────────────────────────────────────────────────
    private fun setupClicks() {
        binding.btnBack.setOnClickListener    { v -> animPress(v) { finish() } }

        binding.btnRefresh.setOnClickListener { v ->
            v.animate().rotation(v.rotation + 360f).setDuration(500).start()
            if (semesterId.isEmpty()) loadStudentThenData() else loadResults()
        }

        binding.tabInternal.setOnClickListener { v ->
            if (currentTab != "internal") animPress(v) { switchTab("internal") }
        }
        binding.tabExternal.setOnClickListener { v ->
            if (currentTab != "external") animPress(v) { switchTab("external") }
        }

        // Internal PDF — tap card to open
        binding.cardInternalResult.setOnClickListener { v ->
            animPress(v) { openInternalPdf() }
        }

        // External — university website
        binding.btnViewExternalResult.setOnClickListener { v ->
            animPress(v) { openExternalResult() }
        }
    }
}
