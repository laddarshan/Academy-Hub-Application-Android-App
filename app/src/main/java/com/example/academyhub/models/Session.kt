package com.example.academyhub.models

import com.google.firebase.firestore.PropertyName

data class Session(
    var id: String = "",
    var sessionName: String = "",
    var startYear: String = "",
    var endYear: String = "",
    var startDate: String = "",
    var endDate: String = "",

    // Firebase ma field name "current" chhe
    @get:PropertyName("current")
    @set:PropertyName("current")
    var isCurrent: Boolean = false,

    // Firebase ma field name "active" chhe
    @get:PropertyName("active")
    @set:PropertyName("active")
    var isActive: Boolean = false,

    // Direct fields (same name)
    var createdAt: Long = 0L,
    var createdBy: String = ""
) {
    constructor() : this("")
}