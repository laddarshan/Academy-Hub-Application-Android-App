package com.example.academyhub.adapters

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemStudentFeesBinding

data class StudentFeeItem(
    val studentDocId : String = "",
    val name         : String = "",
    val rollNumber   : String = "",
    val courseId     : String = "",
    val courseName   : String = "",
    val semesterId   : String = "",
    val semesterName : String = "",
    val totalFees    : Double = 0.0,
    val paidFees     : Double = 0.0,
    val pendingFees  : Double = 0.0,
    val feesStatus   : String = "pending"  // paid / partial / pending
)

class StudentFeesAdapter(
    private val items       : MutableList<StudentFeeItem>,
    private val onItemClick : (StudentFeeItem) -> Unit,
    private val onPayFees   : (StudentFeeItem) -> Unit
) : RecyclerView.Adapter<StudentFeesAdapter.VH>() {

    inner class VH(val b: ItemStudentFeesBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStudentFeesBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b    = holder.b

        // ── Avatar initial ──
        b.tvInitials.text = item.name.firstOrNull()?.uppercase() ?: "?"

        // ── Name + Roll ──
        b.tvStudentName.text = item.name
        b.tvRollNumber.text  = "Roll: ${item.rollNumber}"

        // ── Pending amount ──
        b.tvPendingFees.text = "₹${fmt(item.pendingFees)}"

        // ── Paid % mini progress ──
        val pct = if (item.totalFees > 0)
            ((item.paidFees / item.totalFees) * 100).toInt() else 0

        b.miniProgress.progress = 0
        ObjectAnimator.ofInt(b.miniProgress, "progress", 0, pct)
            .apply { duration = 600; interpolator = DecelerateInterpolator() }
            .start()
        b.tvPaidPercent.text = "$pct%"

        // ── Status badge + accent bar ──
        when (item.feesStatus) {
            "paid" -> {
                b.tvFeesStatus.text = "Paid"
                b.tvFeesStatus.setTextColor(Color.parseColor("#059669"))
                b.viewStatusBar.setBackgroundColor(Color.parseColor("#059669"))
                b.tvPendingFees.setTextColor(Color.parseColor("#059669"))
                b.miniProgress.progressTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#059669"))
                b.tvPaidPercent.setTextColor(Color.parseColor("#059669"))
            }
            "partial" -> {
                b.tvFeesStatus.text = "Partial"
                b.tvFeesStatus.setTextColor(Color.parseColor("#D97706"))
                b.viewStatusBar.setBackgroundColor(Color.parseColor("#D97706"))
                b.tvPendingFees.setTextColor(Color.parseColor("#D97706"))
                b.miniProgress.progressTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#D97706"))
                b.tvPaidPercent.setTextColor(Color.parseColor("#D97706"))
            }
            else -> {
                b.tvFeesStatus.text = "Pending"
                b.tvFeesStatus.setTextColor(Color.parseColor("#C62828"))
                b.viewStatusBar.setBackgroundColor(Color.parseColor("#C62828"))
                b.tvPendingFees.setTextColor(Color.parseColor("#C62828"))
                b.miniProgress.progressTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))
                b.tvPaidPercent.setTextColor(Color.parseColor("#C62828"))
            }
        }

        // ── Pay button — hide if fully paid ──
        if (item.feesStatus == "paid" || item.pendingFees <= 0) {
            b.btnPayFees.visibility = android.view.View.GONE
        } else {
            b.btnPayFees.visibility = android.view.View.VISIBLE
            b.btnPayFees.setOnClickListener { v ->
                animPress(v) { onPayFees(item) }
            }
        }

        // ── Row click → full details ──
        b.root.setOnClickListener { onItemClick(item) }

        // ── Staggered entrance animation ──
        b.root.alpha = 0f
        b.root.translationX = -16f
        b.root.postDelayed({
            b.root.animate().alpha(1f).translationX(0f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, position * 50L)
    }

    // ─────────────────────────────────────────────────────────────────────
    fun updateList(newItems: List<StudentFeeItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun filter(query: String, status: String): List<StudentFeeItem> {
        return items.filter { item ->
            val matchSearch = query.isEmpty() ||
                    item.name.lowercase().contains(query) ||
                    item.rollNumber.lowercase().contains(query)
            val matchStatus = status == "all" || item.feesStatus == status
            matchSearch && matchStatus
        }
    }

    private fun animPress(view: android.view.View, onComplete: () -> Unit) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(2f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    private fun fmt(a: Double) = String.format("%,.0f", a)
}