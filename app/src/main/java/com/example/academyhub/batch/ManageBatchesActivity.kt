package com.example.academyhub.batch

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.databinding.ActivityManageBatchesBinding
import com.example.academyhub.models.Batch
import com.google.firebase.firestore.FirebaseFirestore

class ManageBatchesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageBatchesBinding
    private val db = FirebaseFirestore.getInstance()

    private val allList      = mutableListOf<Batch>()
    private val filteredList = mutableListOf<Batch>()
    private lateinit var adapter: BatchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBatchesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = BatchAdapter(
            batches  = filteredList,
            onEdit   = { batch -> openEdit(batch) },
            onToggle = { batch -> confirmToggle(batch) },
            onDelete = { batch -> confirmDelete(batch) }
        )
        binding.rvBatches.layoutManager = LinearLayoutManager(this)
        binding.rvBatches.adapter       = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddBatch.setOnClickListener {
            startActivity(Intent(this, AddBatchActivity::class.java))
        }

        setupSearch()
        setupSwipeRefresh()
        loadBatches()
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
    }

    // ─── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            Color.parseColor("#075E54"),
            Color.parseColor("#26A69A"),
            Color.parseColor("#80CBC4")
        )
        binding.swipeRefresh.setOnRefreshListener { loadBatches(fromSwipe = true) }
    }

    // ─── Load batches ──────────────────────────────────────────────────────────

    private fun loadBatches(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        db.collection("Batches").get()
            .addOnSuccessListener { snap ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allList.clear()
                for (doc in snap) {
                    val batch = doc.toObject(Batch::class.java)
                    // Batch model has var id — set it directly
                    batch.id = doc.id
                    allList.add(batch)
                }
                allList.sortBy { it.batchName }
                updateStats()
                applyFilter()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Stats ValueAnimator ───────────────────────────────────────────────────

    private fun updateStats() {
        val total    = allList.size
        val active   = allList.count { it.isActive }
        val inactive = total - active

        ValueAnimator.ofInt(0, total).apply {
            duration = 500
            addUpdateListener { binding.tvTotalCount.text = "${it.animatedValue}" }
            start()
        }
        ValueAnimator.ofInt(0, active).apply {
            duration = 600
            addUpdateListener { binding.tvActiveCount.text = "${it.animatedValue}" }
            start()
        }
        ValueAnimator.ofInt(0, inactive).apply {
            duration = 700
            addUpdateListener { binding.tvInactiveCount.text = "${it.animatedValue}" }
            start()
        }
        binding.tvCount.text = "$total batches"
    }

    // ─── Filter + fade ─────────────────────────────────────────────────────────

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(
            if (q.isEmpty()) allList
            else allList.filter {
                it.batchName.lowercase().contains(q) ||
                it.batchCode.lowercase().contains(q)
            }
        )
        val isEmpty = filteredList.isEmpty()
        binding.layoutEmpty.visibility  = if (isEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility = if (isEmpty) View.GONE    else View.VISIBLE
        binding.tvNoBatches.text =
            if (q.isNotEmpty()) "No results for \"$q\"" else "No batches found"

        adapter.notifyDataSetChanged()
        binding.rvBatches.alpha = 0f
        binding.rvBatches.animate().alpha(1f)
            .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── Edit ──────────────────────────────────────────────────────────────────

    private fun openEdit(batch: Batch) {
        startActivity(Intent(this, AddBatchActivity::class.java).apply {
            putExtra("isEdit",  true)
            putExtra("batchId", batch.id)
        })
    }

    // ─── Toggle — reads live Firestore, uses correct field "active" ────────────

    private fun confirmToggle(batch: Batch) {
        db.collection("Batches").document(batch.id).get()
            .addOnSuccessListener { doc ->
                val currentActive = doc.getBoolean("active") ?: true
                val newStatus     = !currentActive
                val label         = if (newStatus) "Activate" else "Deactivate"

                AlertDialog.Builder(this)
                    .setTitle("$label Batch")
                    .setMessage("\"${batch.batchName}\" ne $label karvu chhe?")
                    .setPositiveButton(label) { _, _ ->
                        binding.progressBar.visibility = View.VISIBLE
                        // ✅ Correct field "active" (not "isActive")
                        db.collection("Batches").document(batch.id)
                            .update("active", newStatus)
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this,
                                    "${batch.batchName} ${if (newStatus) "Activated!" else "Deactivated!"}",
                                    Toast.LENGTH_SHORT).show()
                                loadBatches()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    private fun confirmDelete(batch: Batch) {
        AlertDialog.Builder(this)
            .setTitle("Delete Batch")
            .setMessage("\"${batch.batchName}\" delete karvu chhe?")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                db.collection("Batches").document(batch.id).delete()
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Batch deleted", Toast.LENGTH_SHORT).show()
                        loadBatches()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); loadBatches() }
}
