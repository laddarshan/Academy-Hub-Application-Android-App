package com.example.academyhub.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemLeaveBinding
import com.example.academyhub.models.Leave

class LeaveAdapter(
    private var leaves: List<Leave>,
    private val onItemClick: (Leave) -> Unit,
    private val onApproveClick: (Leave) -> Unit,
    private val onRejectClick: (Leave) -> Unit
) : RecyclerView.Adapter<LeaveAdapter.LeaveViewHolder>() {

    inner class LeaveViewHolder(private val binding: ItemLeaveBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(leave: Leave) {
            // Leave Type
            binding.tvLeaveType.text = leave.getLeaveTypeLabel()
            
            // Leave Type Icon
            binding.ivLeaveIcon.setImageResource(
                when (leave.leaveType) {
                    "casual" -> android.R.drawable.ic_menu_day
                    "sick" -> android.R.drawable.ic_menu_info_details
                    "emergency" -> android.R.drawable.ic_dialog_alert
                    else -> android.R.drawable.ic_menu_help
                }
            )

            // Date Range
            binding.tvDateRange.text = leave.getDateRange()
            
            // Duration
            binding.tvDuration.text = leave.getDaysLabel()

            // Reason
            binding.tvReason.text = leave.reason

            // Status Badge
            binding.tvStatus.text = leave.status.uppercase()
            val statusColor = android.graphics.Color.parseColor(leave.getStatusColor())
            binding.tvStatus.setBackgroundColor(statusColor)
            
            // Apply tint to card
            binding.cardView.setCardBackgroundColor(
                android.graphics.Color.argb(30,
                    android.graphics.Color.red(statusColor),
                    android.graphics.Color.green(statusColor),
                    android.graphics.Color.blue(statusColor)
                )
            )

            // Applied Date
            val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            binding.tvAppliedDate.text = "Applied: ${dateFormat.format(leave.appliedAt)}"

            // Show/Hide action buttons based on status
            if (leave.isPending()) {
                binding.layoutActions.visibility = View.VISIBLE
                binding.btnApprove.setOnClickListener { onApproveClick(leave) }
                binding.btnReject.setOnClickListener { onRejectClick(leave) }
            } else {
                binding.layoutActions.visibility = View.GONE
            }

            // Additional Info for approved/rejected
            if (leave.isApproved()) {
                binding.tvAdditionalInfo.text = "✓ Approved by ${leave.approvedByName}"
                binding.tvAdditionalInfo.visibility = View.VISIBLE
            } else if (leave.isRejected()) {
                binding.tvAdditionalInfo.text = "✗ Rejected: ${leave.rejectionReason}"
                binding.tvAdditionalInfo.visibility = View.VISIBLE
            } else {
                binding.tvAdditionalInfo.visibility = View.GONE
            }

            // Document indicator
            if (leave.documentUrl.isNotEmpty()) {
                binding.ivDocumentIcon.visibility = View.VISIBLE
                binding.tvDocumentLabel.text = "Document attached"
                binding.tvDocumentLabel.visibility = View.VISIBLE
            } else {
                binding.ivDocumentIcon.visibility = View.GONE
                binding.tvDocumentLabel.visibility = View.GONE
            }

            // Substitute teacher info
            if (leave.substituteAssigned && leave.substituteTeacherName.isNotEmpty()) {
                binding.tvSubstituteInfo.text = "Substitute: ${leave.substituteTeacherName}"
                binding.tvSubstituteInfo.visibility = View.VISIBLE
            } else {
                binding.tvSubstituteInfo.visibility = View.GONE
            }

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(leave)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaveViewHolder {
        val binding = ItemLeaveBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LeaveViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaveViewHolder, position: Int) {
        holder.bind(leaves[position])
    }

    override fun getItemCount(): Int = leaves.size

    fun updateList(newList: List<Leave>) {
        leaves = newList
        notifyDataSetChanged()
    }
}
