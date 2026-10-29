package com.example.academyhub.session

import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ItemSessionBinding
import com.example.academyhub.models.Session

class SessionAdapter(
    private var sessions    : List<Session>,
    private val onSetCurrent: (Session) -> Unit,
    private val onEdit      : (Session) -> Unit,
    private val onToggle    : (Session) -> Unit,
    private val onDelete    : (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    inner class VH(val b: ItemSessionBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        VH(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = sessions.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val session = sessions[pos]
        val b       = holder.b
        val ctx     = b.root.context

        b.tvSessionName.text  = session.sessionName
        b.tvSessionDates.text = "${session.startDate} - ${session.endDate}"

        // ── Current chip: only shown when session is current ──────────────────────
        if (session.isCurrent) {
            b.tvStatus.visibility = android.view.View.VISIBLE
            b.tvStatus.text = "Current"
            b.viewIndicator.setBackgroundColor(Color.parseColor("#075E54"))
        } else {
            b.tvStatus.visibility = android.view.View.GONE
            b.viewIndicator.setBackgroundColor(
                if (session.isActive) Color.parseColor("#1565C0")
                else Color.parseColor("#9E9E9E")
            )
        }

        // ── Active / Inactive chip: always visible ─────────────────────────────
        if (session.isActive) {
            b.tvActiveStatus.text = "Active"
            b.tvActiveStatus.setBackgroundColor(Color.parseColor("#26A69A"))
        } else {
            b.tvActiveStatus.text = "Inactive"
            b.tvActiveStatus.setBackgroundColor(Color.parseColor("#9E9E9E"))
        }

        // Set Current button — scale + indicator zoom (Interaction requirement)
        b.btnSetCurrent.setOnClickListener {
            // Scale zoom animation
            ObjectAnimator.ofFloat(it, "scaleX", 1f, 1.3f, 1f).apply { duration = 220; start() }
            ObjectAnimator.ofFloat(it, "scaleY", 1f, 1.3f, 1f).apply { duration = 220; start() }
            it.postDelayed({ onSetCurrent(session) }, 160)
        }

        // Edit button — scale zoom
        b.btnEdit.setOnClickListener {
            ObjectAnimator.ofFloat(it, "scaleX", 1f, 1.25f, 1f).apply { duration = 200; start() }
            ObjectAnimator.ofFloat(it, "scaleY", 1f, 1.25f, 1f).apply { duration = 200; start() }
            it.postDelayed({ onEdit(session) }, 140)
        }

        // More → popup: Toggle Active, Delete
        b.btnMore.setOnClickListener { view ->
            ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f).apply { duration = 180; start() }
            ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f).apply { duration = 180; start() }

            val popup = PopupMenu(ctx, view)
            // Label based on tvStatus text (which reflects latest loaded state)
            val isCurrentlyActive = b.tvActiveStatus.text.toString() == "Active"
            popup.menu.add(0, 1, 0, if (isCurrentlyActive) "Deactivate" else "Activate")
            popup.menu.add(0, 2, 1, "Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onToggle(session); true }
                    2 -> { onDelete(session); true }
                    else -> false
                }
            }
            popup.show()
        }

        // Stagger entrance: slide-up + fade
        b.root.alpha = 0f; b.root.translationY = 16f
        b.root.animate().alpha(1f).translationY(0f)
            .setDuration(220).setStartDelay(pos * 45L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    fun updateList(newList: List<Session>) {
        sessions = newList
        notifyDataSetChanged()
    }
}