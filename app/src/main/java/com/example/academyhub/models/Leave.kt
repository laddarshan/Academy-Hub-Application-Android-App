package com.example.academyhub.models

data class Leave(
    var leaveId: String = "",
    var teacherId: String = "",
    var teacherName: String = "",
    var teacherEmail: String = "",
    var teacherMobile: String = "",
    
    // Leave Details
    var leaveType: String = "casual", // casual, sick, emergency, maternity, paternity
    var fromDate: String = "", // "01/02/2026"
    var toDate: String = "", // "03/02/2026"
    var totalDays: Int = 0,
    var halfDay: Boolean = false,
    
    // Reason & Documents
    var reason: String = "",
    var documentUrl: String = "", // Medical certificate, etc.
    var documentName: String = "",
    
    // Status & Approval
    var status: String = "pending", // pending, approved, rejected, cancelled
    var approvedBy: String = "",
    var approvedByName: String = "",
    var rejectionReason: String = "",
    var adminRemarks: String = "",
    
    // Substitute Teacher
    var substituteTeacherId: String = "",
    var substituteTeacherName: String = "",
    var substituteAssigned: Boolean = false,
    
    // Timestamps
    var appliedAt: Long = System.currentTimeMillis(),
    var approvedAt: Long = 0,
    var rejectedAt: Long = 0,
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
    
    // Helper methods
    fun isPending(): Boolean = status == "pending"
    fun isApproved(): Boolean = status == "approved"
    fun isRejected(): Boolean = status == "rejected"
    fun isCancelled(): Boolean = status == "cancelled"
    
    fun getStatusColor(): String {
        return when (status) {
            "approved" -> "#4CAF50" // Green
            "rejected" -> "#F44336" // Red
            "pending" -> "#FF9800" // Orange
            "cancelled" -> "#9E9E9E" // Gray
            else -> "#2196F3" // Blue
        }
    }
    
    fun getLeaveTypeLabel(): String {
        return when (leaveType) {
            "casual" -> "Casual Leave"
            "sick" -> "Sick Leave"
            "emergency" -> "Emergency Leave"
            "maternity" -> "Maternity Leave"
            "paternity" -> "Paternity Leave"
            else -> leaveType.capitalize()
        }
    }
    
    fun getDateRange(): String {
        return if (fromDate == toDate) {
            fromDate
        } else {
            "$fromDate to $toDate"
        }
    }
    
    fun getDaysLabel(): String {
        return if (halfDay) {
            "Half Day"
        } else if (totalDays == 1) {
            "1 Day"
        } else {
            "$totalDays Days"
        }
    }
}

data class LeaveBalance(
    var teacherId: String = "",
    var teacherName: String = "",
    
    // Leave Allocation (per year)
    var casualLeaveTotal: Int = 12,
    var sickLeaveTotal: Int = 10,
    var emergencyLeaveTotal: Int = 5,
    
    // Leave Used
    var casualLeaveUsed: Int = 0,
    var sickLeaveUsed: Int = 0,
    var emergencyLeaveUsed: Int = 0,
    
    // Leave Remaining
    var casualLeaveRemaining: Int = 12,
    var sickLeaveRemaining: Int = 10,
    var emergencyLeaveRemaining: Int = 5,
    
    // Year
    var year: Int = 0,
    
    // Timestamps
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
    
    fun updateRemainingLeaves() {
        casualLeaveRemaining = casualLeaveTotal - casualLeaveUsed
        sickLeaveRemaining = sickLeaveTotal - sickLeaveUsed
        emergencyLeaveRemaining = emergencyLeaveTotal - emergencyLeaveUsed
    }
    
    fun getTotalLeaveUsed(): Int {
        return casualLeaveUsed + sickLeaveUsed + emergencyLeaveUsed
    }
    
    fun getTotalLeaveRemaining(): Int {
        return casualLeaveRemaining + sickLeaveRemaining + emergencyLeaveRemaining
    }
}
