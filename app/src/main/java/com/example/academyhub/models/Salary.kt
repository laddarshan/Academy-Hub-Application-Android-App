package com.example.academyhub.models

data class Salary(
    var salaryId: String = "",
    var teacherId: String = "",
    var teacherName: String = "",
    
    // Salary Period
    var month: String = "", // "January 2026"
    var year: Int = 0,
    var monthNumber: Int = 0, // 1-12
    
    // Salary Components
    var basicSalary: Double = 0.0,
    var allowances: Double = 0.0,
    var bonuses: Double = 0.0,
    var deductions: Double = 0.0, // Tax, PF, etc.
    var netSalary: Double = 0.0, // Final amount after deductions
    
    // Payment Details
    var paymentDate: String = "",
    var paymentMethod: String = "bank", // cash, bank, upi, cheque
    var transactionId: String = "",
    var accountNumber: String = "",
    var ifscCode: String = "",
    
    // Status
    var status: String = "pending", // pending, paid, cancelled
    var remarks: String = "",
    var paidBy: String = "", // Admin name
    
    // Timestamps
    var createdAt: Long = System.currentTimeMillis(),
    var paidAt: Long = 0
) {
    constructor() : this("")
    
    // Helper methods
    fun calculateNetSalary(): Double {
        return basicSalary + allowances + bonuses - deductions
    }
    
    fun getFormattedNetSalary(): String {
        return "₹${String.format("%,.2f", netSalary)}"
    }
    
    fun getMonthYear(): String {
        return "$month $year"
    }
    
    fun isPaid(): Boolean = status == "paid"
    fun isPending(): Boolean = status == "pending"
}
