package com.example.academyhub.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenHelper {

    private const val TAG = "FcmTokenHelper"

    // Login time par call karo — token save karo WITH ERROR HANDLING
    fun saveToken(onComplete: ((Boolean) -> Unit)? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "User not authenticated")
            onComplete?.invoke(false)
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d(TAG, "Token obtained: ${token.take(20)}...")
                FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(uid)
                    .child("fcmToken")
                    .setValue(token)
                    .addOnSuccessListener {
                        Log.d(TAG, "Token saved successfully")
                        onComplete?.invoke(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to save token: ${e.message}")
                        onComplete?.invoke(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token: ${e.message}")
                onComplete?.invoke(false)
            }
    }

    // Topic subscribe — semester wise notification mate
    fun subscribeToSemester(semesterId: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (semesterId.isEmpty()) {
            Log.w(TAG, "semesterId is empty")
            onComplete?.invoke(false)
            return
        }

        val topic = "semester_$semesterId"
        FirebaseMessaging.getInstance()
            .subscribeToTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Subscribed to topic: $topic")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to subscribe to $topic: ${e.message}")
                onComplete?.invoke(false)
            }
    }

    fun unsubscribeFromSemester(semesterId: String, onComplete: ((Boolean) -> Unit)? = null) {
        if (semesterId.isEmpty()) {
            Log.w(TAG, "semesterId is empty")
            onComplete?.invoke(false)
            return
        }

        val topic = "semester_$semesterId"
        FirebaseMessaging.getInstance()
            .unsubscribeFromTopic(topic)
            .addOnSuccessListener {
                Log.d(TAG, "Unsubscribed from topic: $topic")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to unsubscribe from $topic: ${e.message}")
                onComplete?.invoke(false)
            }
    }

    // All students topic
    fun subscribeToAll(onComplete: ((Boolean) -> Unit)? = null) {
        FirebaseMessaging.getInstance()
            .subscribeToTopic("all_students")
            .addOnSuccessListener {
                Log.d(TAG, "Subscribed to all_students topic")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to subscribe to all_students: ${e.message}")
                onComplete?.invoke(false)
            }
    }
}