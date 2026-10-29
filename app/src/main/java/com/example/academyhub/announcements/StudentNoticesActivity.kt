package com.example.academyhub.announcements

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentNoticesBinding
import com.example.academyhub.databinding.ItemStudentNoticeBinding
import com.example.academyhub.models.Announcement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class StudentNoticesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentNoticesBinding
    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    private val allList      = mutableListOf<Announcement>()
    private val filteredList = mutableListOf<Announcement>()
    private lateinit var adapter: NotifAdapter

    private var courseId  = ""
    private var uid       = ""
    private var readIds   = mutableSetOf<String>()

    // Filter state: "all" / "unread" / "important" / "pdf"
    private var activeFilter = "all"

    // ─────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentNoticesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uid = auth.currentUser?.uid ?: prefs.getString("userId","") ?: ""

        adapter = NotifAdapter(filteredList) { ann -> onNotifClick(ann) }
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.isNestedScrollingEnabled = false

        setupChips()
        setupSearch()
        setupClicks()
        runEntranceAnim()
        loadStudentInfo()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load courseId then announcements
    // ─────────────────────────────────────────────────────────────────────
    private fun loadStudentInfo() {
        binding.progressBar.visibility = View.VISIBLE

        // Try from prefs first
        val savedCourseId = prefs.getString("studentCourseId","") ?: ""
        if (savedCourseId.isNotEmpty()) {
            courseId = savedCourseId
            loadReadIds()
            return
        }

        // Fallback: load from Firestore Students doc
        val docId = prefs.getString("studentDocId","") ?: ""
        if (docId.isNotEmpty()) {
            db.collection("Students").document(docId).get()
                .addOnSuccessListener { doc ->
                    courseId = doc.getString("courseId") ?: ""
                    // Save for next time
                    if (courseId.isNotEmpty()) {
                        prefs.edit().putString("studentCourseId", courseId).apply()
                    }
                    loadReadIds()
                }
                .addOnFailureListener { loadReadIds() }
        } else if (uid.isNotEmpty()) {
            // Try from Firebase Auth users collection
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    courseId = doc.getString("courseId") ?: ""
                    loadReadIds()
                }
                .addOnFailureListener { loadReadIds() }
        } else {
            loadReadIds()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load which announcements this user has already read
    // ─────────────────────────────────────────────────────────────────────
    private fun loadReadIds() {
        if (uid.isEmpty()) { loadAnnouncements(); return }

        db.collection("UserNotifications").document(uid)
            .collection("notifications").get()
            .addOnSuccessListener { snap ->
                readIds = snap.documents
                    .mapNotNull { it.getString("announcementId") }
                    .toMutableSet()
                loadAnnouncements()
            }
            .addOnFailureListener { loadAnnouncements() }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Load announcements — realtime
    // ─────────────────────────────────────────────────────────────────────
    private fun loadAnnouncements() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                binding.progressBar.visibility = View.GONE

                if (err != null || snap == null) {
                    binding.layoutEmpty.visibility     = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                    return@addSnapshotListener
                }

                allList.clear()
                snap.forEach { doc ->
                    val ann = doc.toObject(Announcement::class.java)
                    ann.id = doc.id

                    // Show if sentTo has "all", OR matches this student courseId
                    // If courseId still loading (empty) — show "all" tagged ones only
                    val isForMe = ann.sentTo.contains("all") ||
                            (courseId.isNotEmpty() && ann.sentTo.contains(courseId))

                    if (isForMe) {
                        ann.isRead = readIds.contains(ann.id)
                        allList.add(ann)
                    }
                }
                applyFilter()
                updateUnreadCount()
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Mark as read in Firestore
    // ─────────────────────────────────────────────────────────────────────
    private fun onNotifClick(ann: Announcement) {
        if (ann.isRead || uid.isEmpty()) return
        ann.isRead = true
        readIds.add(ann.id)

        db.collection("UserNotifications").document(uid)
            .collection("notifications")
            .add(mapOf(
                "announcementId" to ann.id,
                "isRead"         to true,
                "readAt"         to System.currentTimeMillis()
            ))

        applyFilter()
        updateUnreadCount()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Mark ALL as read
    // ─────────────────────────────────────────────────────────────────────
    private fun markAllRead() {
        if (uid.isEmpty()) return
        val batch = db.batch()
        allList.filter { !it.isRead }.forEach { ann ->
            ann.isRead = true
            readIds.add(ann.id)
            val ref = db.collection("UserNotifications").document(uid)
                .collection("notifications").document()
            batch.set(ref, mapOf(
                "announcementId" to ann.id,
                "isRead"         to true,
                "readAt"         to System.currentTimeMillis()
            ))
        }
        batch.commit()
        applyFilter()
        updateUnreadCount()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Filter + search
    // ─────────────────────────────────────────────────────────────────────
    private fun applyFilter() {
        val q = binding.etSearch.text.toString().lowercase().trim()
        filteredList.clear()
        filteredList.addAll(allList.filter { ann ->
            val matchFilter = when (activeFilter) {
                "unread"    -> !ann.isRead
                "important" -> ann.priority == "important"
                "pdf"       -> ann.hasPdf
                else        -> true
            }
            val matchSearch = q.isEmpty() ||
                    ann.title.lowercase().contains(q) ||
                    ann.message.lowercase().contains(q)
            matchFilter && matchSearch
        })

        adapter.notifyDataSetChanged()
        binding.layoutEmpty.visibility =
            if (filteredList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvNotifications.visibility =
            if (filteredList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateUnreadCount() {
        val unread = allList.count { !it.isRead }
        binding.tvUnreadCount.text = if (unread > 0) "$unread unread notices" else "${allList.size} notices"
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chips
    // ─────────────────────────────────────────────────────────────────────
    private fun setupChips() {
        val chips = listOf(
            binding.chipAll       to "all",
            binding.chipUnread    to "unread",
            binding.chipImportant to "important",
            binding.chipPdf       to "pdf"
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener { v ->
                animPress(v)
                activeFilter = filter
                chips.forEach { (c, _) -> setChipStyle(c, false) }
                setChipStyle(chip, true)
                applyFilter()
            }
        }
    }

    private fun setChipStyle(view: TextView, active: Boolean) {
        if (active) {
            view.setBackgroundResource(R.drawable.bg_chip_selected_white)
            view.setTextColor(Color.WHITE)
        } else {
            view.setBackgroundResource(R.drawable.bg_chip_outline_white)
            view.setTextColor(Color.parseColor("#AAFFFFFF"))
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Clicks + animations
    // ─────────────────────────────────────────────────────────────────────
    private fun setupClicks() {
        binding.btnBack.setOnClickListener { v -> animPress(v) { finish() } }
        binding.btnMarkAllRead.setOnClickListener { v -> animPress(v) { markAllRead() } }
    }

    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    private fun runEntranceAnim() {
        binding.layoutHeader.alpha = 0f
        binding.layoutHeader.animate().alpha(1f).setDuration(400).start()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────
    inner class NotifAdapter(
        private val items: List<Announcement>,
        private val onItemClick: (Announcement) -> Unit
    ) : RecyclerView.Adapter<NotifAdapter.VH>() {

        inner class VH(val b: ItemStudentNoticeBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemStudentNoticeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ann = items[position]
            val b   = holder.b

            // Unread dot
            b.viewUnreadDot.visibility = if (!ann.isRead) View.VISIBLE else View.GONE

            // Title bold if unread
            b.tvTitle.text = ann.title
            b.tvTitle.alpha = if (ann.isRead) 0.75f else 1f

            b.tvMessage.text = ann.message

            // Date
            b.tvDate.text = timeAgo(ann.createdAt)

            // Priority badge
            b.tvPriority.visibility =
                if (ann.priority == "important") View.VISIBLE else View.GONE

            // Type icon + accent strip + box bg color
            val (iconRes, iconColor, boxBg) = getTypeStyle(ann.priority)
            b.ivTypeIcon.setImageResource(iconRes)
            try {
                b.ivTypeIcon.setColorFilter(Color.parseColor(iconColor))
                b.viewAccentStrip.setBackgroundColor(Color.parseColor(iconColor))
                b.layoutIconBox.setBackgroundResource(boxBg)
            } catch (_: Exception) {}

            // PDF button
            if (ann.hasPdf) {
                b.btnViewPdf.visibility = View.VISIBLE
                b.btnViewPdf.setOnClickListener { v ->
                    animPress(v) {
                        val intent = android.content.Intent(
                            this@StudentNoticesActivity,
                            AnnouncementPdfActivity::class.java
                        )
                        intent.putExtra("announcementId", ann.id)
                        intent.putExtra("pdfFileName",    ann.pdfFileName)
                        intent.putExtra("chunkCount",     ann.pdfChunkCount)
                        startActivity(intent)
                    }
                }
            } else {
                b.btnViewPdf.visibility = View.GONE
            }

            // Card click → mark read
            b.root.setOnClickListener {
                onItemClick(ann)
                // Expand/collapse message on click
                if (b.tvMessage.maxLines == 2) {
                    b.tvMessage.maxLines = Int.MAX_VALUE
                } else {
                    b.tvMessage.maxLines = 2
                }
            }

            // Staggered entrance anim
            b.root.alpha = 0f
            b.root.translationY = 20f
            b.root.postDelayed({
                b.root.animate().alpha(1f).translationY(0f)
                    .setDuration(300).setInterpolator(DecelerateInterpolator()).start()
            }, position * 60L)
        }

        // Priority → (drawable res, icon tint color, box bg color)
        private fun getTypeStyle(priority: String?): Triple<Int, String, Int> = when (priority) {
            "important" -> Triple(R.drawable.ic_alert_triangle, "#EF4444", R.drawable.bg_menu_icon_amber)
            else        -> Triple(R.drawable.ic_announcement,   "#059669", R.drawable.bg_menu_icon_green)
        }

        private fun timeAgo(ts: Long): String {
            val diff = System.currentTimeMillis() - ts
            val mins  = diff / 60_000
            val hours = diff / 3_600_000
            val days  = diff / 86_400_000
            return when {
                mins  < 1   -> "Hame j"
                mins  < 60  -> "${mins}m pehla"
                hours < 24  -> "${hours}h pehla"
                days  < 7   -> "${days} din pehla"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
            }
        }
    }
}