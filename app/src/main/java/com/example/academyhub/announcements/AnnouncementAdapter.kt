package com.example.academyhub.announcements

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.models.Announcement
import java.text.SimpleDateFormat
import java.util.*

class AnnouncementAdapter(
    private val list    : List<Announcement>,
    private val isAdmin : Boolean = false, // લાઈન 20: isAdmin પેરામીટર ઉમેર્યો
    private val onDelete: (Announcement) -> Unit = {},
    private val onEdit  : (Announcement) -> Unit = {}
) : RecyclerView.Adapter<AnnouncementAdapter.VH>() {

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val tvTitle      : TextView     = root.findViewById(R.id.tvTitle)
        val tvMessage    : TextView     = root.findViewById(R.id.tvMessage)
        val tvDate       : TextView     = root.findViewById(R.id.tvDate)
        val tvPriority   : TextView     = root.findViewById(R.id.tvPriority)
        val tvSentTo     : TextView     = root.findViewById(R.id.tvSentTo)
        val viewAccent   : View         = root.findViewById(R.id.viewAccent)
        val layoutActions: LinearLayout = root.findViewById(R.id.layoutActions)
        val btnEdit      : LinearLayout = root.findViewById(R.id.btnEdit)
        val btnDelete    : LinearLayout = root.findViewById(R.id.btnDelete)
        val btnViewPdf   : LinearLayout = root.findViewById(R.id.btnViewPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_announcement, parent, false)
        return VH(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val ann = list[pos]

        // ── Basic content ──────────────────────────────────────────────────────
        holder.tvTitle.text   = ann.title
        holder.tvMessage.text = ann.message
        holder.tvDate.text    = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(ann.createdAt))

        // ── Priority badge + accent bar ────────────────────────────────────────
        if (ann.priority == "important") {
            holder.tvPriority.visibility = View.VISIBLE
            holder.viewAccent.setBackgroundColor(Color.parseColor("#C62828"))
        } else {
            holder.tvPriority.visibility = View.GONE
            holder.viewAccent.setBackgroundColor(Color.parseColor("#075E54"))
        }

        // ── Sent To label ─────────────────────────────────────────────────────
        holder.tvSentTo.visibility = View.VISIBLE
        holder.tvSentTo.text = if (ann.sentTo.contains("all")) "📢 All Students"
        else "📌 ${ann.sentTo.size} course${if (ann.sentTo.size != 1) "s" else ""}"

        // ── Action buttons (Visible only if isAdmin is true) ──────────────────
        if (isAdmin) {
            holder.layoutActions.visibility = View.VISIBLE
            holder.btnEdit.visibility       = View.VISIBLE
            holder.btnDelete.visibility     = View.VISIBLE

            holder.btnEdit.setOnClickListener {
                animBtn(it)
                it.postDelayed({ onEdit(ann) }, 150)
            }
            holder.btnDelete.setOnClickListener {
                animBtn(it)
                it.postDelayed({ onDelete(ann) }, 150)
            }
        } else {
            holder.layoutActions.visibility = View.GONE
        }

        // ── PDF button ────────────────────────────────────────────────────────
        if (ann.hasPdf && ann.pdfFileName.isNotEmpty()) {
            holder.btnViewPdf.visibility = View.VISIBLE
            holder.btnViewPdf.setOnClickListener {
                holder.itemView.context.startActivity(
                    Intent(holder.itemView.context, AnnouncementPdfActivity::class.java).apply {
                        putExtra("announcementId", ann.id)
                        putExtra("pdfFileName",    ann.pdfFileName)
                        putExtra("chunkCount",     ann.pdfChunkCount)
                    }
                )
            }
        } else {
            holder.btnViewPdf.visibility = View.GONE
        }

        // ── Stagger entrance animation ─────────────────────────────────────────
        holder.root.alpha        = 0f
        holder.root.translationY = 18f
        holder.root.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220)
            .setStartDelay(pos * 40L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun animBtn(v: View) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }
}