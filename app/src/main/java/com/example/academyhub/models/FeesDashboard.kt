package com.example.academyhub.models

data class FeesDashboard(
    val totalFeesCollected: Double = 0.0,
    val totalPendingFees: Double = 0.0,
    val monthlyCollection: Double = 0.0,
    val todayCollection: Double = 0.0,
    val totalStudents: Int = 0,
    val paidStudents: Int = 0,
    val pendingStudents: Int = 0,
    val partialStudents: Int = 0
)