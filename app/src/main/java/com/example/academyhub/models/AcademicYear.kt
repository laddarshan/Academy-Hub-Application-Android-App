package com.example.academyhub.models

import com.google.firebase.firestore.PropertyName

data class AcademicYear(
    var id: String = "",
    var courseId: String = "",
    var courseName: String = "",
    var yearName: String = "",
    var yearNumber: Int = 1,
    var yearFullName: String = "",
    @get:PropertyName("active")
    @set:PropertyName("active")
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}