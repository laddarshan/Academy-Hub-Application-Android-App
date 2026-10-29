package com.example.academyhub.models

import com.google.firebase.firestore.PropertyName

data class Semester(
    var id: String = "",
    var courseId: String = "",
    var courseName: String = "",
    var yearId: String = "",
    var yearName: String = "",
    var semesterName: String = "",
    var semesterNumber: Int = 1,
    @get:PropertyName("active")
    @set:PropertyName("active")
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}