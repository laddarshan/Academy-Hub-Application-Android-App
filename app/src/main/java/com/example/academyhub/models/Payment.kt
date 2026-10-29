package com.example.academyhub.models

data class Payment(
    var id: String = "",
    var studentId: String = "",
    var studentName: String = "",
    var rollNumber: String = "",

    var courseId: String = "",
    var courseName: String = "",
    var semesterId: String = "",
    var semesterName: String = "",

    var amount: Double = 0.0,
    var paymentDate: String = "",
    var paymentMode: String = "cash",   // Cash, Cheque, Bank Transfer, UPI
    var transactionId: String = "",
    var receiptNumber: String = "",
    var remark: String = "",

    var status: String = "completed",
    var createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}