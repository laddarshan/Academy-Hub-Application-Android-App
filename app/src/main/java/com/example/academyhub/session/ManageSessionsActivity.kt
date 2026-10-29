package com.example.academyhub.session

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityManageSessionsBinding
import com.example.academyhub.models.Session
import com.google.firebase.firestore.FirebaseFirestore

class ManageSessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageSessionsBinding
    private val db = FirebaseFirestore.getInstance()

    private val allList      = mutableListOf<Session>()
    private val filteredList = mutableListOf<Session>()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageSessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SessionAdapter(
            sessions      = filteredList,
            onSetCurrent  = { session -> setAsCurrent(session) },
            onEdit        = { session -> openEdit(session) },
            onToggle      = { session -> toggleStatus(session) },
            onDelete      = { session -> confirmDelete(session) }
        )
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddSession.setOnClickListener {
            startActivity(Intent(this, AddSessionActivity::class.java))
        }

        setupSearch()
        setupSwipeRefresh()
        loadSessions()
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
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
        binding.swipeRefresh.setOnRefreshListener { loadSessions(fromSwipe = true) }
    }

    // ─── Load sessions ─────────────────────────────────────────────────────────

    private fun loadSessions(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        // Load ALL sessions (active + inactive) so toggle works
        db.collection("Sessions").get()
            .addOnSuccessListener { snap ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                allList.clear()
                for (doc in snap) {
                    val session = doc.toObject(Session::class.java)
                    session.id = doc.id
                    allList.add(session)
                }
                allList.sortByDescending { it.startYear }
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
        val total   = allList.size
        val active  = allList.count { it.isActive }
        val current = allList.firstOrNull { it.isCurrent }

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
        binding.tvCurrentName.text = current?.sessionName ?: "—"
    }

    // ─── Filter ────────────────────────────────────────────────────────────────

    private fun applyFilter() {
        val q = binding.etSearch.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(if (q.isEmpty()) allList
        else allList.filter { it.sessionName.lowercase().contains(q) ||
                it.startYear.contains(q) || it.endYear.contains(q) })

        val isEmpty = filteredList.isEmpty()
        binding.layoutEmpty.visibility   = if (isEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility  = if (isEmpty) View.GONE    else View.VISIBLE
        binding.tvEmpty.text = if (q.isNotEmpty()) "No results for \"$q\"" else "No sessions found"

        adapter.notifyDataSetChanged()
        binding.rvSessions.alpha = 0f
        binding.rvSessions.animate().alpha(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ─── IMPORTANT 1: Set Current — uses correct Firestore field "current" ─────

    private fun setAsCurrent(session: Session) {
        // Confirm with user
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Set as Current")
            .setMessage("${session.sessionName} ne current session set karvu chhe?")
        .setPositiveButton("Set Current") { _, _ ->
            binding.progressBar.visibility = View.VISIBLE

            // Step 1: Unset all current sessions (Firestore field = "current")
            db.collection("Sessions").whereEqualTo("current", true).get()
                .addOnSuccessListener { docs ->
                    val batch = db.batch()
                    for (doc in docs) batch.update(doc.reference, "current", false)
                    // Step 2: Set this session as current + ensure active=true
                    val sessionRef = db.collection("Sessions").document(session.id)
                    batch.update(sessionRef, mapOf("current" to true, "active" to true))
                    batch.commit()
                        .addOnSuccessListener {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this,
                                "${session.sessionName} current session set thayo!",
                                Toast.LENGTH_SHORT).show()
                            loadSessions()
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── IMPORTANT 1: Toggle Active — uses correct field "active" ──────────────

    private fun toggleStatus(session: Session) {
        // Read live value from Firestore to avoid stale local state
        db.collection("Sessions").document(session.id).get()
            .addOnSuccessListener { doc ->
                val currentActive = doc.getBoolean("active") ?: true
                val newStatus     = !currentActive
                val label         = if (newStatus) "Activate" else "Deactivate"

                // Confirm with user
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("$label Session")
                    .setMessage("${session.sessionName} ne $label karvu chhe?")
                    .setPositiveButton(label) { _, _ ->
                        binding.progressBar.visibility = View.VISIBLE
                        db.collection("Sessions").document(session.id)
                            .update("active", newStatus)
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                val msg = if (newStatus) "${session.sessionName} Activated!" else "${session.sessionName} Deactivated!"
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                                loadSessions()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error reading session: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── IMPORTANT 1: Edit ─────────────────────────────────────────────────────

    private fun openEdit(session: Session) {
        startActivity(Intent(this, AddSessionActivity::class.java).apply {
            putExtra("isEditMode", true)
            putExtra("sessionId",    session.id)
            putExtra("startYear",    session.startYear)
            putExtra("endYear",      session.endYear)
            putExtra("startDate",    session.startDate)
            putExtra("endDate",      session.endDate)
            putExtra("sessionName",  session.sessionName)
            putExtra("isCurrent",    session.isCurrent)
        })
    }

    // ─── Delete ────────────────────────────────────────────────────────────────

    private fun confirmDelete(session: Session) {
        AlertDialog.Builder(this)
            .setTitle("Delete Session")
            .setMessage("\"${session.sessionName}\" delete karvu chhe?")
            .setPositiveButton("Delete") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                db.collection("Sessions").document(session.id).delete()
                    .addOnSuccessListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show()
                        loadSessions()
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null).show()
    }

    override fun onResume() { super.onResume(); loadSessions() }
}