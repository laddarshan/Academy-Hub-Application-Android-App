package com.example.academyhub.result

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityResultManagementBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ResultManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultManagementBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""

    // PDF picker — only PDF
    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { uploadInternalResultPdf(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth      = FirebaseAuth.getInstance()
        storage   = FirebaseStorage.getInstance()

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
        loadExistingResults()
    }

    // ─────────────────────────────────────────
    //  SwipeRefresh — teal colors
    // ─────────────────────────────────────────
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener {
            loadExistingResults(fromSwipe = true)
        }
    }

    // ─────────────────────────────────────────
    //  Click Listeners
    // ─────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        binding.btnUploadInternal.setOnClickListener {
            animateButtonPulse(it)
            it.postDelayed({ showPdfPickerBottomSheet() }, 120)
        }

        binding.btnViewInternal.setOnClickListener {
            animateCardTap(it)
            it.postDelayed({ viewInternalPdf() }, 120)
        }

        binding.btnDeleteInternal.setOnClickListener {
            confirmDeleteInternal()
        }

        binding.btnSendExternal.setOnClickListener {
            animateButtonPulse(it)
            it.postDelayed({ confirmSendExternal() }, 120)
        }

        binding.btnCancelExternal.setOnClickListener {
            animateCardTap(it)
            it.postDelayed({ confirmCancelExternal() }, 120)
        }
    }

    // ─────────────────────────────────────────
    //  Bottom Sheet — PDF only picker
    // ─────────────────────────────────────────
    private fun showPdfPickerBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view   = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_pdf_picker, null)

        view.findViewById<View>(R.id.btnPickPdf).setOnClickListener {
            animateCardTap(it)
            it.postDelayed({
                dialog.dismiss()
                pdfPicker.launch("application/pdf")
            }, 120)
        }

        dialog.setContentView(view)
        dialog.show()

        view.post {
            val btn = view.findViewById<View>(R.id.btnPickPdf)
            btn.alpha        = 0f
            btn.translationX = -24f
            btn.animate()
                .alpha(1f).translationX(0f)
                .setDuration(220).setStartDelay(60)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ─────────────────────────────────────────
    //  Load existing results
    // ─────────────────────────────────────────
    private fun loadExistingResults(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBarTop.visibility = View.VISIBLE

        val docId = "${semesterId}_${sessionId}"
        firestore.collection("Results").document(docId).get()
            .addOnSuccessListener { doc ->
                binding.progressBarTop.visibility = View.GONE
                binding.swipeRefresh.isRefreshing  = false

                if (!doc.exists()) {
                    showInternalEmpty()
                    showExternalNotSent()
                    animateContentIn()
                    return@addOnSuccessListener
                }

                val internalUploaded = doc.getBoolean("internalUploaded") ?: false
                val internalFileName = doc.getString("internalFileName")  ?: ""
                if (internalUploaded) showInternalUploaded(internalFileName)
                else showInternalEmpty()

                val externalSent = doc.getBoolean("externalNotificationSent") ?: false
                val externalAt   = doc.getLong("externalNotificationAt")       ?: 0L
                if (externalSent && externalAt > 0) {
                    val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(Date(externalAt))
                    showExternalSent(time)
                } else {
                    showExternalNotSent()
                }

                animateContentIn()
            }
            .addOnFailureListener {
                binding.progressBarTop.visibility = View.GONE
                binding.swipeRefresh.isRefreshing  = false
                animateContentIn()
            }
    }

    // ─────────────────────────────────────────
    //  Internal UI states
    // ─────────────────────────────────────────
    private fun showInternalUploaded(fileName: String) {
        binding.layoutInternalEmpty.visibility    = View.GONE
        binding.layoutInternalUploaded.visibility = View.VISIBLE
        binding.btnDeleteInternal.visibility      = View.VISIBLE
        binding.tvUploadInternalLabel.text        = "Replace"
        binding.tvInternalFileName.text           = fileName

        binding.layoutInternalUploaded.alpha        = 0f
        binding.layoutInternalUploaded.translationY = 16f
        binding.layoutInternalUploaded.animate()
            .alpha(1f).translationY(0f)
            .setDuration(280).setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showInternalEmpty() {
        binding.layoutInternalEmpty.visibility    = View.VISIBLE
        binding.layoutInternalUploaded.visibility = View.GONE
        binding.btnDeleteInternal.visibility      = View.GONE
        binding.tvUploadInternalLabel.text        = "Upload"
    }

    // ─────────────────────────────────────────
    //  External UI states
    // ─────────────────────────────────────────
    private fun showExternalSent(time: String) {
        binding.tvExternalStatus.text      = "Sent on $time"
        binding.tvExternalStatus.setTextColor(Color.parseColor("#2E7D32"))
        binding.layoutExternalStatusBadge.background =
            getDrawable(R.drawable.bg_sent_badge)
        binding.tvSendExternalLabel.text   = "Resend Notification"
        binding.btnCancelExternal.visibility = View.VISIBLE
    }

    private fun showExternalNotSent() {
        binding.tvExternalStatus.text      = "Not sent yet"
        binding.tvExternalStatus.setTextColor(Color.parseColor("#E65100"))
        binding.layoutExternalStatusBadge.background =
            getDrawable(R.drawable.bg_not_sent_badge)
        binding.tvSendExternalLabel.text   = "Send Notification to Students"
        binding.btnCancelExternal.visibility = View.GONE
    }

    // ─────────────────────────────────────────
    //  ✅ FIX 1: Upload PDF — Firebase Storage direct
    // ─────────────────────────────────────────
    private fun uploadInternalResultPdf(uri: Uri) {
        binding.progressBarInternal.visibility = View.VISIBLE

        val fileName   = getFileName(uri)
        // પાથ બનાવતી વખતે ધ્યાન રાખવું કે તે યુનિક હોય
        val filePath   = "results/${semesterId}/${sessionId}/${fileName}"
        val storageRef = storage.reference.child(filePath)
        val docId      = "${semesterId}_${sessionId}"

        storageRef.putFile(uri)
            .addOnSuccessListener {
                // Firestore માં સેવ કરવા માટેનો ડેટા મેપ
                val resultData = hashMapOf(
                    "semesterId"         to semesterId,
                    "semesterName"       to semesterName,
                    "sessionId"          to sessionId,
                    "sessionName"        to sessionName,
                    "courseId"           to courseId,
                    "courseName"         to courseName,
                    "yearId"             to yearId,
                    "yearName"           to yearName,
                    "internalUploaded"   to true,
                    "internalFileName"   to fileName,
                    "internalFilePath"   to filePath, // આ સૌથી મહત્વનું છે
                    "internalUploadedAt" to System.currentTimeMillis()
                )

                // Firestore માં Results કલેક્શનમાં ડેટા સેવ કરવો
                firestore.collection("Results").document(docId)
                    .set(resultData, SetOptions.merge()) // merge વાપરવાથી બીજો ડેટા (જેમ કે external) ડિલીટ નહીં થાય
                    .addOnSuccessListener {
                        binding.progressBarInternal.visibility = View.GONE
                        showInternalUploaded(fileName)
                        Toast.makeText(this, "Success: Firestore updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBarInternal.visibility = View.GONE
                        Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBarInternal.visibility = View.GONE
                Toast.makeText(this, "Storage Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────────────────────
    //  ✅ FIX 2: View PDF — Storage thi download karke open
    // ─────────────────────────────────────────
    private fun viewInternalPdf() {
        binding.progressBarInternal.visibility = View.VISIBLE
        val docId = "${semesterId}_${sessionId}"

        firestore.collection("Results").document(docId).get()
            .addOnSuccessListener { doc ->
                val filePath = doc.getString("internalFilePath") ?: ""
                val fileName = doc.getString("internalFileName") ?: "result.pdf"

                if (filePath.isEmpty()) {
                    binding.progressBarInternal.visibility = View.GONE
                    Toast.makeText(this, "PDF not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val localFile = File(cacheDir, fileName)
                storage.reference.child(filePath).getFile(localFile)
                    .addOnSuccessListener {
                        binding.progressBarInternal.visibility = View.GONE
                        val fileUri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.provider",
                            localFile
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { startActivity(intent) }
                        catch (e: Exception) {
                            Toast.makeText(this, "Please install a PDF viewer app", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        binding.progressBarInternal.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                binding.progressBarInternal.visibility = View.GONE
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────────────────────
    //  ✅ FIX 3: Delete — Storage + Firestore dono clean
    // ─────────────────────────────────────────
    private fun confirmDeleteInternal() {
        AlertDialog.Builder(this)
            .setTitle("Delete Internal Result")
            .setMessage("Are you sure you want to delete this PDF?")
            .setPositiveButton("Delete") { _, _ -> deleteInternalResult() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteInternalResult() {
        binding.progressBarTop.visibility = View.VISIBLE
        val docId = "${semesterId}_${sessionId}"

        firestore.collection("Results").document(docId).get()
            .addOnSuccessListener { doc ->
                val filePath = doc.getString("internalFilePath") ?: ""

                fun updateFirestore() {
                    firestore.collection("Results").document(docId).update(
                        mapOf(
                            "internalUploaded" to false,
                            "internalFileName" to "",
                            "internalFilePath" to ""
                        )
                    ).addOnSuccessListener {
                        binding.progressBarTop.visibility = View.GONE
                        showInternalEmpty()
                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener {
                        binding.progressBarTop.visibility = View.GONE
                        Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                if (filePath.isNotEmpty()) {
                    storage.reference.child(filePath).delete()
                        .addOnSuccessListener { updateFirestore() }
                        .addOnFailureListener { updateFirestore() }
                } else {
                    updateFirestore()
                }
            }
            .addOnFailureListener {
                binding.progressBarTop.visibility = View.GONE
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────────────────────
    //  Send External Notification
    //  (No FCM push — saves message to Firestore only, student app reads it)
    // ─────────────────────────────────────────
    private fun confirmSendExternal() {
        AlertDialog.Builder(this)
            .setTitle("Send Notification")
            .setMessage(
                "$courseName - $yearName - $semesterName students will see:\n\n" +
                        "\"Your external result is now available. Please check online.\"\n\n" +
                        "Proceed?"
            )
            .setPositiveButton("Send") { _, _ -> sendExternalNotification() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendExternalNotification() {
        binding.progressBarExternal.visibility = View.VISIBLE
        binding.btnSendExternal.isEnabled      = false
        binding.btnSendExternal.alpha          = 0.5f

        val now     = System.currentTimeMillis()
        val docId   = "${semesterId}_${sessionId}"
        val title   = "External Result Available"
        val message = "Your external result is now available. Please check online."

        // Step 1: Save status in Results collection
        firestore.collection("Results").document(docId).set(
            hashMapOf(
                "externalNotificationSent" to true,
                "externalNotificationAt"   to now,
                "semesterId"               to semesterId,
                "semesterName"             to semesterName,
                "sessionId"                to sessionId,
                "courseId"                 to courseId,
                "courseName"               to courseName,
                "yearId"                   to yearId,
                "yearName"                 to yearName
            ),
            SetOptions.merge()
        ).addOnSuccessListener {

            // Step 2: Save message in Notifications collection
            // Student app reads this collection to show in-app messages
            firestore.collection("Notifications").add(
                hashMapOf(
                    "title"       to title,
                    "body"        to message,
                    "type"        to "external_result",
                    "semesterId"  to semesterId,
                    "sessionId"   to sessionId,
                    "courseId"    to courseId,
                    "courseName"  to courseName,
                    "yearName"    to yearName,
                    "semesterName" to semesterName,
                    "createdAt"   to now,
                    "isActive"    to true
                )
            ).addOnSuccessListener {
                binding.progressBarExternal.visibility = View.GONE
                binding.btnSendExternal.isEnabled      = true
                binding.btnSendExternal.alpha          = 1f
                val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(now))
                showExternalSent(time)
                Toast.makeText(this, "Message saved! Students will see it in the app.", Toast.LENGTH_LONG).show()
            }.addOnFailureListener { e ->
                binding.progressBarExternal.visibility = View.GONE
                binding.btnSendExternal.isEnabled      = true
                binding.btnSendExternal.alpha          = 1f
                Toast.makeText(this, "Error saving notification: ${e.message}", Toast.LENGTH_LONG).show()
            }

        }.addOnFailureListener { e ->
            binding.progressBarExternal.visibility = View.GONE
            binding.btnSendExternal.isEnabled      = true
            binding.btnSendExternal.alpha          = 1f
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────
    //  Cancel External Notification
    // ─────────────────────────────────────────
    private fun confirmCancelExternal() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Notification")
            .setMessage("Cancel this notification? You can send it again to students later.")
            .setPositiveButton("Cancel Notification") { _, _ -> cancelExternalNotification() }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun cancelExternalNotification() {
        binding.progressBarTop.visibility = View.VISIBLE
        val docId = "${semesterId}_${sessionId}"

        firestore.collection("Results").document(docId).update(
            mapOf(
                "externalNotificationSent" to false,
                "externalNotificationAt"   to 0L
            )
        ).addOnSuccessListener {
            binding.progressBarTop.visibility = View.GONE
            showExternalNotSent()
            Toast.makeText(this, "Notification cancelled!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            binding.progressBarTop.visibility = View.GONE
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────
    //  Animations
    // ─────────────────────────────────────────
    private fun animateContentIn() {
        binding.layoutContent.visibility = View.VISIBLE
        if (binding.layoutContent.alpha == 1f) return

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutContent, "alpha", 0f, 1f)
                    .apply { duration = 350 },
                ObjectAnimator.ofFloat(binding.layoutContent, "translationY", 40f, 0f)
                    .apply { duration = 380; interpolator = DecelerateInterpolator() }
            )
            start()
        }

        val cards = listOf(binding.cardInternal, binding.cardExternal)
        cards.forEachIndexed { i, card ->
            card.alpha        = 0f
            card.translationY = 30f
            card.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300).setStartDelay(i * 80L)
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
    //  Helper
    // ─────────────────────────────────────────
    private fun getFileName(uri: Uri): String {
        var name = "internal_result.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }
}