package com.example.academyhub.student

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.academyhub.R
import com.example.academyhub.databinding.ActivityStudentListBinding
import com.example.academyhub.models.Student
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedReader
import java.io.InputStreamReader

class StudentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentListBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: StudentAdapter

    private val allStudents      = mutableListOf<Student>()
    private val filteredStudents = mutableListOf<Student>()

    // Filters from StudentRoleActivity
    private var filterSessionId    = ""; private var filterSessionName  = ""
    private var filterCourseId     = ""; private var filterCourseName   = ""
    private var filterYearId       = ""; private var filterYearName     = ""
    private var filterSemesterId   = ""; private var filterSemesterName = ""
    private var filterBatchId      = ""; private var filterBatchName    = ""
    private var fromRoleSelect     = false

    private var fabMenuOpen = false
    private val TAG = "StudentListActivity"

    // ── Sort state ────────────────────────────────────────────
    private var currentSort = SortMode.NONE
    enum class SortMode { NONE, NAME_AZ, NAME_ZA, ROLL_AZ, ROLL_ZA }

    // ── Multi-select state ────────────────────────────────────
    private var isMultiSelectMode = false
    private val selectedIds = mutableSetOf<String>()

    // ── CSV Picker ────────────────────────────────────────────
    private val csvPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { parseCsvAndUpload(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        fromRoleSelect     = intent.getBooleanExtra("fromRoleSelect", false)
        filterSessionId    = intent.getStringExtra("filterSessionId")    ?: ""
        filterSessionName  = intent.getStringExtra("filterSessionName")  ?: ""
        filterCourseId     = intent.getStringExtra("filterCourseId")     ?: ""
        filterCourseName   = intent.getStringExtra("filterCourseName")   ?: ""
        filterYearId       = intent.getStringExtra("filterYearId")       ?: ""
        filterYearName     = intent.getStringExtra("filterYearName")     ?: ""
        filterSemesterId   = intent.getStringExtra("filterSemesterId")   ?: ""
        filterSemesterName = intent.getStringExtra("filterSemesterName") ?: ""
        filterBatchId      = intent.getStringExtra("filterBatchId")      ?: ""
        filterBatchName    = intent.getStringExtra("filterBatchName")    ?: ""

        if (fromRoleSelect && filterCourseName.isNotEmpty()) {
            binding.tvGroupBadge.visibility = View.VISIBLE
            binding.tvGroupBadge.text =
                "$filterSessionName  \u203a  $filterCourseName  \u203a  $filterYearName  \u203a  $filterSemesterName  \u203a  $filterBatchName"
        }

        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        setupFabMenu()
        setupClickListeners()
        loadStudents()
    }

    // ──────────────────────────────────────────────────────────
    //  RecyclerView
    // ──────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = StudentAdapter(
            students    = filteredStudents,
            onItemClick = { student ->
                if (isMultiSelectMode) {
                    toggleSelect(student)
                } else {
                    startActivity(Intent(this, StudentDetailsActivity::class.java)
                        .putExtra("studentId", student.id))
                }
            },
            onMoreClick = { student, anchor ->
                if (!isMultiSelectMode) showMoreOptions(student, anchor)
            }
        )
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        binding.rvStudents.adapter       = adapter
        binding.rvStudents.itemAnimator  = null
    }

    // ──────────────────────────────────────────────────────────
    //  Search
    // ──────────────────────────────────────────────────────────
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
        ObjectAnimator.ofFloat(binding.rvStudents, "alpha", 1f, 0f).apply { duration = 100; start() }
        binding.rvStudents.postDelayed({
            applyFilterAndSort(query)
            binding.rvStudents.translationY = 14f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(binding.rvStudents, "alpha", 0f, 1f).apply { duration = 190 },
                    ObjectAnimator.ofFloat(binding.rvStudents, "translationY", 14f, 0f).apply {
                        duration = 210; interpolator = DecelerateInterpolator()
                    }
                )
                start()
            }
        }, 110)
    }

    // ──────────────────────────────────────────────────────────
    //  Natural sort comparator — "1,2,3...9,10,11" (not "1,10,11,2")
    //  Numbers compare numerically, letters compare alphabetically
    // ──────────────────────────────────────────────────────────
    private fun naturalOrder(ascending: Boolean): Comparator<Student> {
        return Comparator { a, b ->
            val result = naturalCompare(a.rollNumber, b.rollNumber)
            if (ascending) result else -result
        }
    }

    private fun naturalCompare(s1: String, s2: String): Int {
        var i = 0; var j = 0
        val a = s1.lowercase(); val b = s2.lowercase()
        while (i < a.length && j < b.length) {
            val ca = a[i]; val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                // Extract full number from both strings and compare numerically
                var numA = ""; var numB = ""
                while (i < a.length && a[i].isDigit()) { numA += a[i]; i++ }
                while (j < b.length && b[j].isDigit()) { numB += b[j]; j++ }
                val cmp = numA.toLong().compareTo(numB.toLong())
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.compareTo(cb)
                if (cmp != 0) return cmp
                i++; j++
            }
        }
        return a.length - b.length
    }

    // ──────────────────────────────────────────────────────────
    //  Sort + Filter combined apply
    // ──────────────────────────────────────────────────────────
    private fun applyFilterAndSort(query: String = binding.etSearch.text.toString()) {
        filteredStudents.clear()

        val q = query.trim().lowercase()
        val base = if (q.isBlank()) allStudents.toMutableList()
        else allStudents.filter {
            it.name.lowercase().contains(q) ||
                    it.rollNumber.lowercase().contains(q) ||
                    it.mobile.contains(q)
        }.toMutableList()

        val sorted = when (currentSort) {
            SortMode.NONE    -> base
            SortMode.NAME_AZ -> base.sortedBy   { it.name.lowercase() }
            SortMode.NAME_ZA -> base.sortedByDescending { it.name.lowercase() }
            // Natural sort: "1,2,3...10,11" — numbers numerically, letters alphabetically
            SortMode.ROLL_AZ -> base.sortedWith(naturalOrder(ascending = true))
            SortMode.ROLL_ZA -> base.sortedWith(naturalOrder(ascending = false))
        }

        filteredStudents.addAll(sorted)
        adapter.updateList(filteredStudents)
        updateEmptyState()
    }

    // ──────────────────────────────────────────────────────────
    //  SwipeRefresh
    // ──────────────────────────────────────────────────────────
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

    // ──────────────────────────────────────────────────────────
    //  FAB Menu
    // ──────────────────────────────────────────────────────────
    private fun setupFabMenu() {
        binding.fabMain.setOnClickListener { toggleFabMenu() }
        binding.fabScrim.setOnClickListener { closeFabMenu() }

        binding.fabAddStudent.setOnClickListener {
            closeFabMenu()
            val intent = Intent(this, AddStudentActivity::class.java)
            if (fromRoleSelect) {
                intent.putExtra("prefillMode",         true)
                intent.putExtra("prefillSessionId",    filterSessionId)
                intent.putExtra("prefillSessionName",  filterSessionName)
                intent.putExtra("prefillCourseId",     filterCourseId)
                intent.putExtra("prefillCourseName",   filterCourseName)
                intent.putExtra("prefillYearId",       filterYearId)
                intent.putExtra("prefillYearName",     filterYearName)
                intent.putExtra("prefillSemesterId",   filterSemesterId)
                intent.putExtra("prefillSemesterName", filterSemesterName)
            }
            startActivity(intent)
        }

        binding.fabUploadCsv.setOnClickListener {
            closeFabMenu()
            csvPickerLauncher.launch("text/*")
        }
    }

    private fun toggleFabMenu() { if (fabMenuOpen) closeFabMenu() else openFabMenu() }

    private fun openFabMenu() {
        fabMenuOpen = true
        binding.fabMain.animate().rotation(45f).setDuration(220)
            .setInterpolator(OvershootInterpolator(1.5f)).start()

        binding.fabScrim.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(binding.fabScrim, "alpha", 0f, 1f).apply { duration = 200; start() }

        binding.fabMenuAddStudent.visibility = View.VISIBLE
        binding.fabMenuAddStudent.translationY = 30f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.fabMenuAddStudent, "alpha", 0f, 1f).apply { duration = 200 },
                ObjectAnimator.ofFloat(binding.fabMenuAddStudent, "translationY", 30f, 0f).apply {
                    duration = 220; interpolator = DecelerateInterpolator()
                }
            )
            start()
        }

        binding.fabMenuUploadCsv.visibility = View.VISIBLE
        binding.fabMenuUploadCsv.translationY = 30f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.fabMenuUploadCsv, "alpha", 0f, 1f).apply { duration = 200 },
                ObjectAnimator.ofFloat(binding.fabMenuUploadCsv, "translationY", 30f, 0f).apply {
                    duration = 220; interpolator = DecelerateInterpolator()
                }
            )
            startDelay = 60
            start()
        }
    }

    private fun closeFabMenu() {
        fabMenuOpen = false
        binding.fabMain.animate().rotation(0f).setDuration(200).start()

        ObjectAnimator.ofFloat(binding.fabScrim, "alpha", 1f, 0f).apply {
            duration = 160
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { binding.fabScrim.visibility = View.GONE }
            })
            start()
        }
        listOf(binding.fabMenuAddStudent, binding.fabMenuUploadCsv).forEach { v ->
            ObjectAnimator.ofFloat(v, "alpha", 1f, 0f).apply {
                duration = 140
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { v.visibility = View.GONE }
                })
                start()
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Click Listeners
    // ──────────────────────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (isMultiSelectMode) exitMultiSelectMode()
            else { finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right) }
        }

        // Sort icon — PopupMenu
        binding.btnSort.setOnClickListener { showSortMenu() }

        // Multi-select icon
        binding.btnMultiSelect.setOnClickListener {
            if (isMultiSelectMode) exitMultiSelectMode() else enterMultiSelectMode()
        }

        // Delete selected
        binding.btnDeleteSelected.setOnClickListener {
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "No student selected!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBulkDeleteConfirmation()
        }

        // Select All / Deselect All
        binding.btnSelectAll.setOnClickListener {
            if (selectedIds.size == filteredStudents.size) selectedIds.clear()
            else filteredStudents.forEach { selectedIds.add(it.id) }
            adapter.setSelectedIds(selectedIds)
            updateMultiSelectToolbar()
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Sort Menu
    // ──────────────────────────────────────────────────────────
    private fun showSortMenu() {
        val popup = PopupMenu(this, binding.btnSort)
        popup.menu.apply {
            add(0, 1, 0, if (currentSort == SortMode.NAME_AZ) "✓  Name  A → Z" else "Name  A → Z")
            add(0, 2, 1, if (currentSort == SortMode.NAME_ZA) "✓  Name  Z → A" else "Name  Z → A")
            add(0, 3, 2, if (currentSort == SortMode.ROLL_AZ) "✓  Roll No  A → Z" else "Roll No  A → Z")
            add(0, 4, 3, if (currentSort == SortMode.ROLL_ZA) "✓  Roll No  Z → A" else "Roll No  Z → A")
            if (currentSort != SortMode.NONE) add(0, 5, 4, "Clear Sort")
        }
        popup.setOnMenuItemClickListener { item ->
            currentSort = when (item.itemId) {
                1 -> SortMode.NAME_AZ; 2 -> SortMode.NAME_ZA
                3 -> SortMode.ROLL_AZ; 4 -> SortMode.ROLL_ZA
                else -> SortMode.NONE
            }
            // Sort icon tint — active = teal, inactive = white
            binding.btnSort.setColorFilter(
                Color.parseColor(if (currentSort != SortMode.NONE) "#26A69A" else "#FFFFFF")
            )
            applyFilterAndSort()
            true
        }
        popup.show()
    }

    // ──────────────────────────────────────────────────────────
    //  Multi-Select Mode
    // ──────────────────────────────────────────────────────────
    private fun enterMultiSelectMode() {
        isMultiSelectMode = true
        selectedIds.clear()
        adapter.setMultiSelectMode(true, selectedIds)

        binding.multiSelectToolbar.visibility = View.VISIBLE
        binding.fabMain.visibility            = View.GONE
        binding.multiSelectToolbar.alpha      = 0f
        binding.multiSelectToolbar.animate().alpha(1f).setDuration(220).start()

        updateMultiSelectToolbar()
        Toast.makeText(this, "Tap students to select — then delete", Toast.LENGTH_SHORT).show()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedIds.clear()
        adapter.setMultiSelectMode(false, selectedIds)

        binding.multiSelectToolbar.animate().alpha(0f).setDuration(180).withEndAction {
            binding.multiSelectToolbar.visibility = View.GONE
        }.start()
        binding.fabMain.visibility = View.VISIBLE
    }

    private fun toggleSelect(student: Student) {
        if (selectedIds.contains(student.id)) selectedIds.remove(student.id)
        else selectedIds.add(student.id)
        adapter.setSelectedIds(selectedIds)
        updateMultiSelectToolbar()
    }

    private fun updateMultiSelectToolbar() {
        val count = selectedIds.size
        binding.tvSelectedCount.text = if (count == 0) "Select students" else "$count selected"
        binding.btnSelectAll.text    = if (count == filteredStudents.size && filteredStudents.isNotEmpty()) "Deselect All" else "Select All"
        binding.btnDeleteSelected.isEnabled = count > 0
        binding.btnDeleteSelected.alpha     = if (count > 0) 1f else 0.4f
    }

    private fun showBulkDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete ${selectedIds.size} Students")
            .setMessage("Are you sure you want to permanently delete ${selectedIds.size} students?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ -> doBulkDelete() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doBulkDelete() {
        binding.progressBar.visibility = View.VISIBLE
        val idsToDelete = selectedIds.toList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                idsToDelete.chunked(400).forEach { chunk ->
                    val batch: WriteBatch = firestore.batch()
                    chunk.forEach { id -> batch.delete(firestore.collection("Students").document(id)) }
                    batch.commit().await()
                }
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@StudentListActivity,
                        "✓ ${idsToDelete.size} students deleted successfully!", Toast.LENGTH_LONG).show()
                    exitMultiSelectMode()
                    loadStudents()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@StudentListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Load Students
    // ──────────────────────────────────────────────────────────
    private fun loadStudents(fromSwipe: Boolean = false) {
        if (!fromSwipe) binding.progressBar.visibility = View.VISIBLE

        var query: com.google.firebase.firestore.Query = firestore.collection("Students")
        if (fromRoleSelect && filterSessionId.isNotEmpty()) {
            query = query.whereEqualTo("sessionId", filterSessionId)
        }

        query.get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false

                allStudents.clear()
                for (doc in docs) {
                    val s = doc.toObject(Student::class.java)
                    if (fromRoleSelect) {
                        if (filterCourseId.isNotEmpty()   && s.courseId   != filterCourseId)   continue
                        if (filterYearId.isNotEmpty()     && s.yearId     != filterYearId)     continue
                        if (filterSemesterId.isNotEmpty() && s.semesterId != filterSemesterId) continue
                        if (filterBatchId.isNotEmpty()    && s.batchId    != filterBatchId)    continue
                    }
                    allStudents.add(s)
                }

                if (currentSort == SortMode.NONE) allStudents.sortBy { it.name }

                animateCountBadge(allStudents.size)
                applyFilterAndSort()
                if (!fromSwipe) adapter.animateEntrance(binding.rvStudents)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility    = View.GONE
                binding.swipeRefresh.isRefreshing = false
                Log.e(TAG, "Load error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun animateCountBadge(target: Int) {
        ValueAnimator.ofInt(0, target).apply {
            duration = 700; interpolator = DecelerateInterpolator()
            addUpdateListener { binding.tvStudentCount.text = "${it.animatedValue}" }
            start()
        }
    }

    private fun updateEmptyState() {
        val empty = filteredStudents.isEmpty()
        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvStudents.visibility  = if (empty) View.GONE   else View.VISIBLE
    }

    // ──────────────────────────────────────────────────────────
    //  More Options
    // ──────────────────────────────────────────────────────────
    private fun showMoreOptions(student: Student, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, 1, 0, "View Details")
            add(0, 2, 1, "Edit Student")
            add(0, 3, 2, if (student.status == "blocked") "Unblock Student" else "Block Student")
            add(0, 4, 3, "Delete Student")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { startActivity(Intent(this, StudentDetailsActivity::class.java).putExtra("studentId", student.id)); true }
                2 -> { startActivity(Intent(this, AddStudentActivity::class.java).putExtra("studentId", student.id).putExtra("isEdit", true)); true }
                3 -> { showBlockConfirmation(student); true }
                4 -> { showDeleteConfirmation(student); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showBlockConfirmation(student: Student) {
        val isBlocked = student.status == "blocked"
        AlertDialog.Builder(this)
            .setTitle(if (isBlocked) "Unblock Student" else "Block Student")
            .setMessage(if (isBlocked) "Unblock ${student.name}?" else "Block ${student.name}?")
            .setPositiveButton(if (isBlocked) "Unblock" else "Block") { _, _ ->
                firestore.collection("Students").document(student.id)
                    .update("status", if (isBlocked) "active" else "blocked")
                    .addOnSuccessListener { loadStudents() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDeleteConfirmation(student: Student) {
        AlertDialog.Builder(this)
            .setTitle("Delete Student")
            .setMessage("Permanently delete ${student.name}?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("Students").document(student.id).delete()
                    .addOnSuccessListener { loadStudents() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ──────────────────────────────────────────────────────────
    //  CSV Upload
    // ──────────────────────────────────────────────────────────
    private fun parseCsvAndUpload(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Upload CSV")
            .setMessage("Students from this CSV file will be added automatically. Proceed?")
            .setPositiveButton("Upload") { _, _ -> doCsvUpload(uri) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun doCsvUpload(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
                val lines = reader.readLines(); reader.close()

                if (lines.isEmpty()) {
                    runOnUiThread { binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@StudentListActivity, "CSV file is empty!", Toast.LENGTH_SHORT).show() }
                    return@launch
                }

                val headers = lines[0].split(",").map { it.trim().lowercase().replace(" ", "") }
                val toAdd = mutableListOf<Student>()

                for (i in 1 until lines.size) {
                    val line = lines[i].trim(); if (line.isEmpty()) continue
                    val values = parseCSVLine(line)
                    fun get(vararg cols: String): String {
                        for (col in cols) {
                            val idx = headers.indexOf(col.lowercase().replace(" ", ""))
                            if (idx >= 0 && idx < values.size && values[idx].isNotEmpty()) return values[idx].trim()
                        }; return ""
                    }
                    val name = get("name", "studentname", "fullname"); if (name.isEmpty()) continue
                    val newId = firestore.collection("Students").document().id
                    toAdd.add(Student(id=newId, name=name, email=get("email","emailaddress"),
                        mobile=get("mobile","phone","mobileno"), gender=get("gender"),
                        dateOfBirth=get("dateofbirth","dob"), address=get("address"),
                        sessionId=filterSessionId, sessionName=filterSessionName,
                        courseId=filterCourseId, courseName=filterCourseName,
                        yearId=filterYearId, yearName=filterYearName,
                        semesterId=filterSemesterId, semesterName=filterSemesterName,
                        batchId=filterBatchId, batchName=filterBatchName,
                        rollNumber=get("rollnumber","roll","rollno"),
                        admissionDate=get("admissiondate","admission"),
                        status=get("status").ifEmpty{"active"}, feesStatus=get("feesstatus","fees").ifEmpty{"pending"},
                        createdAt=System.currentTimeMillis(), updatedAt=System.currentTimeMillis()))
                }

                if (toAdd.isEmpty()) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        AlertDialog.Builder(this@StudentListActivity)
                            .setTitle("Invalid CSV Format")
                            .setMessage(
                                "No valid student data found in the CSV file.\n\n" +
                                        "Your CSV file must have the following columns:\n\n" +
                                        "Required:\n" +
                                        "  • name\n" +
                                        "  • mobile\n" +
                                        "  • email\n" +
                                        "  • gender\n" +
                                        "  • rollnumber\n" +
                                        "  • dateofbirth\n" +
                                        "  • address\n\n" +
                                        "  • admissiondate\n" +
                                        "Optional:\n" +
                                        "  • status\n" +
                                        "  • feesstatus\n\n" +
                                        "Example first line:\n" +
                                        "name,mobile,email,gender,rollnumber,dateofbirth,address"
                            )
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@launch
                }

                // Fetch all existing mobile numbers from Firestore to check duplicates
                val existingSnap = firestore.collection("Students").get().await()
                val existingMobiles = existingSnap.documents
                    .mapNotNull { it.getString("mobile") }
                    .filter { it.isNotEmpty() }
                    .toSet()

                // Also check duplicates within the CSV itself
                val seenInCsv    = mutableSetOf<String>()
                val uniqueToAdd  = mutableListOf<Student>()
                val skippedNames = mutableListOf<String>()

                for (s in toAdd) {
                    val mob = s.mobile.trim()
                    when {
                        mob.isEmpty() -> {
                            // No mobile number — skip duplicate check and add
                            uniqueToAdd.add(s)
                        }
                        existingMobiles.contains(mob) -> {
                            // Already registered in Firestore — skip
                            skippedNames.add("${s.name} ($mob)")
                        }
                        seenInCsv.contains(mob) -> {
                            // Duplicate within the same CSV file — skip
                            skippedNames.add("${s.name} ($mob) [CSV duplicate]")
                        }
                        else -> {
                            seenInCsv.add(mob)
                            uniqueToAdd.add(s)
                        }
                    }
                }

                if (uniqueToAdd.isEmpty()) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        AlertDialog.Builder(this@StudentListActivity)
                            .setTitle("No Students Added")
                            .setMessage("All students in this CSV are already registered:\n\n${skippedNames.joinToString("\n")}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@launch
                }

                // Batch write — only unique students
                uniqueToAdd.chunked(400).forEach { chunk ->
                    val batch: WriteBatch = firestore.batch()
                    chunk.forEach { s -> batch.set(firestore.collection("Students").document(s.id), s) }
                    batch.commit().await()
                }

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    val msg = buildString {
                        append("✓ ${uniqueToAdd.size} students added successfully!")
                        if (skippedNames.isNotEmpty()) {
                            append("\n\n⚠ ${skippedNames.size} skipped (duplicate mobile):\n")
                            append(skippedNames.take(5).joinToString("\n"))
                            if (skippedNames.size > 5) append("\n...and ${skippedNames.size - 5} more")
                        }
                    }
                    AlertDialog.Builder(this@StudentListActivity)
                        .setTitle("Upload Complete")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                    loadStudents()
                }
            } catch (e: Exception) {
                runOnUiThread { binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@StudentListActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>(); var current = StringBuilder(); var inQuotes = false
        for (ch in line) { when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> { result.add(current.toString().trim()); current = StringBuilder() }
            else -> current.append(ch)
        }}
        result.add(current.toString().trim()); return result
    }

    override fun onResume() { super.onResume(); loadStudents() }

    override fun onBackPressed() {
        when {
            isMultiSelectMode -> exitMultiSelectMode()
            fabMenuOpen       -> closeFabMenu()
            else              -> super.onBackPressed()
        }
    }
}