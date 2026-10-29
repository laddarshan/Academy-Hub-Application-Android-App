package com.example.academyhub.attendance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAttendanceStudentListBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore

class AttendanceStudentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceStudentListBinding
    private lateinit var firestore: FirebaseFirestore

    private var sessionId    = ""; private var sessionName  = ""
    private var courseId     = ""; private var courseName   = ""
    private var yearId       = ""; private var yearName     = ""
    private var semesterId   = ""; private var semesterName = ""
    private var batchId      = ""; private var batchName    = ""

    // Separate all vs filtered for search
    private val allStudents      = mutableListOf<Student>()
    private val filteredStudents = mutableListOf<Student>()
    private lateinit var studentAdapter: StudentAdapter

    // Avatar teal palette — cycles by name hash
    private val avatarColors = listOf(
        "#075E54", "#00897B", "#26A69A", "#00796B", "#004D40", "#00695C"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceStudentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore    = FirebaseFirestore.getInstance()
        sessionId    = intent.getStringExtra("sessionId")    ?: ""
        sessionName  = intent.getStringExtra("sessionName")  ?: ""
        courseId     = intent.getStringExtra("courseId")     ?: ""
        courseName   = intent.getStringExtra("courseName")   ?: ""
        yearId       = intent.getStringExtra("yearId")       ?: ""
        yearName     = intent.getStringExtra("yearName")     ?: ""
        semesterId   = intent.getStringExtra("semesterId")   ?: ""
        semesterName = intent.getStringExtra("semesterName") ?: ""
        batchId      = intent.getStringExtra("batchId")      ?: ""
        batchName    = intent.getStringExtra("batchName")    ?: ""

        binding.tvGroupBadge.text = "$sessionName  \u203a  $courseName  \u203a  $yearName  \u203a  $semesterName  \u203a  $batchName"

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }

        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        loadStudents()
    }

    // ──────────────────────────────────────
    //  RecyclerView setup
    // ──────────────────────────────────────
    private fun setupRecyclerView() {
        studentAdapter = StudentAdapter(filteredStudents) { student ->
            startActivity(
                Intent(this, AttendanceEntryActivity::class.java).apply {
                    putExtra("studentId",   student.id)
                    putExtra("studentName", student.name)
                    putExtra("rollNumber",  student.rollNumber)
                    putExtra("sessionId",   sessionId)
                    putExtra("semesterId",  semesterId)
                    putExtra("courseId",    courseId)
                }
            )
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        }
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter       = studentAdapter
        binding.rvStudents.itemAnimator  = null          // no flicker
    }

    // ──────────────────────────────────────
    //  Search — TextWatcher + Fade In/Out
    // ──────────────────────────────────────
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterStudents(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    private fun filterStudents(query: String) {
        // Step 1 — Fade OUT
        ObjectAnimator.ofFloat(binding.rvStudents, "alpha", 1f, 0f)
            .apply { duration = 110; start() }

        binding.rvStudents.postDelayed({
            filteredStudents.clear()
            if (query.isBlank()) {
                filteredStudents.addAll(allStudents)
            } else {
                val q = query.trim().lowercase()
                allStudents.filterTo(filteredStudents) {
                    it.name.lowercase().contains(q) || it.rollNumber.lowercase().contains(q)
                }
            }
            studentAdapter.notifyDataSetChanged()
            updateEmptyState()

            // Step 2 — Fade IN + Slide UP
            binding.rvStudents.translationY = 18f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.rvStudents, "alpha", 0f, 1f)
                        .apply { duration = 200 },
                    ObjectAnimator.ofFloat(binding.rvStudents, "translationY", 18f, 0f)
                        .apply { duration = 220; interpolator = DecelerateInterpolator() }
                )
                start()
            }
        }, 120)
    }

    // ──────────────────────────────────────
    //  SwipeRefreshLayout — #075E54 colors
    //  Icon color changes while swiping
    // ──────────────────────────────────────
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, android.R.color.white)
        )
        binding.swipeRefresh.setOnRefreshListener { loadStudents(fromSwipe = true) }
    }

    // ──────────────────────────────────────
    //  Load students — ORIGINAL LOGIC kept
    //  Single batchId filter, rest client-side
    // ──────────────────────────────────────
    private fun loadStudents(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Students")
            .whereEqualTo("batchId", batchId)
            .get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false

                allStudents.clear()
                for (d in docs) {
                    val s = d.toObject(Student::class.java)
                    if (s.status     != "active")   continue   // client-side filter
                    if (s.semesterId != semesterId) continue
                    if (s.sessionId  != sessionId)  continue
                    allStudents.add(s)
                }
                allStudents.sortBy { it.rollNumber }

                // ValueAnimator — count badge counts up 0 → N
                animateCountBadge(allStudents.size)

                // Re-apply current search query
                val currentQuery = binding.etSearch.text.toString()
                filteredStudents.clear()
                if (currentQuery.isBlank()) {
                    filteredStudents.addAll(allStudents)
                } else {
                    val q = currentQuery.trim().lowercase()
                    allStudents.filterTo(filteredStudents) {
                        it.name.lowercase().contains(q) || it.rollNumber.lowercase().contains(q)
                    }
                }
                studentAdapter.notifyDataSetChanged()
                updateEmptyState()

                if (!fromSwipe) staggerEntranceAnimation()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ValueAnimator: count badge 0 → N animated
    private fun animateCountBadge(target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { binding.tvStudentCount.text = "${it.animatedValue}" }
            start()
        }
    }

    // Stagger slide-up entrance for visible rows
    private fun staggerEntranceAnimation() {
        binding.rvStudents.post {
            val lm   = binding.rvStudents.layoutManager as? LinearLayoutManager ?: return@post
            val last = lm.findLastVisibleItemPosition()
            for (i in 0..last) {
                val child = lm.findViewByPosition(i) ?: continue
                child.alpha        = 0f
                child.translationY = 36f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                            .apply { duration = 260 },
                        ObjectAnimator.ofFloat(child, "translationY", 36f, 0f)
                            .apply { duration = 280; interpolator = DecelerateInterpolator() }
                    )
                    startDelay = (i * 45L)
                    start()
                }
            }
        }
    }

    private fun updateEmptyState() {
        val empty = filteredStudents.isEmpty()
        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvStudents.visibility  = if (empty) View.GONE   else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadStudents()   // original onResume reload kept
    }

    // ──────────────────────────────────────
    //  Inner Adapter
    // ──────────────────────────────────────
    inner class StudentAdapter(
        private val list: List<Student>,
        private val onClick: (Student) -> Unit
    ) : RecyclerView.Adapter<StudentAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val serial:      TextView    = v.findViewById(R.id.tvSerialNo)
            val letter:      TextView    = v.findViewById(R.id.tvAvatarLetter)
            val avatarFrame: FrameLayout = v.findViewById(R.id.avatarFrame)
            val name:        TextView    = v.findViewById(R.id.tvStudentName)
            val roll:        TextView    = v.findViewById(R.id.tvRollNumber)
            val attendance:  TextView    = v.findViewById(R.id.tvOverallAttendance)
            val dot:         View        = v.findViewById(R.id.attendanceDot)
            val divider:     View        = v.findViewById(R.id.itemDivider)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_attendance_student, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val s = list[position]

            h.serial.text = "${position + 1}"
            h.letter.text = s.name.firstOrNull()?.uppercase() ?: "?"
            h.name.text   = s.name
            h.roll.text   = "Roll: ${s.rollNumber}"

            // Attendance color coding
            val pct = s.attendancePercentage.toInt()
            h.attendance.text = "$pct%"
            val (dotHex, textHex) = when {
                pct >= 75 -> "#4CAF50" to "#075E54"
                pct >= 50 -> "#FF9800" to "#E65100"
                else      -> "#F44336" to "#B71C1C"
            }
            (h.dot.background as? GradientDrawable)?.setColor(Color.parseColor(dotHex))
            h.attendance.setTextColor(Color.parseColor(textHex))

            // Avatar color — unique per student name
            val colorHex = avatarColors[Math.abs(s.name.hashCode()) % avatarColors.size]
            (h.avatarFrame.background as? GradientDrawable)?.setColor(Color.parseColor(colorHex))

            // Hide divider on last item
            h.divider.visibility = if (position < list.size - 1) View.VISIBLE else View.GONE

            // Click: capture student FIRST, then animate, then navigate
            h.itemView.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val student = list[pos]   // capture before any delay
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(h.itemView, "scaleX", 1f, 0.97f, 1f)
                            .apply { duration = 140 },
                        ObjectAnimator.ofFloat(h.itemView, "scaleY", 1f, 0.97f, 1f)
                            .apply { duration = 140 }
                    )
                    start()
                }
                onClick(student)   // navigate immediately, no delay
            }
        }
    }
}