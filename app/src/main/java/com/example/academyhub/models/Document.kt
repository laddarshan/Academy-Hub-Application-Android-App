package com.example.academyhub.models

data class Document(
    var documentId: String = "",
    var teacherId: String = "",
    var teacherName: String = "",

    // Document Information
    var documentType: String = "other",
    var documentName: String = "",
    var documentTitle: String = "",
    var description: String = "",

    // File Information
    var downloadUrl: String = "",
    var storagePath: String = "",
    var fileName: String = "",
    var fileType: String = "",
    var fileSize: Long = 0,

    // Verification Status
    var isVerified: Boolean = false,
    var status: String = "pending",
    var uploadDate: String = "",
    var uploadedBy: String = "",

    // Timestamps
    var uploadedAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    // ૧. ફાઈનલ સોલ્યુશન: નીચેની લાઈન કાઢી નાખો (Remove this)
    // fun isVerified(): Boolean = isVerified -> આની જરૂર નથી, એરર અહીં જ છે.

    // ૨. જો તમારે Adapter માટે આ મેથડ રાખવી જ હોય, તો તેનું નામ બદલી નાખો:
    fun checkIfVerified(): Boolean = isVerified

    fun isPending(): Boolean = status == "pending"
    fun isRejected(): Boolean = status == "rejected"
}