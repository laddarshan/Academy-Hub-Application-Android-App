package com.example.academyhub.models

data class Student(
    var id: String = "",
    var name: String = "",
    var email: String = "",
    var mobile: String = "",
    var gender: String = "",
    var dateOfBirth: String = "",
    var address: String = "",

    // Academic Session Info
    var sessionId: String = "",
    var sessionName: String = "",

    // Course Info
    var courseId: String = "",
    var courseName: String = "",

    // Year Info
    var yearId: String = "",
    var yearName: String = "",
    var yearNumber: Int = 1,

    // Semester Info
    var semesterId: String = "",
    var semesterName: String = "",
    var semesterNumber: Int = 1,

    // Batch Info
    var batchId: String = "",
    var batchName: String = "",

    // Other Academic Info
    var rollNumber: String = "",
    var admissionDate: String = "",
    var status: String = "active", // active, inactive, blocked, passout

    // Profile Image Logic (આ સુધારો સૌથી મહત્વનો છે)
    var hasPhoto: Boolean = false, // ફોટો છે કે નહીં તે ચેક કરવા માટે
    var profileImageBase64: String = "", // આ હંમેશા ખાલી રહેશે કારણ કે આપણે Chunks વાપરીએ છીએ

    // Performance Metrics
    var attendancePercentage: Double = 0.0,
    var feesStatus: String = "pending",
    var totalFees: Double = 0.0,
    var paidFees: Double = 0.0,

    // Promotion History
    var promotionHistory: List<PromotionRecord> = listOf(),

    // Timestamps
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    // Firebase માટે ખાલી કન્સ્ટ્રક્ટર
    constructor() : this("")
}