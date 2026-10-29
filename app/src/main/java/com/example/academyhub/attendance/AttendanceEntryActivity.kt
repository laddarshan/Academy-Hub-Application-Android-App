package com.example.academyhub.attendance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityAttendanceEntryBinding
import com.example.academyhub.models.Subject
import com.google.firebase.firestore.FirebaseFirestore

data class SubjectAttendance(
    val subjectId:   String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    var percentage:  Int    = 0
)

class AttendanceEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceEntryBinding
    private lateinit var firestore: FirebaseFirestore

    private var studentId   = ""
    private var studentName = ""
    private var rollNumber  = ""
    private var sessionId   = ""
    private var semesterId  = ""
    private var courseId    = ""

    private val subjectAttendanceList = mutableListOf<SubjectAttendance>()
    private var lastOverall = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore   = FirebaseFirestore.getInstance()
        studentId   = intent.getStringExtra("studentId")   ?: ""
        studentName = intent.getStringExtra("studentName") ?: ""
        rollNumber  = intent.getStringExtra("rollNumber")  ?: ""
        sessionId   = intent.getStringExtra("sessionId")   ?: ""
        semesterId  = intent.getStringExtra("semesterId")  ?: ""
        courseId    = intent.getStringExtra("courseId")    ?: ""

        binding.tvStudentName.text = studentName
        binding.tvRollNumber.text  = "Roll: $rollNumber"

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
        }
        binding.btnSave.setOnClickListener { saveAttendance() }
        binding.rvSubjects.layoutManager = LinearLayoutManager(this)
        binding.rvSubjects.itemAnimator  = null

        loadExistingAndSubjects()
    }

    // ──────────────────────────────────────
    //  Load subjects + existing — ORIGINAL logic
    // ──────────────────────────────────────
    private fun loadExistingAndSubjects() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("Subjects")
            .whereEqualTo("semesterId", semesterId)
            .get()
            .addOnSuccessListener { subjectDocs ->
                val subjects = subjectDocs.documents
                    .mapNotNull { it.toObject(Subject::class.java) }
                    .sortedBy { it.subjectName }

                if (subjects.isEmpty()) {
                    binding.progressBar.visibility  = View.GONE
                    binding.tvNoSubjects.visibility = View.VISIBLE
                    binding.rvSubjects.visibility   = View.GONE
                    return@addOnSuccessListener
                }

                // Existing attendance doc
                firestore.collection("StudentAttendance")
                    .document("${studentId}_${semesterId}")
                    .get()
                    .addOnSuccessListener { attDoc ->
                        binding.progressBar.visibility = View.GONE

                        val existingMap = mutableMapOf<String, Int>()
                        if (attDoc.exists()) {
                            val subjectsMap = attDoc.get("subjects") as? Map<*, *>
                            subjectsMap?.forEach { (k, v) ->
                                existingMap[k.toString()] = (v as? Long)?.toInt() ?: 0
                            }
                        }
                        buildAndShow(subjects, existingMap)
                    }
                    .addOnFailureListener {
                        binding.progressBar.visibility = View.GONE
                        // No existing data — fresh start
                        buildAndShow(subjects, emptyMap())
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Subjects load error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildAndShow(subjects: List<Subject>, existingMap: Map<String, Int>) {
        subjectAttendanceList.clear()
        for (s in subjects) {
            subjectAttendanceList.add(SubjectAttendance(
                subjectId   = s.id,
                subjectName = s.subjectName,
                subjectCode = s.subjectCode,
                percentage  = existingMap[s.id] ?: 0
            ))
        }
        setupAdapter()
        updateOverallDisplay(animate = false)
        staggerEntranceAnimation()
    }

    // ──────────────────────────────────────
    //  Adapter — indicator bar + focus anim
    // ──────────────────────────────────────
    private fun setupAdapter() {
        binding.tvNoSubjects.visibility = View.GONE
        binding.rvSubjects.visibility   = View.VISIBLE

        binding.rvSubjects.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_subject_attendance, parent, false)
                ) {}

            override fun getItemCount() = subjectAttendanceList.size

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                val item      = subjectAttendanceList[pos]
                val tvName    = holder.itemView.findViewById<TextView>(R.id.tvSubjectName)
                val tvCode    = holder.itemView.findViewById<TextView>(R.id.tvSubjectCode)
                val etPer     = holder.itemView.findViewById<EditText>(R.id.etPercentage)
                val indicator = holder.itemView.findViewById<View>(R.id.indicatorBar)
                val divider   = holder.itemView.findViewById<View>(R.id.subjectDivider)

                tvName.text = item.subjectName
                tvCode.text = item.subjectCode

                // Hide divider on last item
                divider.visibility =
                    if (pos < subjectAttendanceList.size - 1) View.VISIBLE else View.GONE

                // Set existing value without triggering watcher
                etPer.removeTextChangedListener(etPer.tag as? TextWatcher)
                etPer.setText(if (item.percentage > 0) item.percentage.toString() else "")

                // Focus → left green indicator bar animates in
                etPer.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) showIndicator(indicator)
                    else          hideIndicator(indicator)
                }

                val watcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int)     {}
                    override fun afterTextChanged(s: Editable?) {
                        val adapterPos = holder.bindingAdapterPosition
                        if (adapterPos == RecyclerView.NO_POSITION) return
                        val text    = s.toString().trim()
                        val value   = text.toIntOrNull() ?: 0
                        val clamped = value.coerceIn(0, 100)
                        subjectAttendanceList[adapterPos].percentage = clamped
                        updateOverallDisplay(animate = true)
                    }
                }
                etPer.tag = watcher
                etPer.addTextChangedListener(watcher)
            }
        }
    }

    // Left green indicator: overshoot scale-in
    private fun showIndicator(indicator: View) {
        indicator.visibility = View.VISIBLE
        indicator.alpha      = 0f
        indicator.scaleY     = 0.3f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(indicator, "alpha", 0f, 1f)
                    .apply { duration = 200 },
                ObjectAnimator.ofFloat(indicator, "scaleY", 0.3f, 1f)
                    .apply { duration = 280; interpolator = OvershootInterpolator(1.6f) }
            )
            start()
        }
    }

    // Indicator fade-out then INVISIBLE (using AnimatorListenerAdapter — no withEndAction)
    private fun hideIndicator(indicator: View) {
        ObjectAnimator.ofFloat(indicator, "alpha", 1f, 0f).apply {
            duration = 150
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    indicator.visibility = View.INVISIBLE
                }
            })
            start()
        }
    }

    // ValueAnimator: overall % count-up with live color change
    private fun updateOverallDisplay(animate: Boolean) {
        if (subjectAttendanceList.isEmpty()) return
        val newAvg = subjectAttendanceList.map { it.percentage }.average().toInt()
        if (!animate || lastOverall == newAvg) {
            binding.tvOverallPercentage.text = "$newAvg%"
            applyOverallColor(newAvg)
            lastOverall = newAvg
            return
        }
        ValueAnimator.ofInt(lastOverall, newAvg).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Int
                binding.tvOverallPercentage.text = "$v%"
                applyOverallColor(v)
            }
            start()
        }
        lastOverall = newAvg
    }

    private fun applyOverallColor(pct: Int) {
        binding.tvOverallPercentage.setTextColor(Color.parseColor(when {
            pct >= 75 -> "#075E54"
            pct >= 50 -> "#E65100"
            else      -> "#B71C1C"
        }))
    }

    // Stagger slide-in from right for subjects
    private fun staggerEntranceAnimation() {
        binding.rvSubjects.post {
            val lm = binding.rvSubjects.layoutManager as? LinearLayoutManager ?: return@post
            for (i in 0..lm.findLastVisibleItemPosition()) {
                val child = lm.findViewByPosition(i) ?: continue
                child.alpha        = 0f
                child.translationX = 28f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(child, "alpha", 0f, 1f)
                            .apply { duration = 240 },
                        ObjectAnimator.ofFloat(child, "translationX", 28f, 0f)
                            .apply { duration = 260; interpolator = DecelerateInterpolator() }
                    )
                    startDelay = (i * 40L)
                    start()
                }
            }
        }
    }

    // ──────────────────────────────────────
    //  Save attendance — ORIGINAL logic kept
    // ──────────────────────────────────────
    private fun saveAttendance() {
        if (subjectAttendanceList.isEmpty()) return

        // Button press feedback
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.btnSave, "scaleX", 1f, 0.95f, 1f)
                    .apply { duration = 150 },
                ObjectAnimator.ofFloat(binding.btnSave, "scaleY", 1f, 0.95f, 1f)
                    .apply { duration = 150 }
            )
            start()
        }

        binding.btnSave.isEnabled      = false
        binding.progressBar.visibility = View.VISIBLE

        // Build subjects map: {subjectId: percentage}
        val subjectsMap = mutableMapOf<String, Any>()
        for (item in subjectAttendanceList) {
            subjectsMap[item.subjectId] = item.percentage
        }

        val overall = subjectAttendanceList.map { it.percentage }.average()

        val data = hashMapOf(
            "studentId"   to studentId,
            "semesterId"  to semesterId,
            "sessionId"   to sessionId,
            "subjects"    to subjectsMap,
            "overall"     to overall.toInt(),
            "updatedAt"   to System.currentTimeMillis()
        )

        // Save to StudentAttendance collection
        val docId = "${studentId}_${semesterId}"
        firestore.collection("StudentAttendance").document(docId).set(data)
            .addOnSuccessListener {
                // Update student's overall attendancePercentage in Students collection
                firestore.collection("Students").document(studentId)
                    .update("attendancePercentage", overall)
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled      = true
                        Toast.makeText(this, "Attendance saved!", Toast.LENGTH_SHORT).show()
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
                    }
                    .addOnFailureListener {
                        binding.progressBar.visibility = View.GONE
                        binding.btnSave.isEnabled      = true
                        Toast.makeText(this, "Saved! (Student update failed)", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled      = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
