package com.example.academyhub.fees

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityFeesDashboardBinding
import com.example.academyhub.databinding.ItemFeesStructureListBinding
import com.example.academyhub.models.FeesStructure
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore

class FeesDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeesDashboardBinding
    private lateinit var firestore: FirebaseFirestore

    private val allStructures      = mutableListOf<FeesStructure>()
    private val filteredStructures = mutableListOf<FeesStructure>()
    private var selectedCourse: String? = null

    private val adapter = StructuresListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeesDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firestore = FirebaseFirestore.getInstance()

        setupRecycler()
        setupClicks()
        loadSummary()
        loadStructures()

        // Card entrance animations
        binding.cardFeesStructure.alpha = 0f; binding.cardFeesStructure.translationY = 30f
        binding.cardStudentFees.alpha   = 0f; binding.cardStudentFees.translationY   = 30f
        binding.cardFeesStructure.animate().alpha(1f).translationY(0f)
            .setDuration(360).setStartDelay(160).setInterpolator(OvershootInterpolator(0.7f)).start()
        binding.cardStudentFees.animate().alpha(1f).translationY(0f)
            .setDuration(360).setStartDelay(240).setInterpolator(OvershootInterpolator(0.7f)).start()
    }

    private fun setupRecycler() {
        binding.rvFeesStructures.layoutManager = LinearLayoutManager(this)
        binding.rvFeesStructures.adapter = adapter
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardFeesStructure.setOnClickListener {
            animateCardTap(it) {
                startActivity(Intent(this, FeesRoleActivity::class.java)
                    .putExtra("mode", "structure"))
            }
        }
        binding.cardStudentFees.setOnClickListener {
            animateCardTap(it) {
                startActivity(Intent(this, FeesRoleActivity::class.java)
                    .putExtra("mode", "student_fees"))
            }
        }
    }

    private fun animateCardTap(v: View, action: () -> Unit) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.96f, 1f).apply { duration = 150 },
                ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.96f, 1f).apply { duration = 150 }
            ); start()
        }
        v.postDelayed(action, 130)
    }

    // ─── Summary ───────────────────────────────────────────────────────────────

    private fun loadSummary() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Students").get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                var totalCollected = 0.0
                var totalPending   = 0.0
                for (doc in docs) {
                    val paid  = doc.getDouble("paidFees") ?: 0.0
                    val total = doc.getDouble("totalFees") ?: 0.0
                    totalCollected += paid
                    totalPending   += (total - paid).coerceAtLeast(0.0)
                }
                // ValueAnimator count-up
                animateAmount(binding.tvTotalCollected, totalCollected, "#FFFFFF")
                animateAmount(binding.tvTotalPending,   totalPending,   "#FFCDD2")
            }
            .addOnFailureListener { binding.progressBar.visibility = View.GONE }
    }

    private fun animateAmount(tv: android.widget.TextView, target: Double, hexColor: String) {
        ValueAnimator.ofFloat(0f, target.toFloat()).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = (it.animatedValue as Float).toDouble()
                tv.text = "₹${fmt(v)}"
                tv.setTextColor(Color.parseColor(hexColor))
            }
            start()
        }
    }

    // ─── Structures ────────────────────────────────────────────────────────────

    private fun loadStructures() {
        firestore.collection("FeesStructure").get()
            .addOnSuccessListener { snap ->
                allStructures.clear()
                for (doc in snap.documents) {
                    val s = doc.toObject(FeesStructure::class.java) ?: continue
                    allStructures.add(s)
                }
                allStructures.sortBy { it.courseName + it.semesterName }
                setupFilterChips()
                applyFilter()
            }
    }

    private fun setupFilterChips() {
        binding.chipGroupCourses.removeAllViews()

        val allChip = Chip(this).apply {
            text = "All"; isCheckable = true; isChecked = true
            setChipBackgroundColorResource(R.color.primary_color)
            setTextColor(Color.WHITE)
            setOnClickListener { selectedCourse = null; applyFilter(); updateChips(null) }
        }
        binding.chipGroupCourses.addView(allChip)

        allStructures.map { it.courseName }.distinct().sorted().forEach { name ->
            val chip = Chip(this).apply {
                text = name; isCheckable = true
                setOnClickListener { selectedCourse = name; applyFilter(); updateChips(name) }
            }
            binding.chipGroupCourses.addView(chip)
        }
    }

    private fun updateChips(selected: String?) {
        for (i in 0 until binding.chipGroupCourses.childCount) {
            val chip = binding.chipGroupCourses.getChildAt(i) as? Chip ?: continue
            val isSel = if (selected == null) i == 0 else chip.text == selected
            chip.isChecked = isSel
            if (isSel) {
                chip.setChipBackgroundColorResource(R.color.primary_color)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.chipBackgroundColor = null
                chip.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    private fun applyFilter() {
        filteredStructures.clear()
        filteredStructures.addAll(
            if (selectedCourse == null) allStructures
            else allStructures.filter { it.courseName == selectedCourse }
        )
        binding.layoutEmpty.visibility       = if (filteredStructures.isEmpty()) View.VISIBLE else View.GONE
        binding.rvFeesStructures.visibility  = if (filteredStructures.isEmpty()) View.GONE    else View.VISIBLE
        adapter.update(filteredStructures)
    }

    // ─── Inner Adapter ─────────────────────────────────────────────────────────

    inner class StructuresListAdapter :
        RecyclerView.Adapter<StructuresListAdapter.VH>() {

        private val list = mutableListOf<FeesStructure>()

        fun update(newList: List<FeesStructure>) {
            list.clear(); list.addAll(newList); notifyDataSetChanged()
        }

        inner class VH(val b: ItemFeesStructureListBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            VH(ItemFeesStructureListBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val s = list[pos]
            holder.b.tvCourseSem.text    = "${s.courseName}  \u203A  ${s.semesterName}"
            holder.b.tvInstallments.text = "${s.installmentCount} installment${if (s.installmentCount != 1) "s" else ""}"
            holder.b.tvAmount.text       = "₹${fmt(s.totalFees)}"

            // Stagger entrance
            holder.b.root.alpha = 0f; holder.b.root.translationX = -16f
            holder.b.root.animate().alpha(1f).translationX(0f)
                .setDuration(220).setStartDelay(pos * 50L)
                .setInterpolator(DecelerateInterpolator()).start()

            // Click → go to edit
            holder.b.root.setOnClickListener {
                startActivity(Intent(this@FeesDashboardActivity, FeesRoleActivity::class.java)
                    .putExtra("mode", "structure"))
            }
        }
    }

    private fun fmt(a: Double) = if (a >= 100000)
        String.format("%.1fL", a / 100000)
    else String.format("%,.0f", a)

    override fun onResume() { super.onResume(); loadSummary(); loadStructures() }
}
