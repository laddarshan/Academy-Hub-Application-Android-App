package com.example.academyhub.announcements

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.academyhub.databinding.ActivityAnnouncementPdfBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File

class AnnouncementPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnnouncementPdfBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val announcementId = intent.getStringExtra("announcementId") ?: run { finish(); return }
        val pdfFileName    = intent.getStringExtra("pdfFileName") ?: "announcement.pdf"
        val chunkCount     = intent.getIntExtra("chunkCount", 0)

        binding.tvLoading.text = "Loading PDF..."
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val chunks = mutableMapOf<Int, String>()
                for (i in 0 until chunkCount) {
                    val doc = db.collection("announcements")
                        .document(announcementId)
                        .collection("pdfChunks")
                        .document("chunk_$i")
                        .get().await()
                    chunks[i] = doc.getString("data") ?: ""
                }
                val fullB64  = (0 until chunkCount).joinToString("") { chunks[it] ?: "" }
                val pdfBytes = Base64.decode(fullB64, Base64.NO_WRAP)
                val file     = File(cacheDir, pdfFileName)
                file.writeBytes(pdfBytes)

                val uri = FileProvider.getUriForFile(
                    this@AnnouncementPdfActivity, "${packageName}.provider", file)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivity(viewIntent) }
                    catch (e: Exception) {
                        Toast.makeText(this@AnnouncementPdfActivity,
                            "Please install a PDF viewer app", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@AnnouncementPdfActivity,
                        "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}