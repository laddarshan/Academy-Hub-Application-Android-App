package com.example.academyhub.reports

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class FilterSelection(
    val courseId: String = "all",
    val courseName: String = "All Courses",
    val sessionId: String = "all",
    val sessionName: String = "All Sessions",
    val yearId: String = "all",
    val yearName: String = "All Years",
    val semesterId: String = "all",
    val semesterName: String = "All Semesters"
)

class ReportFilterHelper(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val spinnerCourse: Spinner,
    private val spinnerSession: Spinner,
    private val spinnerYear: Spinner,
    private val spinnerSemester: Spinner,
    private val onFilterChanged: (FilterSelection) -> Unit
) {
    private val courseList   = mutableListOf<Pair<String, String>>()
    private val sessionList  = mutableListOf<Pair<String, String>>()
    private val yearList     = mutableListOf<Pair<String, String>>()
    private val semesterList = mutableListOf<Pair<String, String>>()

    private var selCourseId = "all";   private var selCourseName = "All Courses"
    private var selSessionId = "all";  private var selSessionName = "All Sessions"
    private var selYearId = "all";     private var selYearName = "All Years"
    private var selSemId = "all";      private var selSemName = "All Semesters"

    fun loadFilters() {
        loadCourses()
        loadSessions()
        setupYearSpinner()
        resetSemesters()
    }

    private fun loadCourses() {
        db.collection("Courses").get().addOnSuccessListener { snap ->
            courseList.clear()
            courseList.add("all" to "All Courses")
            for (doc in snap) {
                val name = doc.getString("courseName") ?: continue
                courseList.add(doc.id to name)
            }
            setupSpinner(spinnerCourse, courseList.map { it.second }) { pos ->
                selCourseId = courseList[pos].first
                selCourseName = courseList[pos].second
                if (selCourseId != "all") loadSemesters(selCourseId) else resetSemesters()
                notifyChanged()
            }
        }
    }

    private fun loadSessions() {
        db.collection("Sessions").get().addOnSuccessListener { snap ->
            sessionList.clear()
            sessionList.add("all" to "All Sessions")
            for (doc in snap) {
                val name = doc.getString("sessionName") ?: doc.getString("name") ?: continue
                sessionList.add(doc.id to name)
            }
            setupSpinner(spinnerSession, sessionList.map { it.second }) { pos ->
                selSessionId = sessionList[pos].first
                selSessionName = sessionList[pos].second
                notifyChanged()
            }
        }
    }

    private fun setupYearSpinner() {
        yearList.clear()
        yearList.addAll(listOf("all" to "All Years", "FY" to "FY (First Year)",
            "SY" to "SY (Second Year)", "TY" to "TY (Third Year)"))
        setupSpinner(spinnerYear, yearList.map { it.second }) { pos ->
            selYearId = yearList[pos].first
            selYearName = yearList[pos].second
            notifyChanged()
        }
    }

    private fun loadSemesters(courseId: String) {
        db.collection("Semesters").whereEqualTo("courseId", courseId)
            .get().addOnSuccessListener { snap ->
                semesterList.clear()
                semesterList.add("all" to "All Semesters")
                val items = snap.documents.mapNotNull { doc ->
                    val name = doc.getString("semesterName") ?: doc.getString("name") ?: return@mapNotNull null
                    doc.id to name
                }.sortedBy { it.second }
                semesterList.addAll(items)
                setupSpinner(spinnerSemester, semesterList.map { it.second }) { pos ->
                    selSemId = semesterList[pos].first
                    selSemName = semesterList[pos].second
                    notifyChanged()
                }
            }
    }

    private fun resetSemesters() {
        semesterList.clear()
        semesterList.add("all" to "All Semesters")
        selSemId = "all"; selSemName = "All Semesters"
        setupSpinner(spinnerSemester, semesterList.map { it.second }) { _ -> }
    }

    private fun setupSpinner(spinner: Spinner, items: List<String>, onSelected: (Int) -> Unit) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = onSelected(pos)
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun notifyChanged() = onFilterChanged(getCurrentFilter())

    fun getCurrentFilter() = FilterSelection(selCourseId, selCourseName,
        selSessionId, selSessionName, selYearId, selYearName, selSemId, selSemName)

    // Students collection query
    fun buildStudentsQuery(): Query {
        var q: Query = db.collection("Students")
        if (selCourseId != "all")   q = q.whereEqualTo("courseId", selCourseId)
        if (selSessionId != "all")  q = q.whereEqualTo("sessionId", selSessionId)
        if (selYearId != "all")     q = q.whereEqualTo("yearName", selYearId)
        if (selSemId != "all")      q = q.whereEqualTo("semesterId", selSemId)
        return q
    }

    // StudentAttendance query
    fun buildAttendanceQuery(): Query {
        var q: Query = db.collection("StudentAttendance")
        if (selSemId != "all")      q = q.whereEqualTo("semesterId", selSemId)
        else if (selSessionId != "all") q = q.whereEqualTo("sessionId", selSessionId)
        return q
    }

    fun filterText(): String {
        val parts = mutableListOf<String>()
        if (selCourseId != "all")  parts.add(selCourseName)
        if (selSessionId != "all") parts.add(selSessionName)
        if (selYearId != "all")    parts.add(selYearName)
        if (selSemId != "all")     parts.add(selSemName)
        return if (parts.isEmpty()) "All Students" else parts.joinToString(" • ")
    }
}
