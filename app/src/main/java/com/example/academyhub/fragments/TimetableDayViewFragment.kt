package com.example.academyhub.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.adapters.TimetableAdapter
import com.example.academyhub.databinding.FragmentTimetableListViewBinding
import com.example.academyhub.models.Timetable
import com.example.academyhub.timetable.AddTimetableActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class TimetableDayViewFragment : Fragment() {

    private var _binding: FragmentTimetableListViewBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var timetableAdapter: TimetableAdapter
    private val timetableList = mutableListOf<Timetable>()

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

        loadTodayTimetable(courseId, semesterId, batchId)
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
            onMoreClick = { }
        )
        binding.rvTimetable.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTimetable.adapter = timetableAdapter
    }

    private fun loadTodayTimetable(courseId: String, semesterId: String, batchId: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvTimetable.visibility = View.GONE

        val currentDay = SimpleDateFormat("EEEE", Locale.getDefault()).format(Calendar.getInstance().time)

        // Filter: today's day + course + semester + batch
        firestore.collection("Timetable")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("batchId", batchId)
            .whereEqualTo("day", currentDay)
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

                timetableList.sortBy { it.startTime }
                timetableAdapter.updateList(timetableList)

                if (isAdded) {
                    binding.rvTimetable.visibility = View.VISIBLE
                    binding.tvNoData.visibility = if (timetableList.isEmpty()) View.VISIBLE else View.GONE
                    if (timetableList.isEmpty()) binding.tvNoData.text = "No classes today ($currentDay)"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}