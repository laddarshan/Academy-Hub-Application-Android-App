package com.example.academyhub.fragments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.academyhub.R
import com.example.academyhub.UserDashboardActivity
import com.example.academyhub.announcements.NotificationsActivity
import com.example.academyhub.announcements.StudentNoticesActivity
import com.example.academyhub.databinding.FragmentUserHomeBinding
import com.example.academyhub.models.Student
import com.example.academyhub.models.Timetable
import com.example.academyhub.student.AttendanceActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class UserHomeFragment : Fragment() {

    private var _binding: FragmentUserHomeBinding? = null
    private val binding get() = _binding!!
    private var animPlayed = false
    private var isTimetableLoading = false

    private val db = FirebaseFirestore.getInstance()

    private val todayDay: String
        get() = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Monday"
            Calendar.TUESDAY   -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY  -> "Thursday"
            Calendar.FRIDAY    -> "Friday"
            Calendar.SATURDAY  -> "Saturday"
            else               -> "Monday"
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setGreeting()
        setupThemeToggle()

        if (!animPlayed) { animPlayed = true; runEntranceAnim() }

        val act = activity as? UserDashboardActivity
        act?.currentStudent?.let { bindStudent(it) }

        binding.cardAttend.setOnClickListener { v ->
            animPress(v) {
                act?.currentStudent?.let { s -> AttendanceActivity.start(requireContext(), s) }
            }
        }

        binding.cardNotices.setOnClickListener { v ->
            animPress(v) {
                startActivity(Intent(requireContext(), StudentNoticesActivity::class.java))
            }
        }

        binding.btnNotification.setOnClickListener { v ->
            animPress(v) {
                startActivity(Intent(requireContext(), NotificationsActivity::class.java))
            }
        }
    }

    fun bindStudent(s: Student) {
        if (_binding == null) return

        binding.tvStudentName.text = s.name.ifEmpty { "Student" }
        binding.tvRollNo.text = if (s.rollNumber.isNotEmpty()) "Roll No. ${s.rollNumber}" else "Roll No. —"

        binding.tvChipName.text  = s.name.ifEmpty { "Student" }
        binding.tvChipRoll.text  = if (s.rollNumber.isNotEmpty()) "Roll No. ${s.rollNumber}" else "Roll No. —"
        binding.tvChipPhone.text = if (s.mobile.isNotEmpty())
            "+91 ${s.mobile.removePrefix("+91")}" else "—"

        binding.tagCourse.text = s.courseName.ifEmpty { "—" }
        binding.tagSem.text = buildString {
            if (s.yearName.isNotEmpty())     append(s.yearName)
            if (s.semesterName.isNotEmpty()) append(" · ${s.semesterName}")
        }.ifEmpty { "—" }

        val isActive = s.status.lowercase() == "active"
        binding.tagStatus.text = if (isActive) "✓ Active" else "✗ Inactive"
        binding.tagStatus.setTextColor(
            if (isActive) requireContext().getColor(R.color.teal_color)
            else Color.parseColor("#E74C3C")
        )

        binding.tvTodayDay.text = todayDay
        loadTodayTimetable(s.courseId, s.batchId)
    }

    // ─── Load today's timetable ───────────────────────────────────────────────

    private fun loadTodayTimetable(courseId: String, batchId: String) {
        if (_binding == null || isTimetableLoading) return
        isTimetableLoading = true

        binding.layoutTimetable.removeAllViews()
        binding.tvNoClasses.visibility = View.GONE

        if (courseId.isEmpty() && batchId.isEmpty()) {
            isTimetableLoading = false
            showNoClasses(); return
        }

        var query = db.collection("Timetable").whereEqualTo("day", todayDay)
        if (courseId.isNotEmpty()) query = query.whereEqualTo("courseId", courseId)
        if (batchId.isNotEmpty())  query = query.whereEqualTo("batchId",  batchId)

        query.get()
            .addOnSuccessListener { snap ->
                isTimetableLoading = false
                if (_binding == null) return@addOnSuccessListener
                binding.layoutTimetable.removeAllViews()

                // Use Timetable model directly — no TimetableEntry needed
                val entries = snap.documents.mapNotNull { doc ->
                    doc.toObject(Timetable::class.java)
                }.sortedBy { it.startTime }

                if (entries.isEmpty()) {
                    showNoClasses()
                } else {
                    binding.tvNoClasses.visibility = View.GONE
                    val distinct = entries.distinctBy { "${it.subject}_${it.startTime}" }
                    distinct.forEachIndexed { i, entry -> addTimetableRow(entry, i) }
                }
            }
            .addOnFailureListener {
                isTimetableLoading = false
                if (_binding != null) showNoClasses()
            }
    }

    // ─── Add row using item_timetable layout ──────────────────────────────────

    private fun addTimetableRow(entry: Timetable, index: Int) {
        if (_binding == null) return
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_timetable, binding.layoutTimetable, false)

        // Hide More button — student is read-only
        row.findViewById<View>(R.id.btnMore).visibility = View.GONE

        // Accent bar color by type
        val (stripColor, typeText, typeColor) = getTypeStyle(entry.type)
        row.findViewById<View>(R.id.viewAccentBar)
            .setBackgroundColor(Color.parseColor(stripColor))

        // Time
        row.findViewById<TextView>(R.id.tvStartTime).apply {
            text = entry.startTime
            setTextColor(Color.parseColor(stripColor))
        }
        row.findViewById<TextView>(R.id.tvEndTime).text = entry.endTime
        row.findViewById<TextView>(R.id.tvDuration).text =
            if (entry.duration > 0) "${entry.duration}m" else ""

        // Content
        row.findViewById<TextView>(R.id.tvSubject).text   = entry.subject
        row.findViewById<TextView>(R.id.tvTeacher).text   = entry.teacherName.ifEmpty { "Teacher TBA" }
        row.findViewById<TextView>(R.id.tvClassroom).text =
            if (entry.type == "online") "💻 Online" else entry.classroom.ifEmpty { "Room TBA" }
        row.findViewById<TextView>(R.id.tvBatchCourse).text = entry.batchName

        // Type badge
        row.findViewById<TextView>(R.id.tvType).apply {
            text = typeText
            setTextColor(Color.parseColor(typeColor))
        }

        binding.layoutTimetable.addView(row)

        // Staggered entrance animation
        row.alpha = 0f; row.translationY = 24f
        row.postDelayed({
            if (_binding == null) return@postDelayed
            row.animate()
                .alpha(1f).translationY(0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }, index * 70L)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getTypeStyle(type: String): Triple<String, String, String> = when (type) {
        "regular"   -> Triple("#075E54", "Regular",   "#075E54")
        "practical" -> Triple("#F59E0B", "Practical", "#F59E0B")
        "online"    -> Triple("#3B82F6", "Online",    "#3B82F6")
        "exam"      -> Triple("#EF4444", "Exam",      "#EF4444")
        else        -> Triple("#6B7280", "Other",     "#6B7280")
    }

    private fun showNoClasses() {
        if (_binding == null) return
        binding.layoutTimetable.removeAllViews()
        binding.tvNoClasses.visibility = View.VISIBLE
        binding.tvNoClasses.text = "No classes today 🎉"
    }

    // ─── Greeting ─────────────────────────────────────────────────────────────

    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvGreeting.text = when {
            hour < 12 -> "Good Morning ☀"
            hour < 17 -> "Good Afternoon 🌤"
            else      -> "Good Evening 🌙"
        }
    }

    // ─── Theme toggle ─────────────────────────────────────────────────────────

    private fun setupThemeToggle() {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.ivThemeIcon.setImageResource(if (isDark) R.drawable.ic_sun else R.drawable.ic_moon)
        binding.btnTheme.setOnClickListener { v ->
            animPress(v) { (activity as? UserDashboardActivity)?.toggleTheme() }
        }
    }

    // ─── Entrance animation ───────────────────────────────────────────────────

    private fun runEntranceAnim() {
        listOf(binding.cardStudentProfile, binding.cardAttend, binding.cardTimetable)
            .forEachIndexed { i, v ->
                v.alpha = 0f; v.translationY = 60f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply {
                            duration = 500; startDelay = (i * 100).toLong()
                        },
                        ObjectAnimator.ofFloat(v, "translationY", 60f, 0f).apply {
                            duration = 500; startDelay = (i * 100).toLong()
                            interpolator = OvershootInterpolator(1.1f)
                        }
                    ); start()
                }
            }
    }

    // ─── Press animation ──────────────────────────────────────────────────────

    private fun animPress(v: View, action: () -> Unit) {
        v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction { action() }.start()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        setGreeting()
        (activity as? UserDashboardActivity)?.currentStudent?.let { bindStudent(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}