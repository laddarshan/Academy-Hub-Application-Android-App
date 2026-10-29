package com.example.academyhub.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.announcements.AnnouncementAdapter
import com.example.academyhub.announcements.CreateAnnouncementActivity
import com.example.academyhub.databinding.FragmentAnnouncementsBinding
import com.example.academyhub.models.Announcement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AnnouncementsFragment : Fragment() {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()

    private val allAnnouncements = mutableListOf<Announcement>()
    private val filteredList     = mutableListOf<Announcement>()
    private val courseNames      = mutableListOf<String>()
    private var selectedFilter   = "ALL"
    private var animationPlayed  = false
    private var dataLoaded       = false
    private val courseIdNameMap  = mutableMapOf<String, String>()

    private lateinit var adapter: AnnouncementAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        if (!animationPlayed) { animationPlayed = true; runEntranceAnimation() }

        setupSendButton()
        setupSwipeRefresh()
        loadAnnouncements()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = AnnouncementAdapter(
            list     = filteredList,
            isAdmin  = true,
            onDelete = { ann -> confirmDelete(ann) },
            onEdit   = { ann -> openEdit(ann) }
        )
        binding.rvAnnouncements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAnnouncements.adapter       = adapter
    }

    // ── Send button — opens CreateAnnouncementActivity directly ──────────────

    private fun setupSendButton() {
        binding.btnSendAnnouncement.setOnClickListener { v ->
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.95f, 1f).apply { duration = 200 },
                    ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.95f, 1f).apply { duration = 200 }
                )
                start()
            }
            v.postDelayed({
                startActivity(Intent(requireContext(), CreateAnnouncementActivity::class.java))
            }, 120)
        }
    }

    // ── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadAnnouncements(fromSwipe = true) }
    }

    // ── Load Firestore ────────────────────────────────────────────────────────

    private fun loadAnnouncements(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        allAnnouncements.clear()
        courseNames.clear()

        db.collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snap ->
                if (_binding == null) return@addOnSuccessListener

                val courseSet = linkedSetOf<String>()

                for (doc in snap) {
                    val ann = Announcement(
                        id            = doc.id,
                        title         = doc.getString("title")         ?: "",
                        message       = doc.getString("message")       ?: "",
                        priority      = doc.getString("priority")      ?: "normal",
                        sentTo        = (doc.get("sentTo") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf(),
                        hasPdf        = doc.getBoolean("hasPdf")       ?: false,
                        pdfFileName   = doc.getString("pdfFileName")   ?: "",
                        pdfChunkCount = (doc.getLong("pdfChunkCount")  ?: 0L).toInt(),
                        createdBy     = doc.getString("createdBy")     ?: "",
                        createdByName = doc.getString("createdByName") ?: "",
                        createdAt     = doc.getLong("createdAt")       ?: 0L
                    )
                    allAnnouncements.add(ann)
                    ann.sentTo.forEach { sentId -> if (sentId != "all") courseSet.add(sentId) }
                }

                db.collection("Courses").get()
                    .addOnSuccessListener { cSnap ->
                        if (_binding == null) return@addOnSuccessListener

                        val courseIdToName = mutableMapOf<String, String>()
                        for (c in cSnap) {
                            courseIdToName[c.id] = c.getString("courseName") ?: c.id
                        }

                        val courseNameSet = linkedSetOf<String>()
                        allAnnouncements.forEach { ann ->
                            ann.sentTo.forEach { sid ->
                                if (sid != "all") courseNameSet.add(courseIdToName[sid] ?: sid)
                            }
                        }
                        courseNames.addAll(courseNameSet.sorted())
                        courseIdNameMap.clear()
                        courseIdNameMap.putAll(courseIdToName)

                        binding.progressBar.visibility    = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        dataLoaded = true
                        buildCourseTabs()
                        applyFilter(selectedFilter)
                    }
                    .addOnFailureListener {
                        binding.progressBar.visibility    = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        dataLoaded = true
                        buildCourseTabs()
                        applyFilter(selectedFilter)
                    }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(context, "Load failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    private fun openEdit(ann: Announcement) {
        startActivity(
            Intent(requireContext(), CreateAnnouncementActivity::class.java).apply {
                putExtra("isEditMode",      true)
                putExtra("announcementId",  ann.id)
            }
        )
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private fun confirmDelete(ann: Announcement) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Announcement")
            .setMessage("Are you sure you want to delete \"${ann.title}\"?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                db.collection("announcements").document(ann.id).delete()
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        // Remove locally — no need to reload from Firestore
                        allAnnouncements.remove(ann)
                        applyFilter(selectedFilter)
                        Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Course filter tabs ────────────────────────────────────────────────────

    private fun buildCourseTabs() {
        binding.llCourseTabs.removeAllViews()
        val tabs = listOf("ALL") + courseNames

        tabs.forEach { name ->
            val chip = TextView(requireContext()).apply {
                text = name; textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(14), dp(7), dp(14), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                isClickable = true; isFocusable = true

                if (name == selectedFilter) {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.parseColor("#075E54"))
                } else {
                    setBackgroundColor(Color.parseColor("#22FFFFFF"))
                    setTextColor(Color.parseColor("#CCFFFFFF"))
                }

                setOnClickListener {
                    selectedFilter = name
                    buildCourseTabs()
                    applyFilter(name)
                    animate().scaleX(1.14f).scaleY(1.14f).setDuration(110).withEndAction {
                        animate().scaleX(1f).scaleY(1f)
                            .setDuration(160).setInterpolator(OvershootInterpolator()).start()
                    }.start()
                }
            }
            binding.llCourseTabs.addView(chip)
        }
    }

    // ── Apply filter ──────────────────────────────────────────────────────────

    private fun applyFilter(filter: String) {
        filteredList.clear()

        if (filter == "ALL") {
            filteredList.addAll(allAnnouncements)
        } else {
            val courseId = courseIdNameMap.entries.firstOrNull { it.value == filter }?.key
            filteredList.addAll(allAnnouncements.filter { ann ->
                ann.sentTo.contains("all") ||
                        (courseId != null && ann.sentTo.contains(courseId)) ||
                        ann.sentTo.contains(filter)
            })
        }

        adapter.notifyDataSetChanged()

        if (filteredList.isEmpty()) {
            binding.layoutNoAnnouncements.visibility = View.VISIBLE
            binding.rvAnnouncements.visibility       = View.GONE
        } else {
            binding.layoutNoAnnouncements.visibility = View.GONE
            binding.rvAnnouncements.visibility       = View.VISIBLE
        }
    }

    // ── Refresh on resume (after create/edit) ─────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (dataLoaded) loadAnnouncements()
        _binding?.root?.let {
            ObjectAnimator.ofFloat(it, "alpha", 0.85f, 1f).apply {
                duration = 250; interpolator = DecelerateInterpolator(); start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.root?.let {
            ObjectAnimator.ofFloat(it, "alpha", 1f, 0.85f).apply { duration = 150; start() }
        }
    }

    // ── Entrance animation ────────────────────────────────────────────────────

    private fun runEntranceAnimation() {
        binding.btnSendAnnouncement.apply { alpha = 0f; translationY = -60f }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnSendAnnouncement, "alpha", 0f, 1f).apply { duration = 450 },
                ObjectAnimator.ofFloat(binding.btnSendAnnouncement, "translationY", -60f, 0f).apply {
                    duration = 450; interpolator = OvershootInterpolator(0.8f)
                }
            ); start()
        }
        binding.rvAnnouncements.apply { alpha = 0f; translationY = 50f }
        ObjectAnimator.ofFloat(binding.rvAnnouncements, "alpha", 0f, 1f).apply {
            duration = 500; startDelay = 300; start()
        }
        ObjectAnimator.ofFloat(binding.rvAnnouncements, "translationY", 50f, 0f).apply {
            duration = 500; startDelay = 300; interpolator = DecelerateInterpolator(); start()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}