package com.example.academyhub.models

data class Subject(
    var id: String = "",

    // Academic context
    var sessionId: String = "",
    var sessionName: String = "",
    var courseId: String = "",
    var courseName: String = "",
    var yearId: String = "",
    var yearName: String = "",
    var semesterId: String = "",
    var semesterName: String = "",

    // Subject info
    var subjectName: String = "",
    var subjectCode: String = "",

    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}
