package com.example.academyhub.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ItemMarkAttendanceBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.models.TeacherAttendance

class MarkAttendanceAdapter(
    private var teachers: List<Teacher>,
    private var selectedDate: String,
    private val onStatusChanged: (Teacher, String) -> Unit
) : RecyclerView.Adapter<MarkAttendanceAdapter.MarkAttendanceViewHolder>() {

    private val attendanceMap = mutableMapOf<String, String>() // teacherId to status
    private val existingAttendanceMap = mutableMapOf<String, TeacherAttendance>()

    inner class MarkAttendanceViewHolder(private val binding: ItemMarkAttendanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(teacher: Teacher, position: Int) {
            binding.tvTeacherName.text = teacher.name
            binding.tvSubject.text = teacher.subject
            binding.tvSerialNumber.text = "${position + 1}."

            // Get existing status or default to present
            val currentStatus = existingAttendanceMap[teacher.id]?.status 
                ?: attendanceMap[teacher.id] 
                ?: "present"

            // Set radio button based on status
            when (currentStatus) {
                "present" -> binding.rbPresent.isChecked = true
                "absent" -> binding.rbAbsent.isChecked = true
                "late" -> binding.rbLate.isChecked = true
                "half_day" -> binding.rbHalfDay.isChecked = true
                "on_leave" -> binding.rbLeave.isChecked = true
            }

            // If already marked, show indicator
            if (existingAttendanceMap.containsKey(teacher.id)) {
                binding.ivMarkedIndicator.visibility = View.VISIBLE
                binding.tvMarkedLabel.visibility = View.VISIBLE
            } else {
                binding.ivMarkedIndicator.visibility = View.GONE
                binding.tvMarkedLabel.visibility = View.GONE
            }

            // Radio group listener
            binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
                val status = when (checkedId) {
                    R.id.rbPresent -> "present"
                    R.id.rbAbsent -> "absent"
                    R.id.rbLate -> "late"
                    R.id.rbHalfDay -> "half_day"
                    R.id.rbLeave -> "on_leave"
                    else -> "present"
                }
                
                attendanceMap[teacher.id] = status
                onStatusChanged(teacher, status)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MarkAttendanceViewHolder {
        val binding = ItemMarkAttendanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MarkAttendanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MarkAttendanceViewHolder, position: Int) {
        holder.bind(teachers[position], position)
    }

    override fun getItemCount(): Int = teachers.size

    fun updateTeachers(newList: List<Teacher>) {
        teachers = newList
        notifyDataSetChanged()
    }

    fun updateDate(newDate: String) {
        selectedDate = newDate
        existingAttendanceMap.clear()
        notifyDataSetChanged()
    }

    fun updateExistingAttendance(attendanceMap: Map<String, TeacherAttendance>) {
        existingAttendanceMap.clear()
        existingAttendanceMap.putAll(attendanceMap)
        
        // Update current selections
        attendanceMap.forEach { (teacherId, attendance) ->
            this.attendanceMap[teacherId] = attendance.status
        }
        
        notifyDataSetChanged()
    }

    fun markAllAs(status: String) {
        teachers.forEach { teacher ->
            attendanceMap[teacher.id] = status
        }
        notifyDataSetChanged()
    }

    fun getAttendanceData(): Map<Teacher, String> {
        val result = mutableMapOf<Teacher, String>()
        teachers.forEach { teacher ->
            val status = attendanceMap[teacher.id] ?: "present"
            result[teacher] = status
        }
        return result
    }
}
