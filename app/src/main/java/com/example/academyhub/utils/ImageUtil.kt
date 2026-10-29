package com.example.academyhub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import android.widget.ProgressBar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtil {

    private const val CHUNK_SIZE = 400 * 1024 // 400 KB per chunk (reduced from 800KB)
    private const val MAX_IMAGE_SIZE = 1024 // Maximum width/height for compression

    /**
     * Convert image URI to Base64 WITH compression (Optimized Quality)
     * Reduces file size significantly while maintaining good quality
     */
    fun convertImageToBase64Optimized(context: Context, imageUri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Resize if image is too large
            if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
                val ratio = Math.min(
                    MAX_IMAGE_SIZE.toFloat() / bitmap.width,
                    MAX_IMAGE_SIZE.toFloat() / bitmap.height
                )
                val width = (ratio * bitmap.width).toInt()
                val height = (ratio * bitmap.height).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
            }

            // Compress to JPEG with 80% quality (good balance)
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()

            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Split Base64 string into smaller chunks (400KB)
     */
    fun splitIntoChunks(base64String: String): List<String> {
        val chunks = mutableListOf<String>()
        var startIndex = 0

        while (startIndex < base64String.length) {
            val endIndex = minOf(startIndex + CHUNK_SIZE, base64String.length)
            chunks.add(base64String.substring(startIndex, endIndex))
            startIndex = endIndex
        }

        return chunks
    }

    /**
     * Upload photo chunks with better error handling and progress
     */
    suspend fun uploadPhotoChunks(
        firestore: FirebaseFirestore,
        userId: String,
        collection: String,
        base64String: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            deletePhotoChunks(firestore, userId, collection)

            val chunks = splitIntoChunks(base64String)
            val totalChunks = chunks.size

            chunks.forEachIndexed { index, chunk ->
                val chunkData = hashMapOf(
                    "index" to index,
                    "data" to chunk,
                    "totalChunks" to totalChunks,
                    "timestamp" to System.currentTimeMillis()
                )

                firestore.collection(collection)
                    .document(userId)
                    .collection("photo_chunks")
                    .document("chunk_$index")
                    .set(chunkData)
                    .await()

                val progress = ((index + 1) * 100) / totalChunks
                onProgress(progress)
            }

            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure(e)
        }
    }

    /**
     * Download and join photo chunks
     */
    suspend fun downloadPhotoChunks(
        firestore: FirebaseFirestore,
        userId: String,
        collection: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val chunks = firestore.collection(collection)
                .document(userId)
                .collection("photo_chunks")
                .get()
                .await()

            if (chunks.isEmpty) {
                onFailure(Exception("No photo chunks found"))
                return
            }

            val sortedChunks = chunks.documents
                .sortedBy { it.getLong("index") ?: 0 }

            val totalChunks = sortedChunks.size
            val fullBase64 = StringBuilder()

            sortedChunks.forEachIndexed { index, document ->
                val chunkData = document.getString("data") ?: ""
                fullBase64.append(chunkData)

                val progress = ((index + 1) * 100) / totalChunks
                onProgress(progress)
            }

            onSuccess(fullBase64.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure(e)
        }
    }

    /**
     * Delete all photo chunks
     */
    private suspend fun deletePhotoChunks(
        firestore: FirebaseFirestore,
        userId: String,
        collection: String
    ) {
        try {
            val chunks = firestore.collection(collection)
                .document(userId)
                .collection("photo_chunks")
                .get()
                .await()

            chunks.documents.forEach {
                it.reference.delete().await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Convert Base64 to Bitmap (Memory optimized)
     */
    fun base64ToBitmap(base64String: String?): Bitmap? {
        return try {
            if (base64String.isNullOrEmpty()) return null

            val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = 1
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            System.gc()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load Base64 image into ImageView
     */
    fun loadBase64Image(imageView: ImageView, base64String: String?) {
        val bitmap = base64ToBitmap(base64String)
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    /**
     * Load image from Firestore chunks with progress
     */
    suspend fun loadImageFromChunks(
        firestore: FirebaseFirestore,
        userId: String,
        collection: String,
        imageView: ImageView,
        progressBar: ProgressBar?,
        onComplete: (Boolean) -> Unit   // ✅ CHANGED
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            progressBar?.visibility = android.view.View.VISIBLE
        }

        downloadPhotoChunks(
            firestore = firestore,
            userId = userId,
            collection = collection,
            onProgress = { progress ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    progressBar?.progress = progress
                }
            },
            onSuccess = { base64String ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    loadBase64Image(imageView, base64String)
                    progressBar?.visibility = android.view.View.GONE
                    onComplete(true)   // ✅ SUCCESS
                }
            },
            onFailure = { e ->
                e.printStackTrace()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    progressBar?.visibility = android.view.View.GONE
                    onComplete(false)  // ✅ FAILURE
                }
            }
        )
    }
}