package com.example.academyhub.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemTimetableBinding
import com.example.academyhub.models.Timetable

class TimetableAdapter(
    private var timetables: List<Timetable>,
    private val onItemClick: (Timetable) -> Unit,
    private val onMoreClick: (Timetable) -> Unit
) : RecyclerView.Adapter<TimetableAdapter.TimetableViewHolder>() {

    inner class TimetableViewHolder(private val binding: ItemTimetableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(timetable: Timetable) {
            // Time display
            val startTime = timetable.startTime.replace(" AM", "").replace(" PM", "")
            val endTime = timetable.endTime.replace(" AM", "").replace(" PM", "")

            binding.tvStartTime.text = startTime
            binding.tvEndTime.text = endTime
            binding.tvDuration.text = "${timetable.duration} min"

            // Class details
            binding.tvSubject.text = timetable.subject
            binding.tvTeacher.text = "${timetable.teacherName}"
            binding.tvBatchCourse.text = "${timetable.courseName} - ${timetable.batchName}"

            // Classroom
            binding.tvClassroom.text = if (timetable.type == "online") {
                "Online Class"
            } else {
                "${timetable.classroom.ifEmpty { "Room TBA" }}"
            }

            // Class type with color coding
            when (timetable.type) {
                "regular" -> {
                    binding.tvType.text = "Regular"
                    binding.tvType.setBackgroundColor(Color.parseColor("#4CAF50"))
                }
                "online" -> {
                    binding.tvType.text = "Online"
                    binding.tvType.setBackgroundColor(Color.parseColor("#2196F3"))
                }
                "exam" -> {
                    binding.tvType.text = "Exam"
                    binding.tvType.setBackgroundColor(Color.parseColor("#F44336"))
                }
                "practical" -> {
                    binding.tvType.text = "Practical"
                    binding.tvType.setBackgroundColor(Color.parseColor("#FF9800"))
                }
                else -> {
                    binding.tvType.text = "Other"
                    binding.tvType.setBackgroundColor(Color.parseColor("#9E9E9E"))
                }
            }

            // Click listeners
            binding.root.setOnClickListener {
                onItemClick(timetable)
            }

            binding.btnMore.setOnClickListener {
                onMoreClick(timetable)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimetableViewHolder {
        val binding = ItemTimetableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimetableViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimetableViewHolder, position: Int) {
        holder.bind(timetables[position])
    }

    override fun getItemCount(): Int = timetables.size

    fun updateList(newList: List<Timetable>) {
        timetables = newList
        notifyDataSetChanged()
    }
}