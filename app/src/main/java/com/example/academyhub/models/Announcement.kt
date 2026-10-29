package com.example.academyhub.models

data class Announcement(
    var id: String = "",
    var title: String = "",
    var message: String = "",
    var priority: String = "normal",
    var sentTo: List<String> = listOf(),
    var hasPdf: Boolean = false,
    var pdfFileName: String = "",
    var pdfChunkCount: Int = 0,
    var createdBy: String = "",
    var createdByName: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    var type: String = ""
) {
    constructor() : this("")
}
