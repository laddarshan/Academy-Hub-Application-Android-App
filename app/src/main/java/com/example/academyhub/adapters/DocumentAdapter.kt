    package com.example.academyhub.teacher.adapters

    import android.graphics.Color
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import androidx.recyclerview.widget.RecyclerView
    import com.example.academyhub.databinding.ItemDocumentBinding
    import com.example.academyhub.models.Document
    import java.text.SimpleDateFormat
    import java.util.*

    class DocumentAdapter(
        private var documents: List<Document>,
        private val onItemClick: (Document) -> Unit,
        private val onDeleteClick: (Document) -> Unit,
        private val onVerifyClick: (Document) -> Unit
    ) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

        inner class DocumentViewHolder(private val binding: ItemDocumentBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(document: Document) {
                // Document Title & Type
                binding.tvDocumentTitle.text = document.documentName
                binding.tvDocumentType.text = document.documentType

                // File Size & Type
                // જો તમારા મોડલમાં fileType ન હોય તો document.fileName માંથી extension લો
                binding.tvFileSize.text = "${String.format("%.2f", document.fileSize / 1024.0)} KB"
                binding.tvFileType.text = document.fileName.substringAfterLast(".", "FILE").uppercase()

                // Status Badge Logic
                val statusText: String
                val colorStr: String

                if (document.isVerified) {
                    statusText = "VERIFIED"
                    colorStr = "#4CAF50" // Green
                    binding.tvVerificationInfo.text = "✓ Verified"
                    binding.tvVerificationInfo.visibility = View.VISIBLE
                    binding.btnVerify.visibility = View.GONE
                } else {
                    statusText = "PENDING"
                    colorStr = "#FF9800" // Orange
                    binding.tvVerificationInfo.visibility = View.GONE
                    binding.btnVerify.visibility = View.VISIBLE
                    binding.btnVerify.text = "Verify"
                }

                binding.tvStatus.text = statusText
                val statusColor = Color.parseColor(colorStr)
                binding.tvStatus.setBackgroundColor(statusColor)

                // Apply light tint to card background
                binding.cardView.setCardBackgroundColor(statusColor)
                binding.cardView.alpha = 0.9f // કાર્ડને થોડું ટ્રાન્સપરન્ટ રાખવા

                // Upload Date
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvUploadDate.text = "Uploaded: ${dateFormat.format(Date(document.uploadedAt))}"

                // Click listeners
                binding.root.setOnClickListener { onItemClick(document) }
                binding.btnDelete.setOnClickListener { onDeleteClick(document) }
                binding.btnVerify.setOnClickListener { onVerifyClick(document) }

                // View button (જો XML માં હોય તો)
                binding.btnView.setOnClickListener { onItemClick(document) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
            val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return DocumentViewHolder(binding)
        }

        override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
            holder.bind(documents[position])
        }

        override fun getItemCount(): Int = documents.size

        fun updateList(newList: List<Document>) {
            documents = newList
            notifyDataSetChanged()
        }
    }