package com.example.academyhub.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.academyhub.databinding.ActivityDocumentsBinding
import com.example.academyhub.databinding.DialogAddDocumentBinding
import com.example.academyhub.models.Document
import com.example.academyhub.teacher.adapters.DocumentAdapter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class DocumentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentsBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var adapter: DocumentAdapter
    private val documentsList = mutableListOf<Document>()
    private var teacherId = ""
    private var selectedFileUri: Uri? = null
    private var selectedFileName = ""
    private var selectedFileSize = 0L
    private var dialogBinding: DialogAddDocumentBinding? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = getFileName(it)
            selectedFileSize = getFileSize(it)
            dialogBinding?.let { db ->
                db.tvSelectedFile.text = selectedFileName
                db.tvSelectedFile.visibility = View.VISIBLE
                db.btnSelectFile.text = "Change File"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        storage   = FirebaseStorage.getInstance()
        teacherId = intent.getStringExtra("teacherId") ?: ""

        if (teacherId.isEmpty()) { finish(); return }

        setupRecyclerView()
        setupClickListeners()
        loadDocuments()
    }

    private fun setupRecyclerView() {
        // એડેપ્ટરમાં 4 પેરામીટર્સ મોકલવા જરૂરી છે
        adapter = DocumentAdapter(
            documents = documentsList,
            onItemClick = { doc -> viewDocument(doc) }, // એડેપ્ટર મુજબ onItemClick વાપર્યું
            onDeleteClick = { doc -> confirmDelete(doc) },
            onVerifyClick = { doc ->
                Toast.makeText(this, "Verify: ${doc.documentName}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDocuments.apply {
            layoutManager = GridLayoutManager(this@DocumentsActivity, 2)
            adapter = this@DocumentsActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddDocument.setOnClickListener { showAddDocumentDialog() }
    }

    private fun loadDocuments() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Teachers").document(teacherId)
            .collection("Documents")
            .orderBy("uploadedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                documentsList.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val document = doc.toObject(Document::class.java)
                        document?.let { documentsList.add(it) }
                    }
                    binding.rvDocuments.visibility   = View.VISIBLE
                    binding.tvNoDocuments.visibility = View.GONE
                } else {
                    binding.rvDocuments.visibility   = View.GONE
                    binding.tvNoDocuments.visibility = View.VISIBLE
                }
                adapter.notifyDataSetChanged()
                // XML માં tvDocumentCount અથવા tvTotalDocuments નામ ચેક કરી લેવું
                binding.tvDocumentCount.text = "${documentsList.size} Documents"
            }
    }

    private fun showAddDocumentDialog() {
        dialogBinding = DialogAddDocumentBinding.inflate(layoutInflater)
        selectedFileUri  = null
        selectedFileName = ""

        val docTypes = arrayOf(
            "Aadhar Card", "PAN Card", "Degree Certificate",
            "Experience Letter", "Appointment Letter", "Payslip",
            "Bank Passbook", "Resume", "Other"
        )
        dialogBinding!!.spinnerDocType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, docTypes)
        )

        dialogBinding!!.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Document")
            .setView(dialogBinding!!.root)
            .setPositiveButton("Upload", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val docType = dialogBinding!!.spinnerDocType.text.toString()
                val docName = dialogBinding!!.etDocumentName.text.toString().trim()

                if (docType.isEmpty()) {
                    Toast.makeText(this, "Please select document type", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (docName.isEmpty()) {
                    dialogBinding!!.etDocumentName.error = "Name is required"
                    return@setOnClickListener
                }
                if (selectedFileUri == null) {
                    Toast.makeText(this, "Please select a file", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialog.dismiss()
                uploadDocument(docType, docName)
            }
        }
        dialog.show()
    }

    private fun uploadDocument(docType: String, docName: String) {
        binding.progressBar.visibility = View.VISIBLE

        val uri = selectedFileUri ?: return
        val docId = UUID.randomUUID().toString() // documentId નો સ્કોપ સુધાર્યો
        val fileExtension = getFileExtension(uri)
        val sPath = "teacher_documents/$teacherId/$docId.$fileExtension" // storagePath એરર સોલ્વ કરી
        val storageRef = storage.reference.child(sPath)

        storageRef.putFile(uri)
            .addOnProgressListener { task ->
                val progress = (100.0 * task.bytesTransferred / task.totalByteCount).toInt()
                // XML માં tvUploadProgress ID હોવું જરૂરી છે
                binding.tvUploadProgress.text = "Uploading... $progress%"
                binding.tvUploadProgress.visibility = View.VISIBLE
            }
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveDocumentToFirestore(
                        documentId = docId,
                        docType = docType,
                        docName = docName,
                        downloadUrl = downloadUri.toString(), // downloadUrl એરર સોલ્વ કરી
                        storagePath = sPath
                    )
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility   = View.GONE
                binding.tvUploadProgress.visibility = View.GONE
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDocumentToFirestore(
        documentId: String, docType: String, docName: String,
        downloadUrl: String, storagePath: String
    ) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val document = Document(
            documentId   = documentId,
            teacherId    = teacherId,
            documentType = docType,
            documentName = docName,
            fileName     = selectedFileName,
            fileSize     = selectedFileSize,
            downloadUrl  = downloadUrl,
            storagePath  = storagePath,
            uploadedAt   = System.currentTimeMillis(),
            uploadDate   = dateFormat.format(Date()),
            uploadedBy   = "Admin",
            isVerified   = false
        )

        firestore.collection("Teachers").document(teacherId)
            .collection("Documents").document(documentId).set(document)
            .addOnSuccessListener {
                firestore.collection("Teachers").document(teacherId)
                    .update("documentCount", com.google.firebase.firestore.FieldValue.increment(1))

                binding.progressBar.visibility    = View.GONE
                binding.tvUploadProgress.visibility = View.GONE
                Toast.makeText(this, "Document added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility    = View.GONE
                binding.tvUploadProgress.visibility = View.GONE
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun viewDocument(document: Document) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(document.downloadUrl))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(document: Document) {
        AlertDialog.Builder(this)
            .setTitle("Delete Document")
            .setMessage("Delete \"${document.documentName}\"?")
            .setPositiveButton("Delete") { _, _ -> deleteDocument(document) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun deleteDocument(document: Document) {
        binding.progressBar.visibility = View.VISIBLE
        if (document.storagePath.isNotEmpty()) {
            storage.reference.child(document.storagePath).delete()
                .addOnCompleteListener { deleteFromFirestore(document) }
        } else {
            deleteFromFirestore(document)
        }
    }

    private fun deleteFromFirestore(document: Document) {
        firestore.collection("Teachers").document(teacherId)
            .collection("Documents").document(document.documentId).delete()
            .addOnSuccessListener {
                firestore.collection("Teachers").document(teacherId)
                    .update("documentCount", com.google.firebase.firestore.FieldValue.increment(-1))
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Document deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFileName(uri: Uri): String {
        var name = "document"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        return name
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index >= 0) size = cursor.getLong(index)
            }
        }
        return size
    }

    private fun getFileExtension(uri: Uri): String {
        return contentResolver.getType(uri)?.let {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        } ?: "pdf"
    }
}