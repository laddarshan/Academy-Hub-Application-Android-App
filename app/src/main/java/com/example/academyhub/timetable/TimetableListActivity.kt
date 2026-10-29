package com.example.academyhub.timetable

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityTimetableListBinding
import com.example.academyhub.databinding.ItemTimetableBinding
import com.example.academyhub.models.Subject
import com.example.academyhub.models.Timetable
import com.example.academyhub.timetable.AddTimetableActivity
import com.google.firebase.firestore.FirebaseFirestore

class TimetableListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimetableListBinding
    private lateinit var firestore: FirebaseFirestore

    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var semesterId   = ""; private var semesterName = ""
    private var batchId      = ""; private var batchName    = ""

    private val allEntries   = mutableListOf<Timetable>()
    private val filteredList = mutableListOf<Timetable>()

    // Subjects loaded from Firestore — for picker in Add/Edit
    private val subjectsList = mutableListOf<Subject>()

    private lateinit var adapter: TimetableAdapter

    private val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    private var selectedDay = "Monday"

    private val dayChips: List<Pair<TextView, String>> by lazy {
        listOf(
            binding.chipMonday    to "Monday",
            binding.chipTuesday   to "Tuesday",
            binding.chipWednesday to "Wednesday",
            binding.chipThursday  to "Thursday",
            binding.chipFriday    to "Friday",
            binding.chipSaturday  to "Saturday",
            binding.chipSunday    to "Sunday"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimetableListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        batchId      = intent.getStringExtra("batchId")      ?: ""
        batchName    = intent.getStringExtra("batchName")    ?: ""

        binding.tvSubtitle.text = listOf(sessionName, courseName, semesterName, batchName)
            .filter { it.isNotEmpty() }.joinToString("  \u203A  ")

        setupRecycler()
        setupDayChips()
        setupSearch()
        setupSwipeRefresh()

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }
        binding.fabAddTimetable.setOnClickListener {
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(it, "scaleX", 1f, 0.9f, 1f).apply { duration = 150 },
                    ObjectAnimator.ofFloat(it, "scaleY", 1f, 0.9f, 1f).apply { duration = 150 }
                ); start()
            }
            it.postDelayed({ openAdd() }, 120)
        }

        loadSubjects()   // load subjects for this semester first
        loadTimetable()
    }

    // ─── Subjects (from Firestore, for Add/Edit picker) ───────────────────────

    private fun loadSubjects() {
        firestore.collection("Subjects")
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                subjectsList.clear()
                for (doc in docs) subjectsList.add(doc.toObject(Subject::class.java))
                subjectsList.sortBy { it.subjectName }
            }
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecycler() {
        adapter = TimetableAdapter(
            filteredList,
            onEdit   = { openEdit(it) },
            onDelete = { confirmDelete(it) }
        )
        binding.rvTimetable.layoutManager = LinearLayoutManager(this)
        binding.rvTimetable.adapter = adapter
    }

    // ─── Day Chips ────────────────────────────────────────────────────────────

    private fun setupDayChips() {
        dayChips.forEach { (chip, day) ->
            chip.setOnClickListener {
                selectedDay = day
                updateChipUI(chip)
                filterAndShow()
                animateChipTap(chip)
            }
        }
        // Default Monday active
        updateChipUI(binding.chipMonday)
    }

    private fun updateChipUI(activeChip: TextView) {
        dayChips.forEach { (chip, _) ->
            if (chip == activeChip) {
                chip.setBackgroundResource(R.drawable.bg_day_chip_active)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_day_chip_inactive)
                chip.setTextColor(Color.parseColor("#AAFFEE"))
            }
        }
        binding.tvCurrentDay.text = selectedDay
    }

    private fun animateChipTap(v: View) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.86f, 1f).apply { duration = 170 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.86f, 1f).apply { duration = 170 }
            ); start()
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterAndShow()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── SwipeRefresh ─────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadTimetable(fromSwipe = true) }
    }

    // ─── Load Timetable ───────────────────────────────────────────────────────

    private fun loadTimetable(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Timetable")
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("batchId",    batchId)
            .whereEqualTo("status",     "active")
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allEntries.clear()
                for (doc in docs) allEntries.add(doc.toObject(Timetable::class.java))
                allEntries.sortWith(compareBy({ days.indexOf(it.day) }, { it.startTime }))
                filterAndShow()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Filter ───────────────────────────────────────────────────────────────

    private fun filterAndShow() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(allEntries.filter { t ->
            t.day == selectedDay &&
                    (q.isEmpty() || t.subject.lowercase().contains(q) ||
                            t.teacherName.lowercase().contains(q) ||
                            t.classroom.lowercase().contains(q))
        })
        filteredList.sortBy { it.startTime }

        val count = filteredList.size
        binding.tvEntryCount.text = "$count ${if (count == 1) "class" else "classes"}"

        if (filteredList.isEmpty()) {
            binding.rvTimetable.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            val emptyMsg = if (q.isNotEmpty()) "No results for $q" else "No classes for $selectedDay"
            binding.tvEmptyMessage.text = emptyMsg
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvTimetable.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
        binding.rvTimetable.alpha = 0f
        binding.rvTimetable.animate().alpha(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── Add / Edit ───────────────────────────────────────────────────────────

    private fun openAdd() {
        startActivity(Intent(this, AddTimetableActivity::class.java).apply {
            putExtra("sessionId",             sessionId)
            putExtra("sessionName",           sessionName)
            putExtra("prefilledCourseId",     courseId)
            putExtra("prefilledCourseName",   courseName)
            putExtra("prefilledSemesterId",   semesterId)
            putExtra("prefilledSemesterName", semesterName)
            putExtra("prefilledBatchId",      batchId)
            putExtra("prefilledBatchName",    batchName)
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun openEdit(t: Timetable) {
        startActivity(Intent(this, AddTimetableActivity::class.java).apply {
            putExtra("isEdit",      true)
            putExtra("timetableId", t.id)
        })
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private fun confirmDelete(t: Timetable) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("\"${t.subject}\" delete karvasho?")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                firestore.collection("Timetable").document(t.id)
                    .update("status", "deleted")
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                        loadTimetable()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); loadTimetable() }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    inner class TimetableAdapter(
        private val list: MutableList<Timetable>,
        private val onEdit:   (Timetable) -> Unit,
        private val onDelete: (Timetable) -> Unit
    ) : RecyclerView.Adapter<TimetableAdapter.VH>() {

        inner class VH(val b: ItemTimetableBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(p: ViewGroup, v: Int) =
            VH(ItemTimetableBinding.inflate(LayoutInflater.from(p.context), p, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = list[pos]; val b = h.b

            b.tvSubject.text   = t.subject
            b.tvTeacher.text   = t.teacherName
            b.tvClassroom.text = if (t.type == "online") "Online Class" else t.classroom.ifEmpty { "Room TBA" }
            b.tvStartTime.text = t.startTime
            b.tvEndTime.text   = t.endTime
            b.tvDuration.text  = "${t.duration}m"
            b.tvBatchCourse.text = "${t.courseName} - ${t.batchName}"

            // type chip + accent bar color
            val typeKey = t.type.lowercase()
            val barHex  = when (typeKey) { "online" -> "#1565C0"; "exam" -> "#E65100"; "practical" -> "#6A1B9A"; else -> "#075E54" }
            val chipBg  = when (typeKey) { "online" -> R.drawable.bg_type_online; "exam" -> R.drawable.bg_type_exam; else -> R.drawable.bg_type_chip }
            val chipHex = barHex
            b.viewAccentBar.setBackgroundColor(Color.parseColor(barHex))
            b.tvType.setBackgroundResource(chipBg)
            b.tvType.setTextColor(Color.parseColor(chipHex))
            b.tvType.text = t.type.replaceFirstChar { it.titlecase() }

            // stagger entrance
            b.root.alpha = 0f; b.root.translationY = 18f
            b.root.animate().alpha(1f).translationY(0f)
                .setDuration(240).setStartDelay(pos * 45L)
                .setInterpolator(DecelerateInterpolator()).start()

            // popup menu
            b.btnMore.setOnClickListener { v ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.78f, 1f).apply { duration = 140 },
                        ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.78f, 1f).apply { duration = 140 }
                    ); start()
                }
                val popup = PopupMenu(v.context, v)
                popup.inflate(R.menu.menu_timetable_options)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_edit   -> { onEdit(t);   true }
                        R.id.action_delete -> { onDelete(t); true }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}