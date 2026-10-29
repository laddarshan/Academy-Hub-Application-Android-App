package com.example.academyhub.models

import com.google.firebase.firestore.PropertyName

data class Course(
    val id: String = "",
    val courseName: String = "",
    val courseCode: String = "",
    val duration: Int = 0,
    val totalSemesters: Int = 0,
    val description: String = "",
    @get:PropertyName("active")
    @set:PropertyName("active")
    var isActive: Boolean = true,  // ✅ var કર્યું (val ને બદલે)
    val createdAt: Long = 0L,
    val createdBy: String = "",
    var studentCount: Int = 0
) {
    constructor() : this("")
}