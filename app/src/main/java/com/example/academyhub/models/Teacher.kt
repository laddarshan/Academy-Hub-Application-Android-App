package com.example.academyhub.models

data class Teacher(
    var id: String = "",
    var name: String = "",
    val rating: Double = 0.0,
    var email: String = "",
    var mobile: String = "",
    var gender: String = "",
    var dateOfBirth: String = "",
    var address: String = "",
    
    // Professional Information
    var subject: String = "",
    var qualification: String = "",
    var experience: Int = 0,
    var joiningDate: String = "",
    
    // Assignment Information
    var assignedCourses: List<String> = listOf(),
    var assignedBatches: List<String> = listOf(),
    var assignedSubjects: List<String> = listOf(),
    
    // Salary Information
    var basicSalary: Double = 0.0,
    var allowances: Double = 0.0,
    var currentSalary: Double = 0.0,
    var salaryStatus: String = "pending", // pending, paid
    var lastSalaryDate: String = "",
    var accountNumber: String = "",
    var ifscCode: String = "",
    
    // Performance Metrics
    var totalClasses: Int = 0,
    var classesAttended: Int = 0,
    var attendancePercentage: Double = 0.0,
    var overallRating: Double = 0.0,
    var studentFeedbackCount: Int = 0,
    
    // Leave Information
    var casualLeaveBalance: Int = 12,
    var sickLeaveBalance: Int = 10,
    var casualLeaveUsed: Int = 0,
    var sickLeaveUsed: Int = 0,
    
    // Status & Timestamps
    var status: String = "active", // active, inactive, blocked, on_leave
    var profileImageBase64: String = "", // Will use chunks
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firestore
    constructor() : this("")
    
    // Helper methods
    fun getRemainingCasualLeave(): Int = casualLeaveBalance - casualLeaveUsed
    fun getRemainingSickLeave(): Int = sickLeaveBalance - sickLeaveUsed
    fun getTotalSalary(): Double = basicSalary + allowances
    fun getAttendancePercent(): String = String.format("%.1f%%", attendancePercentage)
    fun getRatingString(): String = String.format("%.1f", overallRating)
}
