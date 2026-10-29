package com.example.academyhub.models

data class FeesStructure(
    var id: String = "",
    var courseId: String = "",
    var courseName: String = "",
    var semesterId: String = "",
    var semesterName: String = "",      // "Sem-1", "Sem-2"

    var totalFees: Double = 0.0,
    var installmentCount: Int = 1,
    var installmentAmount: Double = 0.0,
    var description: String = "",

    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("")
}
