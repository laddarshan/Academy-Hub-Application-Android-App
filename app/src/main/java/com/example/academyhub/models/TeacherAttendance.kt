package com.example.academyhub.models

data class TeacherAttendance(
    var attendanceId: String = "",
    var teacherId: String = "",
    var teacherName: String = "",
    
    // Date Information
    var date: String = "", // "30/01/2026"
    var day: String = "", // "Monday"
    var month: String = "", // "January"
    var year: Int = 0,
    
    // Time Information
    var checkInTime: String = "",
    var checkOutTime: String = "",
    var totalHours: Double = 0.0,
    var workingHours: Double = 8.0, // Expected working hours
    
    // Attendance Status
    var status: String = "present", // present, absent, late, half_day, on_leave
    var leaveType: String = "", // If on_leave: casual, sick, emergency
    var isLate: Boolean = false,
    var lateMinutes: Int = 0,
    
    // Additional Information
    var remarks: String = "",
    var location: String = "", // GPS location (optional)
    var markedBy: String = "Self", // Self, Admin
    var approvedBy: String = "",
    
    // Timestamps
    var timestamp: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
    
    // Helper methods
    fun isPresent(): Boolean = status == "present"
    fun isAbsent(): Boolean = status == "absent"
    fun isOnLeave(): Boolean = status == "on_leave"
    fun isHalfDay(): Boolean = status == "half_day"
    
    fun getStatusColor(): String {
        return when (status) {
            "present" -> "#4CAF50" // Green
            "absent" -> "#F44336" // Red
            "late" -> "#FF9800" // Orange
            "half_day" -> "#FFC107" // Yellow
            "on_leave" -> "#2196F3" // Blue
            else -> "#9E9E9E" // Gray
        }
    }
    
    fun getFormattedHours(): String {
        return String.format("%.1f hrs", totalHours)
    }
}
