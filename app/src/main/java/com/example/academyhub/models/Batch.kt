package com.example.academyhub.models

import com.google.firebase.firestore.PropertyName

data class Batch(
    var id: String = "",
    var batchName: String = "",
    var startTime: String = "",
    var endTime: String = "",
    var courseId: String = "",
    var batchCode: String = "",
    var description: String = "",
    var courseName: String = "",

    @get:PropertyName("active")
    @set:PropertyName("active")
    var isActive: Boolean = true,

    var createdAt: Long = 0L,
    var updatedAt: Long = 0L,
    var createdBy: String = ""
) {
    constructor() : this("")
}