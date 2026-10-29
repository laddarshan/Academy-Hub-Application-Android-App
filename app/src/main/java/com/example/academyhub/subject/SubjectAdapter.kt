package com.example.academyhub.subject

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemSubjectBinding
import com.example.academyhub.models.Subject

class SubjectAdapter(
    private var subjects: List<Subject>,
    private val onEditClick:   (Subject) -> Unit,
    private val onDeleteClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    // Color palette for initials circles
    private val colors = listOf(
        "#075E54","#1565C0","#6A1B9A","#E65100",
        "#2E7D32","#AD1457","#00838F","#4527A0"
    )

    inner class SubjectViewHolder(private val binding: ItemSubjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subject: Subject, position: Int) {
            binding.tvSubjectName.text = subject.subjectName
            binding.tvSubjectCode.text = subject.subjectCode

            // Initials from subject name
            val initials = subject.subjectName
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it[0].uppercaseChar().toString() }
                .ifEmpty { "S" }
            binding.tvInitials.text = initials

            // Color based on position
            val color = colors[position % colors.size]
            (binding.root.findViewWithTag<android.view.View>("initialsFrame")
                ?: binding.root.getChildAt(1))
                .background?.let {
                    try {
                        (it as android.graphics.drawable.GradientDrawable).setColor(Color.parseColor(color))
                    } catch (_: Exception) {}
                }

            // Stagger entrance animation
            binding.root.alpha = 0f
            binding.root.translationX = -20f
            ObjectAnimator.ofFloat(binding.root, "alpha", 0f, 1f).apply {
                duration = 230; startDelay = position * 40L; interpolator = DecelerateInterpolator(); start()
            }
            ObjectAnimator.ofFloat(binding.root, "translationX", -20f, 0f).apply {
                duration = 230; startDelay = position * 40L; interpolator = DecelerateInterpolator(); start()
            }

            binding.btnEdit.setOnClickListener   { onEditClick(subject)   }
            binding.btnDelete.setOnClickListener { onDeleteClick(subject) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SubjectViewHolder(ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) =
        holder.bind(subjects[position], position)

    override fun getItemCount() = subjects.size

    fun updateList(newList: List<Subject>) {
        subjects = newList
        notifyDataSetChanged()
    }
}
