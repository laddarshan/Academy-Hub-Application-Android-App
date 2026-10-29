package com.example.academyhub.models

data class ExamTimetable(
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

    // Exam type: "internal" or "external"
    var examType: String = "",

    // File info
    var fileType: String = "",   // "image" or "pdf"
    var fileName: String = "",   // original file name
    var totalChunks: Int = 0,
    var uploadedAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = true
) {
    constructor() : this("")
}
