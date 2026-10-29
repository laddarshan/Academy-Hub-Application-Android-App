package com.example.academyhub.course

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemCourseBinding
import com.example.academyhub.models.Course

class CourseAdapter(
    private var courses: List<Course>,
    private val onEdit  : (Course) -> Unit,
    private val onToggle: (Course) -> Unit,
    private val onDelete: (Course) -> Unit
) : RecyclerView.Adapter<CourseAdapter.VH>() {

    inner class VH(val b: ItemCourseBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(ItemCourseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = courses.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val course = courses[pos]
        val b      = holder.b
        val ctx    = b.root.context

        // Initials in circle (e.g. BCA, BBA)
        b.tvCourseIcon.text   = course.courseCode.take(3).uppercase()
        b.tvCourseName.text   = course.courseName
        b.tvCourseCode.text   = "Code: ${course.courseCode}"
        b.tvCourseDuration.text =
            "${course.duration} Year${if (course.duration > 1) "s" else ""}  •  ${course.totalSemesters} Semesters"
        b.tvStudentCount.text =
            "${course.studentCount} Student${if (course.studentCount != 1) "s" else ""}"

        // Status chip + left indicator bar colour
        if (course.isActive) {
            b.tvStatus.text = "Active"
            b.tvStatus.setBackgroundColor(Color.parseColor("#26A69A"))
            b.viewIndicator.setBackgroundColor(Color.parseColor("#075E54"))
        } else {
            b.tvStatus.text = "Inactive"
            b.tvStatus.setBackgroundColor(Color.parseColor("#9E9E9E"))
            b.viewIndicator.setBackgroundColor(Color.parseColor("#9E9E9E"))
        }

        // Edit — scale zoom animation
        b.btnEdit.setOnClickListener {
            ObjectAnimator.ofFloat(it, "scaleX", 1f, 1.25f, 1f).apply { duration = 200; start() }
            ObjectAnimator.ofFloat(it, "scaleY", 1f, 1.25f, 1f).apply { duration = 200; start() }
            it.postDelayed({ onEdit(course) }, 140)
        }

        // More popup — Activate/Deactivate + Delete
        b.btnMore.setOnClickListener { view ->
            ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f).apply { duration = 180; start() }
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f).apply { duration = 180; start() }

            val popup      = PopupMenu(ctx, view)
            val toggleLabel = if (b.tvStatus.text.toString() == "Active") "Deactivate" else "Activate"
            popup.menu.add(0, 1, 0, toggleLabel)
            popup.menu.add(0, 2, 1, "Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onToggle(course); true }
                    2 -> { onDelete(course); true }
                    else -> false
                }
            }
            popup.show()
        }

        // Stagger slide-up + fade entrance
        b.root.alpha       = 0f
        b.root.translationY = 16f
        b.root.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220).setStartDelay(pos * 45L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun updateList(newList: List<Course>) {
        courses = newList
        notifyDataSetChanged()
    }
}
