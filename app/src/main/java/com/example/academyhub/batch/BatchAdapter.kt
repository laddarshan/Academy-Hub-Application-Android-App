package com.example.academyhub.batch

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemBatchBinding
import com.example.academyhub.models.Batch

class BatchAdapter(
    private var batches : List<Batch>,
    private val onEdit  : (Batch) -> Unit,
    private val onToggle: (Batch) -> Unit,
    private val onDelete: (Batch) -> Unit
) : RecyclerView.Adapter<BatchAdapter.VH>() {

    inner class VH(val b: ItemBatchBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(ItemBatchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = batches.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val batch = batches[pos]
        val b     = holder.b
        val ctx   = b.root.context

        // Initials from batch name (e.g. "Morning A" → "MA")
        b.tvBatchIcon.text = batch.batchName
            .split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

        b.tvBatchName.text   = batch.batchName
        b.tvBatchCode.text   = "Code: ${batch.batchCode}"
        b.tvBatchTiming.text = "${batch.startTime} - ${batch.endTime}"
        b.tvStudentCount.text = "0 Students"

        // Status chip + indicator bar
        if (batch.isActive) {
            b.tvStatus.text = "Active"
            b.tvStatus.setBackgroundColor(Color.parseColor("#26A69A"))
            b.viewIndicator.setBackgroundColor(Color.parseColor("#075E54"))
        } else {
            b.tvStatus.text = "Inactive"
            b.tvStatus.setBackgroundColor(Color.parseColor("#9E9E9E"))
            b.viewIndicator.setBackgroundColor(Color.parseColor("#9E9E9E"))
        }

        // Edit — scale zoom
        b.btnEdit.setOnClickListener {
            ObjectAnimator.ofFloat(it, "scaleX", 1f, 1.25f, 1f).apply { duration = 200; start() }
            ObjectAnimator.ofFloat(it, "scaleY", 1f, 1.25f, 1f).apply { duration = 200; start() }
            it.postDelayed({ onEdit(batch) }, 140)
        }

        // More popup
        b.btnMore.setOnClickListener { view ->
            ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f).apply { duration = 180; start() }
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f).apply { duration = 180; start() }

            val popup      = PopupMenu(ctx, view)
            val toggleLabel = if (b.tvStatus.text == "Active") "Deactivate" else "Activate"
            popup.menu.add(0, 1, 0, toggleLabel)
            popup.menu.add(0, 2, 1, "Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onToggle(batch); true }
                    2 -> { onDelete(batch); true }
                    else -> false
                }
            }
            popup.show()
        }

        // Stagger slide-up entrance
        b.root.alpha        = 0f
        b.root.translationY = 16f
        b.root.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220).setStartDelay(pos * 45L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun updateList(newList: List<Batch>) {
        batches = newList
        notifyDataSetChanged()
    }
}
