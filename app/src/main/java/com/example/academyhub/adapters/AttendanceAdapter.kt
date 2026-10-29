package com.example.academyhub.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemAttendanceBinding
import com.example.academyhub.models.TeacherAttendance

class AttendanceAdapter(
    private var attendances: List<TeacherAttendance>,
    private val onItemClick: (TeacherAttendance) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {

    inner class AttendanceViewHolder(private val binding: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(attendance: TeacherAttendance) {
            // Date Info
            binding.tvDate.text = attendance.date
            binding.tvDay.text = attendance.day

            // Status Badge
            binding.tvStatus.text = attendance.status.uppercase()
            val statusColor = android.graphics.Color.parseColor(attendance.getStatusColor())
            binding.tvStatus.setBackgroundColor(statusColor)
            binding.cardView.setCardBackgroundColor(
                android.graphics.Color.argb(30, 
                    android.graphics.Color.red(statusColor),
                    android.graphics.Color.green(statusColor),
                    android.graphics.Color.blue(statusColor)
                )
            )

            // Time Details (only for present/late)
            if (attendance.isPresent() || attendance.isLate) {
                binding.layoutTimeDetails.visibility = View.VISIBLE
                binding.tvCheckIn.text = "In: ${attendance.checkInTime}"
                
                if (attendance.checkOutTime.isNotEmpty()) {
                    binding.tvCheckOut.text = "Out: ${attendance.checkOutTime}"
                    binding.tvCheckOut.visibility = View.VISIBLE
                } else {
                    binding.tvCheckOut.visibility = View.GONE
                }

                if (attendance.totalHours > 0) {
                    binding.tvTotalHours.text = attendance.getFormattedHours()
                    binding.tvTotalHours.visibility = View.VISIBLE
                } else {
                    binding.tvTotalHours.visibility = View.GONE
                }
            } else {
                binding.layoutTimeDetails.visibility = View.GONE
            }

            // Late Badge
            if (attendance.isLate) {
                binding.tvLateTag.text = "⏰ Late by ${attendance.lateMinutes} min"
                binding.tvLateTag.visibility = View.VISIBLE
            } else {
                binding.tvLateTag.visibility = View.GONE
            }

            // Leave Type (if on leave)
            if (attendance.isOnLeave() && attendance.leaveType.isNotEmpty()) {
                binding.tvLeaveType.text = "${attendance.leaveType.uppercase()} LEAVE"
                binding.tvLeaveType.visibility = View.VISIBLE
            } else {
                binding.tvLeaveType.visibility = View.GONE
            }

            // Remarks
            if (attendance.remarks.isNotEmpty()) {
                binding.tvRemarks.text = "📝 ${attendance.remarks}"
                binding.tvRemarks.visibility = View.VISIBLE
            } else {
                binding.tvRemarks.visibility = View.GONE
            }

            // Status Icon
            binding.ivStatusIcon.setImageResource(
                when {
                    attendance.isPresent() -> android.R.drawable.checkbox_on_background
                    attendance.isAbsent() -> android.R.drawable.ic_delete
                    attendance.isOnLeave() -> android.R.drawable.ic_menu_info_details
                    attendance.isHalfDay() -> android.R.drawable.ic_menu_day
                    else -> android.R.drawable.ic_menu_help
                }
            )

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(attendance)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(attendances[position])
    }

    override fun getItemCount(): Int = attendances.size

    fun updateList(newList: List<TeacherAttendance>) {
        attendances = newList
        notifyDataSetChanged()
    }
}
