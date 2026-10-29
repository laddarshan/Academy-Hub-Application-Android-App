package com.example.academyhub.models

data class PromotionRecord(
    var fromSemesterId: String = "",
    var fromSemesterName: String = "",
    var toSemesterId: String = "",
    var toSemesterName: String = "",
    var promotedAt: Long = System.currentTimeMillis(),
    var promotedBy: String = ""
)
