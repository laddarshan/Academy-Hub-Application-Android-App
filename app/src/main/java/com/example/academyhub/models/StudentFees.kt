package com.example.academyhub.models

data class StudentFees(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNumber: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val batchName: String = "",
    val totalFees: Double = 0.0,
    val paidAmount: Double = 0.0,
    val pendingAmount: Double = 0.0,
    val feesStatus: String = "pending", // paid, pending, partial
    val admissionDate: String = "",
    val lastPaymentDate: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)