package com.example.academyhub.announcements

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentNotificationsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentNotificationsBinding
    private val db    = FirebaseFirestore.getInstance()
    private val auth  = FirebaseAuth.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    data class NotifItem(
        val id:             String  = "",
        val title:          String  = "",
        val body:           String  = "",
        val type:           String  = "announcement",
        val priority:       String  = "normal",
        val announcementId: String  = "",
        var isRead:         Boolean = false,
        val createdAt:      Long    = 0L,
        val hasPdf:         Boolean = false,
        val pdfFileName:    String  = "",
        val pdfChunkCount:  Int     = 0
    )

    private val allItems     = mutableListOf<NotifItem>()
    private val displayItems = mutableListOf<NotifItem>()
    private var filterMode   = "all"
    private var searchQuery  = ""
    private var courseId     = ""

    private lateinit var adapter: NotifAdapter

    // ─────────────────────────────────────────────────────
    //  Adapter
    // ─────────────────────────────────────────────────────
    inner class NotifAdapter : RecyclerView.Adapter<NotifAdapter.VH>() {

        inner class VH(val card: CardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification, parent, false) as CardView
            return VH(v)
        }

        override fun getItemCount() = displayItems.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = displayItems[position]
            val v    = holder.card

            val iconBox = v.findViewById<FrameLayout>(R.id.notifIconBox)
            val icon    = v.findViewById<ImageView>(R.id.notifIcon)

            // Icon + color per type
            val (iconRes, bgRes, tintHex) = when {
                item.priority == "important" -> Triple(
                    R.drawable.ic_alert_triangle, R.drawable.bg_menu_icon_amber, "#D97706")
                item.type == "exam"   -> Triple(
                    R.drawable.ic_file_text, R.drawable.bg_menu_icon_rose, "#E11D48")
                item.type == "fees"   -> Triple(
                    R.drawable.ic_rupee, R.drawable.bg_menu_icon_green, "#059669")
                item.type == "result" -> Triple(
                    R.drawable.ic_bar_chart, R.drawable.bg_menu_icon_violet, "#7C3AED")
                else -> Triple(
                    R.drawable.ic_bell, R.drawable.bg_menu_icon_sky, "#0284C7")
            }
            iconBox.setBackgroundResource(bgRes)
            icon.setImageResource(iconRes)
            icon.setColorFilter(Color.parseColor(tintHex))

            val tvTitle = v.findViewById<TextView>(R.id.tvNotifTitle)
            val tvBody  = v.findViewById<TextView>(R.id.tvNotifBody)
            val tvTime  = v.findViewById<TextView>(R.id.tvNotifTime)

            tvTitle.text = item.title
            tvBody.text  = item.body
            tvTime.text  = timeAgo(item.createdAt)

            // Important badge
            v.findViewById<TextView>(R.id.tvImportantBadge).visibility =
                if (item.priority == "important") View.VISIBLE else View.GONE

            // Unread dot
            val dot = v.findViewById<View>(R.id.unreadDot)
            dot.visibility = if (!item.isRead) View.VISIBLE else View.GONE

            // Unread bg highlight
            if (!item.isRead) {
                val isDark = (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                v.setCardBackgroundColor(
                    if (isDark) Color.parseColor("#0D1C2B") else Color.parseColor("#F0FDF9")
                )
            } else {
                v.setCardBackgroundColor(surfaceColor())
            }

            // ── PDF Card — WhatsApp style ──────────────────────────────────
            val pdfCard     = v.findViewById<View?>(R.id.cardPdfAttachment)
            val tvPdfName   = v.findViewById<TextView?>(R.id.tvPdfFileName)
            val tvPdfPages  = v.findViewById<TextView?>(R.id.tvPdfPages)

            if (item.hasPdf && item.pdfFileName.isNotEmpty() && pdfCard != null) {
                pdfCard.visibility = View.VISIBLE
                tvPdfName?.text    = item.pdfFileName
                tvPdfPages?.text   = "PDF • Tap to open"

                pdfCard.setOnClickListener {
                    animPress(it) { openPdf(item) }
                }
            } else {
                pdfCard?.visibility = View.GONE
            }

            // ── Title click → full message dialog ─────────────────────────
            tvTitle.setOnClickListener {
                showFullMessageDialog(item)
            }

            // ── Body click → expand/collapse text ─────────────────────────
            // Default collapsed to 3 lines
            tvBody.maxLines = 3
            tvBody.setOnClickListener {
                if (tvBody.maxLines == 3) {
                    tvBody.maxLines = Int.MAX_VALUE
                } else {
                    tvBody.maxLines = 3
                }
            }

            // ── Row click = mark as read ───────────────────────────────────
            v.setOnClickListener {
                if (!item.isRead) {
                    item.isRead = true
                    dot.animate().scaleX(0f).scaleY(0f).setDuration(180).withEndAction {
                        dot.visibility = View.GONE
                    }.start()
                    v.setCardBackgroundColor(surfaceColor())
                    markAsReadFirestore(item)
                    updateCountDisplay()
                }
                animPress(v) {}
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  onCreate
    // ─────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = NotifAdapter()
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter       = adapter
        binding.rvNotifications.itemAnimator  = null

        setupSwipeRefresh()
        setupSearch()
        setupClicks()
        loadData()
    }

    // ─────────────────────────────────────────────────────
    //  SwipeRefresh
    // ─────────────────────────────────────────────────────
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#25D366"),
            Color.parseColor("#128C7E")
        )
        binding.swipeRefresh.setOnRefreshListener { loadData(fromSwipe = true) }
    }

    // ─────────────────────────────────────────────────────
    //  Search
    // ─────────────────────────────────────────────────────
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.btnClear.visibility =
                    if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSearch()
            }
        })
        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    // ─────────────────────────────────────────────────────
    //  Load data
    // ─────────────────────────────────────────────────────
    private fun loadData(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        binding.tvCount.text = "Loading..."

        courseId = prefs.getString("studentCourseId","") ?: ""
        val uid  = auth.currentUser?.uid ?: ""

        if (courseId.isEmpty()) {
            val docId = prefs.getString("studentDocId","") ?: ""
            if (docId.isNotEmpty()) {
                db.collection("Students").document(docId).get()
                    .addOnSuccessListener { doc ->
                        courseId = doc.getString("courseId") ?: ""
                        prefs.edit().putString("studentCourseId", courseId).apply()
                        fetchAnnouncements(uid, fromSwipe)
                    }
                    .addOnFailureListener { fetchAnnouncements(uid, fromSwipe) }
            } else fetchAnnouncements(uid, fromSwipe)
        } else fetchAnnouncements(uid, fromSwipe)
    }

    private fun fetchAnnouncements(uid: String, fromSwipe: Boolean) {
        // Load which announcements user has read
        db.collection("UserNotifications").document(uid)
            .collection("notifications").get()
            .addOnSuccessListener { readSnap ->

                val readIds = readSnap.documents
                    .filter { it.getBoolean("isRead") == true }
                    .mapNotNull { it.getString("announcementId") }
                    .toSet()

                db.collection("announcements")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(60)
                    .get()
                    .addOnSuccessListener { docs ->
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false

                        allItems.clear()
                        docs.forEach { doc ->
                            @Suppress("UNCHECKED_CAST")
                            val sentTo = doc.get("sentTo") as? List<String> ?: emptyList()
                            // Show if sentTo "all" or this student's course
                            val isForMe = sentTo.contains("all") ||
                                    sentTo.contains(courseId) ||
                                    courseId.isEmpty()
                            if (!isForMe) return@forEach

                            allItems.add(NotifItem(
                                id             = doc.id,
                                title          = doc.getString("title")         ?: "",
                                body           = doc.getString("message")       ?: "",
                                type           = doc.getString("type")          ?: "announcement",
                                priority       = doc.getString("priority")      ?: "normal",
                                announcementId = doc.id,
                                isRead         = readIds.contains(doc.id),
                                createdAt      = doc.getLong("createdAt")       ?: 0L,
                                hasPdf         = doc.getBoolean("hasPdf")       ?: false,
                                pdfFileName    = doc.getString("pdfFileName")   ?: "",
                                pdfChunkCount  = (doc.getLong("pdfChunkCount")  ?: 0L).toInt()
                            ))
                        }

                        updateCountDisplay()
                        applyFilterAndSearch()
                        animateListIn()
                    }
                    .addOnFailureListener {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        showEmpty(true)
                    }
            }
            .addOnFailureListener {
                // No read records yet — show all as unread
                fetchAnnouncements(uid, fromSwipe)
            }
    }

    // ─────────────────────────────────────────────────────
    //  Filter + Search
    // ─────────────────────────────────────────────────────
    private fun applyFilterAndSearch() {
        var filtered: List<NotifItem> = when (filterMode) {
            "unread"    -> allItems.filter { !it.isRead }
            "important" -> allItems.filter { it.priority == "important" }
            else        -> allItems.toList()
        }

        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) || it.body.lowercase().contains(q)
            }
        }

        displayItems.clear()
        displayItems.addAll(filtered)
        adapter.notifyDataSetChanged()
        showEmpty(displayItems.isEmpty())
    }

    private fun animateListIn() {
        binding.rvNotifications.post {
            val lm = binding.rvNotifications.layoutManager as? LinearLayoutManager ?: return@post
            val last = lm.findLastVisibleItemPosition()
            for (i in 0..last) {
                val child = lm.findViewByPosition(i) ?: continue
                child.alpha = 0f
                child.translationY = 18f
                child.animate()
                    .alpha(1f).translationY(0f)
                    .setDuration(260)
                    .setStartDelay(i * 50L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  Mark as read
    // ─────────────────────────────────────────────────────
    private fun markAsReadFirestore(item: NotifItem) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("UserNotifications").document(uid)
            .collection("notifications")
            .whereEqualTo("announcementId", item.announcementId)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    db.collection("UserNotifications").document(uid)
                        .collection("notifications").add(hashMapOf(
                            "announcementId" to item.announcementId,
                            "isRead"         to true,
                            "title"          to item.title,
                            "type"           to item.type,
                            "priority"       to item.priority,
                            "createdAt"      to System.currentTimeMillis()
                        ))
                } else {
                    docs.documents[0].reference.update("isRead", true)
                }
            }
    }

    // ─────────────────────────────────────────────────────
    //  Mark ALL read
    // ─────────────────────────────────────────────────────
    private fun markAllRead() {
        val uid    = auth.currentUser?.uid ?: return
        val unread = allItems.filter { !it.isRead }
        if (unread.isEmpty()) return

        val batch = db.batch()
        unread.forEach { item ->
            item.isRead = true
            val ref = db.collection("UserNotifications").document(uid)
                .collection("notifications").document()
            batch.set(ref, hashMapOf(
                "announcementId" to item.announcementId,
                "isRead"         to true,
                "createdAt"      to System.currentTimeMillis()
            ))
        }
        batch.commit().addOnSuccessListener {
            updateCountDisplay()
            adapter.notifyDataSetChanged()
        }
    }

    // ─────────────────────────────────────────────────────
    //  Filter chips
    // ─────────────────────────────────────────────────────
    private fun setFilter(mode: String) {
        filterMode = mode

        listOf(
            binding.chipAll       to "all",
            binding.chipUnread    to "unread",
            binding.chipImportant to "important"
        ).forEach { (chip, chipMode) ->
            val active = chipMode == mode
            chip.setBackgroundResource(
                if (active) R.drawable.bg_chip_selected_white
                else        R.drawable.bg_chip_outline_white
            )
            chip.setTextColor(
                if (active) Color.parseColor("#075E54")
                else        Color.parseColor("#CCFFFFFF")
            )
        }
        applyFilterAndSearch()
    }

    // ─────────────────────────────────────────────────────
    //  Open PDF from Firestore chunks
    // ─────────────────────────────────────────────────────
    private fun openPdf(item: NotifItem) {
        if (item.pdfChunkCount == 0) {
            android.widget.Toast.makeText(this, "PDF not available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val chunks = mutableMapOf<Int, String>()
                for (i in 0 until item.pdfChunkCount) {
                    val doc = db.collection("announcements")
                        .document(item.announcementId)
                        .collection("pdfChunks")
                        .document("chunk_$i")
                        .get().await()
                    chunks[i] = doc.getString("data") ?: ""
                }

                val fullB64  = (0 until item.pdfChunkCount).joinToString("") { chunks[it] ?: "" }
                val pdfBytes = Base64.decode(fullB64, Base64.NO_WRAP)
                val file     = File(cacheDir, item.pdfFileName.ifEmpty { "attachment.pdf" })
                file.writeBytes(pdfBytes)

                val uri = FileProvider.getUriForFile(this@NotificationsActivity, "${packageName}.provider", file)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivity(viewIntent) }
                    catch (e: Exception) {
                        android.widget.Toast.makeText(this@NotificationsActivity,
                            "Please install a PDF viewer app", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    android.widget.Toast.makeText(this@NotificationsActivity,
                        "Error loading PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────
    //  Full message dialog — tap on title opens this
    // ─────────────────────────────────────────────────────
    private fun showFullMessageDialog(item: NotifItem) {
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(item.body)
            .setPositiveButton("Close", null)
            .show()
    }

    // ─────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────
    private fun updateCountDisplay() {
        val total  = allItems.size
        val unread = allItems.count { !it.isRead }
        binding.tvCount.text = when {
            unread > 0 -> "$unread unread · $total total"
            total > 0  -> "All read ✓ · $total total"
            else       -> "No notifications"
        }
    }

    private fun showEmpty(empty: Boolean) {
        binding.layoutEmpty.visibility  = if (empty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility = if (empty) View.GONE    else View.VISIBLE
        binding.tvEmpty.text = if (searchQuery.isNotEmpty())
            "No results for \"$searchQuery\""
        else
            "No notifications yet"
    }

    private fun surfaceColor(): Int {
        val ta = obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSurface))
        val c  = ta.getColor(0, Color.WHITE)
        ta.recycle()
        return c
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener       { v -> animPress(v) { finish() } }
        binding.btnMarkAllRead.setOnClickListener { v -> animPress(v) { markAllRead() } }
        binding.chipAll.setOnClickListener       { v -> animPress(v) { setFilter("all") } }
        binding.chipUnread.setOnClickListener    { v -> animPress(v) { setFilter("unread") } }
        binding.chipImportant.setOnClickListener { v -> animPress(v) { setFilter("important") } }
    }

    private fun animPress(view: View, onComplete: () -> Unit = {}) {
        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { onComplete() }.start()
        }.start()
    }

    private fun timeAgo(ts: Long): String {
        if (ts == 0L) return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000      -> "Just now"
            diff < 3_600_000   -> "${diff / 60_000} min ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            diff < 172_800_000 -> "Yesterday"
            else               -> "${diff / 86_400_000}d ago"
        }
    }
}