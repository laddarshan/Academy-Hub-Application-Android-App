package com.example.academyhub.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.adapters.TimetableWeekAdapter
import com.example.academyhub.databinding.FragmentTimetableListViewBinding
import com.example.academyhub.models.DayTimetable
import com.example.academyhub.models.Timetable
import com.example.academyhub.timetable.AddTimetableActivity
import com.google.firebase.firestore.FirebaseFirestore

class TimetableWeekViewFragment : Fragment() {

    private var _binding: FragmentTimetableListViewBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var weekAdapter: TimetableWeekAdapter
    private val dayTimetables = mutableListOf<DayTimetable>()

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

        // Read filters passed from TimetableListActivity
        val courseId   = arguments?.getString("courseId") ?: ""
        val semesterId = arguments?.getString("semesterId") ?: ""
        val batchId    = arguments?.getString("batchId") ?: ""

        loadTimetables(courseId, semesterId, batchId)
    }

    private fun setupRecyclerView() {
        weekAdapter = TimetableWeekAdapter(
            dayTimetables = dayTimetables,
            onItemClick = { timetable ->
                val intent = Intent(requireContext(), AddTimetableActivity::class.java)
                intent.putExtra("timetableId", timetable.id)
                intent.putExtra("isEdit", true)
                startActivity(intent)
            }
        )
        binding.rvTimetable.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimetable.adapter = weekAdapter
    }

    private fun loadTimetables(courseId: String, semesterId: String, batchId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvTimetable.visibility = View.GONE

        // Build filtered query — Course + Semester + Batch
        var query = firestore.collection("Timetable")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("batchId", batchId)

        query.addSnapshotListener { snapshot, error ->
            binding.progressBar.visibility = View.GONE

            if (error != null) {
                if (isAdded) Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            val timetables = mutableListOf<Timetable>()
            snapshot?.documents?.forEach { doc ->
                doc.toObject(Timetable::class.java)?.let { timetables.add(it) }
            }

            groupByDay(timetables)
            weekAdapter.updateList(dayTimetables)

            if (isAdded) {
                binding.rvTimetable.visibility = View.VISIBLE
                binding.tvNoData.visibility = if (dayTimetables.isEmpty()) View.VISIBLE else View.GONE
                if (dayTimetables.isEmpty()) binding.tvNoData.text = "No timetable entries found"
            }
        }
    }

    private fun groupByDay(timetables: List<Timetable>) {
        dayTimetables.clear()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        for (day in days) {
            val dayClasses = timetables.filter { it.day == day }.sortedBy { it.startTime }
            if (dayClasses.isNotEmpty()) {
                dayTimetables.add(DayTimetable(day, dayClasses))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}