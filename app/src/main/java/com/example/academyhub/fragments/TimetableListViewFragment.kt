package com.example.academyhub.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.R
import com.example.academyhub.adapters.TimetableAdapter
import com.example.academyhub.databinding.FragmentTimetableListViewBinding
import com.example.academyhub.models.Timetable
import com.example.academyhub.timetable.AddTimetableActivity
import com.google.firebase.firestore.FirebaseFirestore

class TimetableListViewFragment : Fragment() {

    private var _binding: FragmentTimetableListViewBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var timetableAdapter: TimetableAdapter
    private val timetableList = mutableListOf<Timetable>()

    // Day order for sorting
    private val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimetableListViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()
        setupRecyclerView()

        // Read filters
        val courseId   = arguments?.getString("courseId") ?: ""
        val semesterId = arguments?.getString("semesterId") ?: ""
        val batchId    = arguments?.getString("batchId") ?: ""

        loadTimetables(courseId, semesterId, batchId)
    }

    private fun setupRecyclerView() {
        timetableAdapter = TimetableAdapter(
            timetables = timetableList,
            onItemClick = { timetable ->
                val intent = Intent(requireContext(), AddTimetableActivity::class.java)
                intent.putExtra("timetableId", timetable.id)
                intent.putExtra("isEdit", true)
                startActivity(intent)
            },
            onMoreClick = { timetable -> showOptionsMenu(timetable) }
        )
        binding.rvTimetable.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimetable.adapter = timetableAdapter
    }

    private fun loadTimetables(courseId: String, semesterId: String, batchId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvTimetable.visibility = View.GONE

        // Filter: Course + Semester + Batch
        firestore.collection("Timetable")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("batchId", batchId)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    if (isAdded) Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                timetableList.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Timetable::class.java)?.let { timetableList.add(it) }
                }

                // Sort by day order then by start time (no getDayIndex needed)
                timetableList.sortWith(compareBy({ dayOrder.indexOf(it.day) }, { it.startTime }))

                timetableAdapter.updateList(timetableList)

                if (isAdded) {
                    binding.rvTimetable.visibility = View.VISIBLE
                    binding.tvNoData.visibility = if (timetableList.isEmpty()) View.VISIBLE else View.GONE
                    if (timetableList.isEmpty()) binding.tvNoData.text = "No timetable entries found"
                }
            }
    }

    private fun showOptionsMenu(timetable: Timetable) {
        val popupMenu = PopupMenu(requireContext(), binding.rvTimetable)
        popupMenu.menuInflater.inflate(R.menu.menu_timetable_options, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(requireContext(), AddTimetableActivity::class.java)
                    intent.putExtra("timetableId", timetable.id)
                    intent.putExtra("isEdit", true)
                    startActivity(intent)
                    true
                }
                R.id.action_cancel -> { cancelClass(timetable); true }
                R.id.action_delete -> { showDeleteConfirmation(timetable); true }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun cancelClass(timetable: Timetable) {
        firestore.collection("Timetable").document(timetable.id)
            .update("status", "cancelled")
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Class cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation(timetable: Timetable) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this timetable entry?")
            .setPositiveButton("Delete") { _, _ -> deleteTimetable(timetable) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTimetable(timetable: Timetable) {
        firestore.collection("Timetable").document(timetable.id)
            .delete()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                if (isAdded) Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}