package com.example.academyhub.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemDayHeaderBinding
import com.example.academyhub.databinding.ItemTimetableBinding
import com.example.academyhub.models.DayTimetable
import com.example.academyhub.models.Timetable

class TimetableWeekAdapter(
    private var dayTimetables: List<DayTimetable>,
    private val onItemClick: (Timetable) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val flattenedList = mutableListOf<Any>()

    init {
        flattenList()
    }

    private fun flattenList() {
        flattenedList.clear()
        for (dayTimetable in dayTimetables) {
            flattenedList.add(dayTimetable.day) // Add header
            flattenedList.addAll(dayTimetable.timetables) // Add items
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (flattenedList[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemDayHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            HeaderViewHolder(binding)
        } else {
            val binding = ItemTimetableBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            TimetableViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(flattenedList[position] as String)
        } else if (holder is TimetableViewHolder) {
            holder.bind(flattenedList[position] as Timetable)
        }
    }

    override fun getItemCount(): Int = flattenedList.size

    inner class HeaderViewHolder(private val binding: ItemDayHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(day: String) {
            binding.tvDayName.text = day
        }
    }

    inner class TimetableViewHolder(private val binding: ItemTimetableBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(timetable: Timetable) {
            val startTime = timetable.startTime.replace(" AM", "").replace(" PM", "")
            val endTime = timetable.endTime.replace(" AM", "").replace(" PM", "")

            binding.tvStartTime.text = startTime
            binding.tvEndTime.text = endTime
            binding.tvDuration.text = "${timetable.duration} min"

            binding.tvSubject.text = timetable.subject
            binding.tvTeacher.text = "${timetable.teacherName}"
            binding.tvBatchCourse.text = "${timetable.courseName} - ${timetable.batchName}"

            binding.tvClassroom.text = if (timetable.type == "online") {
                "💻 Online Class"
            } else {
                "🏛️ ${timetable.classroom.ifEmpty { "Room TBA" }}"
            }

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
            }

            binding.root.setOnClickListener {
                onItemClick(timetable)
            }

            // Hide more button in week view
            binding.btnMore.visibility = android.view.View.GONE
        }
    }

    fun updateList(newList: List<DayTimetable>) {
        dayTimetables = newList
        flattenList()
        notifyDataSetChanged()
    }
}