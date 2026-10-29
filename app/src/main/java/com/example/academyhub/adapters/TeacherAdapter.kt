package com.example.academyhub.teacher.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemTeacherBinding
import com.example.academyhub.models.Teacher
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TeacherAdapter(
    private var teachers: List<Teacher>,
    private val onItemClick: (Teacher) -> Unit,
    private val onMoreClick: (Teacher) -> Unit
) : RecyclerView.Adapter<TeacherAdapter.TeacherViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val imageCache = mutableMapOf<String, String>()

    inner class TeacherViewHolder(private val binding: ItemTeacherBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(teacher: Teacher) {
            binding.tvTeacherName.text = teacher.name
            binding.tvSubject.text = teacher.subject
            binding.tvMobile.text = teacher.mobile
            binding.tvExperience.text = "${teacher.experience} Years Exp"
            binding.tvEmail.text = teacher.email

            // Status badge
            binding.tvStatus.text = teacher.status.uppercase()
            binding.tvStatus.setBackgroundColor(
                when (teacher.status) {
                    "active" -> android.graphics.Color.parseColor("#4CAF50")
                    "inactive" -> android.graphics.Color.parseColor("#FF9800")
                    "blocked" -> android.graphics.Color.parseColor("#F44336")
                    "on_leave" -> android.graphics.Color.parseColor("#2196F3")
                    else -> android.graphics.Color.parseColor("#9E9E9E")
                }
            )

            // Additional Info
            binding.tvQualification.text = "🎓 ${teacher.qualification}"
            binding.tvSalary.text = "💰 ₹${String.format("%,.0f", teacher.currentSalary)}/month"
            binding.tvRating.text = "⭐ ${teacher.getRatingString()}"
            binding.tvAttendance.text = "📊 ${teacher.getAttendancePercent()}"

            // Load profile image with caching
            if (imageCache.containsKey(teacher.id)) {
                ImageUtil.loadBase64Image(binding.ivTeacherProfile, imageCache[teacher.id])
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    ImageUtil.downloadPhotoChunks(
                        firestore = firestore,
                        userId = teacher.id,
                        collection = "Teachers",
                        onProgress = {},
                        onSuccess = { base64String ->
                            imageCache[teacher.id] = base64String
                            ImageUtil.loadBase64Image(binding.ivTeacherProfile, base64String)
                        },
                        onFailure = {
                            binding.ivTeacherProfile.setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    )
                }
            }

            // Click listeners
            binding.root.setOnClickListener {
                onItemClick(teacher)
            }

            binding.btnMore.setOnClickListener {
                onMoreClick(teacher)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeacherViewHolder {
        val binding = ItemTeacherBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TeacherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TeacherViewHolder, position: Int) {
        holder.bind(teachers[position])
    }

    override fun getItemCount(): Int = teachers.size

    fun updateList(newList: List<Teacher>) {
        teachers = newList
        notifyDataSetChanged()
    }
}
