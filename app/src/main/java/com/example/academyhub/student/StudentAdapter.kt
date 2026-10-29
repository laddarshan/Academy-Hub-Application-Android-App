package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.models.Student
import com.example.academyhub.utils.ImageUtil
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StudentAdapter(
    private var students: List<Student>,
    private val onItemClick: (Student) -> Unit,
    private val onMoreClick: (Student, View) -> Unit
) : RecyclerView.Adapter<StudentAdapter.VH>() {

    private val firestore = FirebaseFirestore.getInstance()

    private val avatarColors = listOf(
        "#075E54", "#00897B", "#26A69A", "#00796B", "#004D40", "#00695C"
    )

    // ── Multi-select state ────────────────────────────────────
    private var multiSelectMode = false
    private var selectedIds: MutableSet<String> = mutableSetOf()

    fun setMultiSelectMode(enabled: Boolean, ids: MutableSet<String>) {
        multiSelectMode = enabled
        selectedIds = ids
        notifyDataSetChanged()
    }

    fun setSelectedIds(ids: MutableSet<String>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val photo:          CircleImageView = v.findViewById(R.id.ivStudentProfile)
        val avatarFallback: FrameLayout     = v.findViewById(R.id.avatarFallback)
        val avatarLetter:   TextView        = v.findViewById(R.id.tvAvatarLetter)
        val name:           TextView        = v.findViewById(R.id.tvStudentName)
        val course:         TextView        = v.findViewById(R.id.tvStudentCourse)
        val status:         TextView        = v.findViewById(R.id.tvStatus)
        val fees:           TextView        = v.findViewById(R.id.tvFeesStatus)
        val roll:           TextView        = v.findViewById(R.id.tvRollNo)
        val more:           ImageView       = v.findViewById(R.id.btnMore)
        val divider:        View            = v.findViewById(R.id.itemDivider)
        // item_student.xml ma CheckBox id="cbSelect" add karvu joie
        val checkBox:       CheckBox?       = v.findViewById(R.id.cbSelect)

        var photoJob: Job? = null
        var boundStudentId: String = ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false))

    override fun getItemCount() = students.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = students[position]

        h.photoJob?.cancel()
        h.boundStudentId = s.id

        h.name.text   = s.name
        h.course.text = buildString {
            if (s.courseName.isNotEmpty())   append(s.courseName)
            if (s.yearName.isNotEmpty())     append(" - ${s.yearName}")
            if (s.semesterName.isNotEmpty()) append(" - ${s.semesterName}")
        }
        h.roll.text = if (s.rollNumber.isNotEmpty()) s.rollNumber else "--"

        setStatusPill(h.status, s.status)
        setFeesPill(h.fees, s.feesStatus)

        val colorHex = avatarColors[Math.abs(s.name.hashCode()) % avatarColors.size]
        (h.avatarFallback.background as? GradientDrawable)?.setColor(Color.parseColor(colorHex))
        h.avatarLetter.text = s.name.firstOrNull()?.uppercase() ?: "?"

        h.photo.visibility          = View.GONE
        h.avatarFallback.visibility = View.VISIBLE
        h.photo.setImageDrawable(null)

        h.divider.visibility = if (position < students.size - 1) View.VISIBLE else View.GONE

        // ── Multi-select mode ─────────────────────────────────
        if (multiSelectMode) {
            val isSelected = selectedIds.contains(s.id)
            h.checkBox?.visibility = View.VISIBLE
            h.checkBox?.isChecked  = isSelected
            h.more.visibility      = View.GONE
            // Selected hoy to light green background
            h.itemView.setBackgroundColor(
                if (isSelected) Color.parseColor("#E8F5E9") else Color.TRANSPARENT
            )
        } else {
            h.checkBox?.visibility = View.GONE
            h.more.visibility      = View.VISIBLE
            h.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        loadPhoto(h, s)

        // Row click
        h.itemView.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (!multiSelectMode) {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(h.itemView, "scaleX", 1f, 0.98f, 1f).apply { duration = 130 },
                        ObjectAnimator.ofFloat(h.itemView, "scaleY", 1f, 0.98f, 1f).apply { duration = 130 }
                    )
                    start()
                }
                h.itemView.postDelayed({ onItemClick(students[pos]) }, 100)
            } else {
                onItemClick(students[pos])
            }
        }

        h.more.setOnClickListener {
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMoreClick(students[pos], h.more)
        }
    }

    override fun onViewRecycled(h: VH) {
        super.onViewRecycled(h)
        h.photoJob?.cancel()
        h.boundStudentId = ""
        h.photo.setImageDrawable(null)
        h.photo.visibility          = View.GONE
        h.avatarFallback.visibility = View.VISIBLE
        h.itemView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun loadPhoto(h: VH, s: Student) {
        if (s.id.isEmpty()) return
        val targetId = s.id
        h.photoJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                ImageUtil.loadImageFromChunks(
                    firestore   = firestore,
                    userId      = s.id,
                    collection  = "Students",
                    imageView   = h.photo,
                    progressBar = null,
                    onComplete  = {
                        if (h.boundStudentId != targetId) return@loadImageFromChunks
                        val drawable = h.photo.drawable
                        val systemGalleryState = h.photo.context
                            .getDrawable(android.R.drawable.ic_menu_gallery)?.constantState
                        val isSystemFallback = drawable?.constantState == systemGalleryState
                        if (drawable != null && !isSystemFallback) {
                            h.photo.visibility          = View.VISIBLE
                            h.avatarFallback.visibility = View.GONE
                        } else {
                            h.photo.visibility          = View.GONE
                            h.avatarFallback.visibility = View.VISIBLE
                        }
                    }
                )
            } catch (e: Exception) {
                if (h.boundStudentId == targetId) {
                    h.photo.visibility          = View.GONE
                    h.avatarFallback.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setStatusPill(tv: TextView, status: String) {
        val (text, textColor, bgColor) = when (status) {
            "active"   -> Triple("Active",   "#075E54", "#E0F2F1")
            "inactive" -> Triple("Inactive", "#616161", "#F5F5F5")
            "blocked"  -> Triple("Blocked",  "#C62828", "#FFEBEE")
            "passout"  -> Triple("Pass Out", "#1565C0", "#E3F2FD")
            else       -> Triple(status.replaceFirstChar { it.uppercase() }, "#616161", "#F5F5F5")
        }
        tv.text = text
        tv.setTextColor(Color.parseColor(textColor))
        (tv.background as? GradientDrawable)?.setColor(Color.parseColor(bgColor))
    }

    private fun setFeesPill(tv: TextView, feesStatus: String) {
        val (text, textColor, bgColor) = when (feesStatus) {
            "paid"    -> Triple("Paid",    "#2E7D32", "#E8F5E9")
            "pending" -> Triple("Pending", "#E65100", "#FFF8E1")
            "partial" -> Triple("Partial", "#F57F17", "#FFFDE7")
            else      -> Triple("N/A",     "#9E9E9E", "#F5F5F5")
        }
        tv.text = text
        tv.setTextColor(Color.parseColor(textColor))
        (tv.background as? GradientDrawable)?.setColor(Color.parseColor(bgColor))
    }

    fun updateList(newList: List<Student>) {
        students = newList
        notifyDataSetChanged()
    }

    fun animateEntrance(rv: RecyclerView) {
        rv.post {
            val lm   = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return@post
            val last = lm.findLastVisibleItemPosition()
            for (i in 0..last) {
                val child = lm.findViewByPosition(i) ?: continue
                child.alpha        = 0f
                child.translationY = 32f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(child, "alpha", 0f, 1f).apply { duration = 250 },
                        ObjectAnimator.ofFloat(child, "translationY", 32f, 0f).apply {
                            duration = 270; interpolator = DecelerateInterpolator()
                        }
                    )
                    startDelay = (i * 40L); start()
                }
            }
        }
    }
}