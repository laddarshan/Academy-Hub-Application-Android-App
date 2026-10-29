package com.example.academyhub.announcements

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAnnouncementHistoryBinding
import com.example.academyhub.models.Announcement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AnnouncementHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnnouncementHistoryBinding
    private val db = FirebaseFirestore.getInstance()

    private val allList      = mutableListOf<Announcement>()
    private val filteredList = mutableListOf<Announcement>()
    private lateinit var adapter: AnnouncementAdapter

    // null=All, "all"=AllStudents, courseId=specific
    private var selectedFilter: String? = null
    private val filterChips = mutableListOf<Pair<String?, TextView>>()
    private val coursesMap  = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AnnouncementAdapter(
            list     = filteredList,
            isAdmin  = true,
            onDelete = { confirmDelete(it) },
            onEdit   = { openEdit(it) }
        )
        binding.rvAnnouncements.layoutManager = LinearLayoutManager(this)
        binding.rvAnnouncements.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        setupSearch()
        setupSwipeRefresh()
        loadCoursesFirst()
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })
        binding.btnClear.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── Swipe Refresh ─────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadAnnouncements(fromSwipe = true) }
    }

    // ─── Courses → build chips → load announcements ─────────────────────────────

    private fun loadCoursesFirst() {
        binding.progressBar.visibility = View.VISIBLE
        db.collection("Courses").whereEqualTo("active", true).get()
            .addOnSuccessListener { snap ->
                coursesMap.clear()
                for (doc in snap) {
                    val name = doc.getString("courseName") ?: continue
                    coursesMap[doc.id] = name
                }
                buildFilterChips()
                loadAnnouncements()
            }
            .addOnFailureListener { loadAnnouncements() }
    }

    // ─── IMPORTANT 2: Filter Chips ──────────────────────────────────────────────

    private fun buildFilterChips() {
        binding.llFilterChips.removeAllViews()
        filterChips.clear()

        addChip("All", null)
        addChip("All Students", "all")
        for ((id, name) in coursesMap) addChip(name, id)

        updateChipState(null)
    }

    private fun addChip(label: String, filterValue: String?) {
        val dp = resources.displayMetrics.density
        val chip = TextView(this).apply {
            text = label
            textSize  = 12f
            typeface  = Typeface.create(typeface, Typeface.BOLD)
            setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
            gravity   = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (32 * dp).toInt())
            lp.marginEnd = (8 * dp).toInt()
            layoutParams = lp
            isClickable = true; isFocusable = true
            setOnClickListener {
                selectedFilter = filterValue
                updateChipState(filterValue)
                applyFilter()
                // Scale zoom on chip click (Interaction requirement)
                ObjectAnimator.ofFloat(this, "scaleX", 1f, 1.18f, 1f).apply { duration = 200; start() }
                ObjectAnimator.ofFloat(this, "scaleY", 1f, 1.18f, 1f).apply { duration = 200; start() }
            }
        }
        filterChips.add(filterValue to chip)
        binding.llFilterChips.addView(chip)
    }

    private fun updateChipState(selected: String?) {
        for ((value, chip) in filterChips) {
            if (value == selected) {
                chip.setBackgroundResource(R.drawable.bg_day_chip_active)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_day_chip_inactive)
                chip.setTextColor(Color.parseColor("#AAFFEE"))
            }
        }
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    private fun loadAnnouncements(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allList.clear()
                for (doc in snap) {
                    val ann = doc.toObject(Announcement::class.java); ann.id = doc.id; allList.add(ann)
                }
                // ValueAnimator count up
                ValueAnimator.ofInt(0, allList.size).apply {
                    duration = 500
                    addUpdateListener { binding.tvCount.text = "${it.animatedValue} announcements" }
                    start()
                }
                applyFilter()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── IMPORTANT 2: Filter announcements by chip ─────────────────────────────

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(allList.filter { ann ->
            val matchFilter = when (selectedFilter) {
                null  -> true                                                    // All — show everything
                "all" -> ann.sentTo.contains("all")                              // All Students chip
                else  -> ann.sentTo.contains(selectedFilter) || ann.sentTo.contains("all")  // course chip
            }
            val matchSearch = q.isEmpty() ||
                    ann.title.lowercase().contains(q) ||
                    ann.message.lowercase().contains(q)
            matchFilter && matchSearch
        })

        val isEmpty = filteredList.isEmpty()
        binding.layoutEmpty.visibility     = if (isEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility    = if (isEmpty) View.GONE    else View.VISIBLE
        binding.tvEmpty.text = if (q.isNotEmpty()) "No results for \"$q\"" else "No announcements here"

        adapter.notifyDataSetChanged()

        // Fade list animation
        binding.rvAnnouncements.alpha = 0f
        binding.rvAnnouncements.animate().alpha(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── Edit / Delete ─────────────────────────────────────────────────────────

    private fun openEdit(ann: Announcement) {
        startActivity(Intent(this, CreateAnnouncementActivity::class.java).apply {
            putExtra("isEditMode", true)
            putExtra("announcementId", ann.id)
        })
    }

    private fun confirmDelete(ann: Announcement) {
        AlertDialog.Builder(this)
            .setTitle("Delete Announcement")
            .setMessage("Are you sure you want to delete \"${ann.title}\"?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                db.collection("announcements").document(ann.id).delete()
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        // Instant local remove — no wait for reload
                        allList.remove(ann)
                        applyFilter()
                        // ValueAnimator update count
                        ValueAnimator.ofInt(allList.size + 1, allList.size).apply {
                            duration = 400
                            addUpdateListener { binding.tvCount.text = "${it.animatedValue} announcements" }
                            start()
                        }
                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); loadAnnouncements() }
}