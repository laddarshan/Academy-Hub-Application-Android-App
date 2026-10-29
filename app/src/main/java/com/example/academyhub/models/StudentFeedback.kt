package com.example.academyhub.models

data class StudentFeedback(
    var studentId: String = "",
    var studentName: String = "",
    var rating: Int = 5, // 1-5 stars
    var comment: String = "",
    var submittedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}