package com.example.academyhub.models

data class Timetable(
    var id: String = "",

    // Session Info
    var sessionId: String = "",
    var sessionName: String = "",

    // Course Info
    var courseId: String = "",
    var courseName: String = "",

    // ★ NEW: Semester Info
    var semesterId: String = "",
    var semesterName: String = "",    // "Sem-1", "Sem-2" etc

    // Batch Info
    var batchId: String = "",
    var batchName: String = "",       // "Div-A", "Morning Batch" etc

    // Class Details
    var subject: String = "",
    var teacherId: String = "",
    var teacherName: String = "",

    // Schedule
    var day: String = "",             // "Monday", "Tuesday" etc
    var startTime: String = "",       // "09:00 AM"
    var endTime: String = "",         // "10:00 AM"
    var duration: Int = 60,           // in minutes

    // Location & Type
    var classroom: String = "",
    var type: String = "regular",     // regular, online, exam, practical
    var onlineLink: String = "",

    var status: String = "active",
    var notes: String = "",

    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}