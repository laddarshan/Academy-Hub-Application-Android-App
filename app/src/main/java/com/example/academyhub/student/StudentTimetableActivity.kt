package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentTimetableBinding
import com.example.academyhub.databinding.ItemTimetableBinding
import com.example.academyhub.models.Student
import com.example.academyhub.models.Timetable
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentTimetableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentTimetableBinding
    private val db    = FirebaseFirestore.getInstance()
    private val prefs by lazy { getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) }

    private var courseId     = ""
    private var courseName   = ""
    private var batchId      = ""
    private var batchName    = ""
    private var semesterId   = ""
    private var semesterName = ""
    private var yearName     = ""

    private val allEntries   = mutableListOf<Timetable>()
    private val filteredList = mutableListOf<Timetable>()

    private lateinit var adapter: StudentTimetableAdapter

    private val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    private var selectedDay = "Monday"

    private val dayChips: List<Pair<LinearLayout, String>> by lazy {
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

    companion object {
        fun start(context: Context, student: Student) {
            Intent(context, StudentTimetableActivity::class.java).apply {
                putExtra("courseId",     student.courseId)
                putExtra("courseName",   student.courseName)
                putExtra("batchId",      student.batchId)
                putExtra("batchName",    student.batchName)
                putExtra("semesterId",   student.semesterId)
                putExtra("semesterName", student.semesterName)
                putExtra("yearName",     student.yearName)
                context.startActivity(this)
            }
        }

        fun start(context: Context) {
            context.startActivity(Intent(context, StudentTimetableActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentTimetableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        batchId      = intent.getStringExtra("batchId")      ?: ""
        batchName    = intent.getStringExtra("batchName")    ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""

        // Default selected day = today
        val cal = Calendar.getInstance()
        selectedDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Monday"
            Calendar.TUESDAY   -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY  -> "Thursday"
            Calendar.FRIDAY    -> "Friday"
            Calendar.SATURDAY  -> "Saturday"
            else               -> "Monday"
        }

        setupRecycler()
        setupDayChips()
        setupSearch()
        setupSwipeRefresh()

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        binding.btnRefresh.setOnClickListener {
            it.animate().rotation(it.rotation + 360f).setDuration(500).start()
            loadTimetable(fromSwipe = true)
        }

        if (courseId.isEmpty()) loadStudentThenData()
        else loadTimetable()
    }

    // ─── Subtitle ─────────────────────────────────────────────────────────────

    private fun setupSubtitle() {
        binding.tvSubtitle.text = listOf(courseName, yearName, semesterName, batchName)
            .filter { it.isNotEmpty() }.joinToString("  ›  ")
    }

    // ─── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecycler() {
        adapter = StudentTimetableAdapter(filteredList)
        binding.rvTimetable.layoutManager = LinearLayoutManager(this)
        binding.rvTimetable.adapter = adapter
    }

    // ─── Day Chips with dates ─────────────────────────────────────────────────

    private fun setupDayChips() {
        // Fill date numbers
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (dow == Calendar.SUNDAY) -6 else -(dow - Calendar.MONDAY)
        cal.add(Calendar.DAY_OF_YEAR, offset)

        val dateFmt = SimpleDateFormat("d", Locale.getDefault())
        val dateViews = listOf(
            binding.tvMonDate, binding.tvTueDate, binding.tvWedDate,
            binding.tvThuDate, binding.tvFriDate, binding.tvSatDate, binding.tvSunDate
        )
        dateViews.forEach { tv ->
            tv.text = dateFmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Chip clicks
        dayChips.forEach { (chip, day) ->
            chip.setOnClickListener {
                selectedDay = day
                updateChipUI(chip)
                filterAndShow()
                animateChipTap(chip)
            }
        }

        // Set default active chip
        val todayChip = dayChips.firstOrNull { it.second == selectedDay }?.first
            ?: binding.chipMonday
        updateChipUI(todayChip)
    }

    private fun updateChipUI(activeChip: LinearLayout) {
        dayChips.forEach { (chip, _) ->
            if (chip == activeChip) {
                chip.setBackgroundResource(R.drawable.bg_day_chip_active)
                // White text for active
                (chip.getChildAt(0) as? TextView)?.setTextColor(Color.WHITE)
                (chip.getChildAt(1) as? TextView)?.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_day_chip_inactive)
                // Muted text for inactive
                (chip.getChildAt(0) as? TextView)?.setTextColor(Color.parseColor("#AAFFEE"))
                (chip.getChildAt(1) as? TextView)?.setTextColor(Color.parseColor("#AAFFEE"))
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

    // ─── Load student if no courseId ──────────────────────────────────────────

    private fun loadStudentThenData() {
        binding.progressBar.visibility = View.VISIBLE
        val docId = prefs.getString("studentDocId", "") ?: ""
        if (docId.isNotEmpty()) {
            db.collection("Students").document(docId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        courseId     = doc.getString("courseId")     ?: ""
                        courseName   = doc.getString("courseName")   ?: ""
                        batchId      = doc.getString("batchId")      ?: ""
                        batchName    = doc.getString("batchName")    ?: ""
                        semesterId   = doc.getString("semesterId")   ?: ""
                        semesterName = doc.getString("semesterName") ?: ""
                        yearName     = doc.getString("yearName")     ?: ""
                        setupSubtitle()
                        loadTimetable()
                    } else {
                        binding.progressBar.visibility = View.GONE
                        showEmpty("Student data not found")
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    showEmpty("Error: ${e.message}")
                }
        } else {
            binding.progressBar.visibility = View.GONE
            showEmpty("Student ID not found")
        }
    }

    // ─── Load Timetable ───────────────────────────────────────────────────────

    private fun loadTimetable(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        var query = db.collection("Timetable")
            .whereEqualTo("status", "active")
            .whereEqualTo("day", selectedDay)

        if (courseId.isNotEmpty()) query = query.whereEqualTo("courseId", courseId)
        if (batchId.isNotEmpty())  query = query.whereEqualTo("batchId",  batchId)

        query.get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allEntries.clear()
                for (doc in docs) allEntries.add(doc.toObject(Timetable::class.java))
                allEntries.sortBy { it.startTime }
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
            binding.tvEmptyMessage.text =
                if (q.isNotEmpty()) "No results for \"$q\"" else "No classes for $selectedDay"
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvTimetable.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
        binding.rvTimetable.alpha = 0f
        binding.rvTimetable.animate()
            .alpha(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun showEmpty(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.rvTimetable.visibility = View.GONE
        binding.tvEmptyMessage.text = msg
        binding.tvEntryCount.text = "0 classes"
    }

    override fun onResume() {
        super.onResume()
        if (courseId.isNotEmpty()) loadTimetable()
    }

    // ─── Adapter (read-only — no More/Edit/Delete buttons) ───────────────────

    inner class StudentTimetableAdapter(
        private val list: MutableList<Timetable>
    ) : RecyclerView.Adapter<StudentTimetableAdapter.VH>() {

        inner class VH(val b: ItemTimetableBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(p: ViewGroup, v: Int) =
            VH(ItemTimetableBinding.inflate(LayoutInflater.from(p.context), p, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = list[pos]; val b = h.b

            b.tvSubject.text     = t.subject
            b.tvTeacher.text     = t.teacherName
            b.tvClassroom.text   = if (t.type == "online") "Online Class" else t.classroom.ifEmpty { "Room TBA" }
            b.tvStartTime.text   = t.startTime
            b.tvEndTime.text     = t.endTime
            b.tvDuration.text    = "${t.duration}m"
            b.tvBatchCourse.text = "${t.courseName} - ${t.batchName}"

            // Type chip + accent bar color (same as admin)
            val typeKey = t.type.lowercase()
            val barHex  = when (typeKey) { "online" -> "#1565C0"; "exam" -> "#E65100"; "practical" -> "#6A1B9A"; else -> "#075E54" }
            val chipBg  = when (typeKey) { "online" -> R.drawable.bg_type_online; "exam" -> R.drawable.bg_type_exam; else -> R.drawable.bg_type_chip }
            b.viewAccentBar.setBackgroundColor(Color.parseColor(barHex))
            b.tvType.setBackgroundResource(chipBg)
            b.tvType.setTextColor(Color.parseColor(barHex))
            b.tvType.text = t.type.replaceFirstChar { it.titlecase() }

            // Hide more/edit button — student is read-only
            b.btnMore.visibility = View.GONE

            // Stagger entrance animation
            b.root.alpha = 0f; b.root.translationY = 18f
            b.root.animate().alpha(1f).translationY(0f)
                .setDuration(240).setStartDelay(pos * 45L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }
}