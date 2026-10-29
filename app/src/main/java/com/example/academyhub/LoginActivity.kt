@file:Suppress("HARDCODED_TEXT", "DEPRECATION")

package com.example.academyhub

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityLoginBinding
import com.example.academyhub.student.RegisterRoleActivity
import com.example.academyhub.utils.FcmTokenHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var verificationId = ""
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countDownTimer: CountDownTimer? = null

    private lateinit var otpBoxes: List<EditText>
    private lateinit var otpFrames: List<FrameLayout>

    private val TAG = "LoginActivity"
    private var navigated = false // ✅ Double navigation prevent

    // ✅ Admin phone numbers
    private val adminPhones = listOf(
        "9979759935",
        "7383109419"
    )

    // ✅ Admin names — phone સાથે match
    private val adminNames = mapOf(
        "9979759935" to "Darshan Lad",
        "7383109419" to "Other Admin"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth      = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        auth.firebaseAuthSettings.setAppVerificationDisabledForTesting(false)

        if (auth.currentUser != null) {
            checkRoleAndNavigate(auth.currentUser!!.uid)
            return
        }

        otpBoxes = listOf(
            binding.etOtp1, binding.etOtp2, binding.etOtp3,
            binding.etOtp4, binding.etOtp5, binding.etOtp6
        )
        otpFrames = listOf(
            binding.frameOtp1, binding.frameOtp2, binding.frameOtp3,
            binding.frameOtp4, binding.frameOtp5, binding.frameOtp6
        )

        setupOtpBoxes()
        showPhoneStep()
        startLogoAnimation()

        binding.cardRegister.setOnClickListener {
            val intent = Intent(this, RegisterRoleActivity::class.java)
            intent.putExtra("fromRegister", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.cardHelp.setOnClickListener { showHelpBottomSheet() }

        binding.btnSendOtp.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (phone.length != 10) {
                binding.etPhone.error = "Please enter a valid 10-digit number"
                return@setOnClickListener
            }
            Log.d(TAG, "Attempting to send OTP to: +91$phone")
            checkUserBeforeOtp(phone)
        }

        binding.btnVerifyOtp.setOnClickListener {
            val otp = getOtp()
            if (otp.length != 6) {
                Toast.makeText(this, "Please enter the complete 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyOtp(otp)
        }

        binding.tvResendOtp.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (resendToken != null) {
                Log.d(TAG, "Resending OTP to: +91$phone")
                resendOtp("+91$phone")
            }
        }

        binding.tvChangeNumber.setOnClickListener {
            countDownTimer?.cancel()
            showPhoneStep()
        }
    }

    // ── Help Center ───────────────────────────────────────────────────────────
    private fun showHelpBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_help_center, null)

        view.findViewById<View>(R.id.itemOtpNotReceived).setOnClickListener {
            Toast.makeText(this, "OTP not received? Make sure your number is correct and has network signal.", Toast.LENGTH_LONG).show()
        }
        view.findViewById<View>(R.id.itemLoginProblem).setOnClickListener {
            Toast.makeText(this, "Login problems? Contact your institute administrator for help.", Toast.LENGTH_LONG).show()
        }
        view.findViewById<View>(R.id.itemNewRegistration).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, RegisterRoleActivity::class.java)
            intent.putExtra("fromRegister", true)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        view.findViewById<View>(R.id.itemContactAdmin).setOnClickListener {
            Toast.makeText(this, "Contact: support@academyhub.com\nPhone: +91 98765 43210", Toast.LENGTH_LONG).show()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ── Check user before OTP ─────────────────────────────────────────────────
    private fun checkUserBeforeOtp(phone: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSendOtp.isEnabled   = false

        if (adminPhones.contains(phone)) {
            Log.d(TAG, "⭐ Admin phone detected!")
            Toast.makeText(this, "Admin login detected ✓", Toast.LENGTH_SHORT).show()
            createAdminInDatabase(phone)
            sendOtp("+91$phone")
            return
        }

        firestore.collection("Students")
            .whereEqualTo("mobile", phone).limit(1).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    Log.d(TAG, "User found in Students collection")
                    sendOtp("+91$phone")
                } else {
                    Log.d(TAG, "Not in Students, checking Admins...")
                    checkAdminWithPhone(phone)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking Students", e)
                checkAdminWithPhone(phone)
            }
    }

    // ── Admin DB create/update — Auth UID thi (FIXED) ────────────────────────
    private fun createAdminInDatabase(phone: String) {
        // OTP verify thaya PAHELA call thay chhe atle uid nathi male
        // Actual DB write ensureAdminInDatabaseByUid() ma Auth UID thi thase
        Log.d(TAG, "createAdminInDatabase: DB write will happen after OTP verify")
    }

    // ── Auth UID thi admin DB ma write karo ───────────────────────────────────
    private fun ensureAdminInDatabaseByUid(uid: String, phone: String) {
        val db        = FirebaseDatabase.getInstance().getReference("Users")
        val adminName = adminNames[phone] ?: "Admin"
        db.child(uid).get()
            .addOnSuccessListener { snapshot ->
                val updates = mutableMapOf<String, Any>()
                if (!snapshot.exists() || snapshot.child("role").value?.toString() != "admin") {
                    updates["id"]    = uid
                    updates["phone"] = phone
                    updates["name"]  = snapshot.child("name").value?.toString()?.takeIf { it.isNotEmpty() } ?: adminName
                    updates["role"]  = "admin"
                    db.child(uid).updateChildren(updates)
                    Log.d(TAG, "✅ Admin DB entry ensured for UID: $uid")
                } else {
                    Log.d(TAG, "✅ Admin already correct in DB for UID: $uid")
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error in ensureAdminInDatabaseByUid", e) }
    }

    private fun checkAdminWithPhone(phone: String) {
        FirebaseDatabase.getInstance().getReference("Users")
            .orderByChild("phone").equalTo(phone).limitToFirst(1).get()
            .addOnSuccessListener { snap1 ->
                if (snap1.exists()) {
                    sendOtp("+91$phone")
                } else {
                    FirebaseDatabase.getInstance().getReference("Users")
                        .orderByChild("phone").equalTo("+91$phone").limitToFirst(1).get()
                        .addOnSuccessListener { snap2 ->
                            binding.progressBar.visibility = View.GONE
                            binding.btnSendOtp.isEnabled   = true
                            if (snap2.exists()) {
                                sendOtp("+91$phone")
                            } else {
                                showRegistrationDialog(phone)
                            }
                        }
                        .addOnFailureListener { e ->
                            binding.progressBar.visibility = View.GONE
                            binding.btnSendOtp.isEnabled   = true
                            Toast.makeText(this, "Connection error. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnSendOtp.isEnabled   = true
                Toast.makeText(this, "Connection error. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRegistrationDialog(phone: String) {
        AlertDialog.Builder(this)
            .setTitle("Number Not Registered")
            .setMessage("This number is not registered.\nPlease register first to continue.")
            .setPositiveButton("Register Now") { _, _ ->
                val intent = Intent(this, RegisterRoleActivity::class.java)
                intent.putExtra("fromRegister", true)
                intent.putExtra("phone", phone)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── OTP ───────────────────────────────────────────────────────────────────
    private fun sendOtp(phoneNumber: String) {
        Log.d(TAG, "sendOtp called for: $phoneNumber")
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSendOtp.isEnabled   = false

        try {
            PhoneAuthProvider.verifyPhoneNumber(
                PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception while calling verifyPhoneNumber", e)
            binding.progressBar.visibility = View.GONE
            binding.btnSendOtp.isEnabled   = true
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d(TAG, "Auto verification successful")
            signInWithCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            binding.progressBar.visibility = View.GONE
            binding.btnSendOtp.isEnabled   = true
            Log.e(TAG, "onVerificationFailed: ${e.message}", e)

            val errorMessage = when {
                e.message?.contains("INVALID_APP_CREDENTIAL", ignoreCase = true) == true ->
                    "SHA key અથવા google-services.json સમસ્યા છે."
                e.message?.contains("TOO_MANY_REQUESTS", ignoreCase = true) == true ->
                    "ઘણી વાર try કર્યું. થોડા સમય પછી try કરો."
                e.message?.contains("QUOTA_EXCEEDED", ignoreCase = true) == true ->
                    "આજની SMS limit પૂરી થઈ ગઈ. કાલે try કરો."
                else -> "Error: ${e.message}"
            }
            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(verificationID: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d(TAG, "onCodeSent: $verificationID")
            verificationId = verificationID
            resendToken    = token
            binding.progressBar.visibility = View.GONE
            showOtpStep()
        }
    }

    private fun verifyOtp(otp: String) {
        if (verificationId.isEmpty()) {
            Toast.makeText(this, "Session expired. Please request OTP again.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        signInWithCredential(PhoneAuthProvider.getCredential(verificationId, otp))
    }

    private fun resendOtp(phoneNumber: String) {
        if (resendToken == null) return
        binding.progressBar.visibility = View.VISIBLE
        try {
            PhoneAuthProvider.verifyPhoneNumber(
                PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(callbacks)
                    .setForceResendingToken(resendToken!!)
                    .build()
            )
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result.user?.uid ?: return@addOnCompleteListener
                    checkRoleAndNavigate(uid)
                } else {
                    binding.progressBar.visibility = View.GONE
                    Log.e(TAG, "Sign in failed", task.exception)
                    Toast.makeText(this, "Incorrect OTP. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ── Role check ────────────────────────────────────────────────────────────
    private fun checkRoleAndNavigate(uid: String) {
        // ✅ Phone number ને normalize કરીએ - બંને variants check કરીએ
        val fullPhone = auth.currentUser?.phoneNumber ?: return
        val authPhone = fullPhone.removePrefix("+91")
        Log.d(TAG, "Checking role for UID: $uid, Phone: $authPhone (Full: $fullPhone)")

        // ✅ Admin check — phone number thi detect, DB Auth UID thi write
        if (adminPhones.contains(authPhone) || adminPhones.contains(fullPhone)) {
            Log.d(TAG, "⭐ Admin detected! UID: $uid")

            // Auth UID thi DB ma correct entry ensure karo
            ensureAdminInDatabaseByUid(uid, authPhone)

            // DB thi name fetch karo — Auth UID thi (FIXED)
            FirebaseDatabase.getInstance().getReference("Users")
                .child(uid).get()
                .addOnSuccessListener { snap ->
                    binding.progressBar.visibility = View.GONE
                    val adminName = snap.child("name").value?.toString()
                        ?.takeIf { it.isNotEmpty() }
                        ?: adminNames[authPhone]
                        ?: "Admin"
                    Log.d(TAG, "Admin name: $adminName, UID: $uid")
                    proceedNavigation(role = "admin", name = adminName, phone = authPhone, id = uid)
                }
                .addOnFailureListener {
                    binding.progressBar.visibility = View.GONE
                    proceedNavigation(role = "admin", name = adminNames[authPhone] ?: "Admin", phone = authPhone, id = uid)
                }
            return
        }

        // Student check — બંને phone formats સાથે try કરીએ
        val phoneVariants = listOf(authPhone, "+91$authPhone", fullPhone)
        checkStudentWithPhones(phoneVariants, uid, 0)
    }

    private fun checkStudentWithPhones(phoneVariants: List<String>, uid: String, index: Int) {
        if (index >= phoneVariants.size) {
            // કોઈ પણ variant માં student નથી મળ્યો — fallback to Users DB
            FirebaseDatabase.getInstance().getReference("Users").child(uid).get()
                .addOnSuccessListener { snap ->
                    binding.progressBar.visibility = View.GONE
                    if (snap.exists()) {
                        proceedNavigation(
                            role  = snap.child("role").value?.toString()  ?: "student",
                            name  = snap.child("name").value?.toString()  ?: "",
                            phone = snap.child("phone").value?.toString() ?: "",
                            id    = uid
                        )
                    } else {
                        auth.signOut()
                        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        showPhoneStep()
                        Toast.makeText(this, "User record not found. Please register.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        val currentPhone = phoneVariants[index]
        Log.d(TAG, "Checking Students collection with phone: $currentPhone")

        firestore.collection("Students")
            .whereEqualTo("mobile", currentPhone).limit(1).get()
            .addOnSuccessListener { studentQuery ->
                if (!studentQuery.isEmpty) {
                    binding.progressBar.visibility = View.GONE
                    val doc = studentQuery.documents[0]
                    proceedNavigation(
                        role         = "student",
                        name         = doc.getString("name") ?: "",
                        phone        = doc.getString("mobile") ?: "",
                        id           = doc.id,
                        studentDocId = doc.id,
                        semesterId   = doc.getString("semesterId") ?: ""
                    )
                } else {
                    // આ variant માં નથી મળ્યો, next variant try કરો
                    checkStudentWithPhones(phoneVariants, uid, index + 1)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking student with phone $currentPhone", e)
                checkStudentWithPhones(phoneVariants, uid, index + 1)
            }
    }

    private fun proceedNavigation(
        role: String, name: String, phone: String, id: String,
        studentDocId: String = "", semesterId: String = ""
    ) {
        // ✅ KEY FIX: Double navigation prevent kare
        if (navigated) {
            Log.w(TAG, "proceedNavigation: already navigated, ignoring duplicate call")
            return
        }
        navigated = true

        Log.d(TAG, "Role: $role | Name: $name | Phone: $phone")

        getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().apply {
            putString("userRole",     role)
            putString("userName",     name)
            putString("userPhone",    phone)
            putString("userId",       id)
            putString("studentDocId", studentDocId)
            putString("semesterId",   semesterId)
            apply()
        }

        FcmTokenHelper.saveToken { Log.d(TAG, "FCM save: $it") }
        FcmTokenHelper.subscribeToAll { Log.d(TAG, "Subscribe all: $it") }
        if (role == "student" && semesterId.isNotEmpty())
            FcmTokenHelper.subscribeToSemester(semesterId) { Log.d(TAG, "Subscribe semester: $it") }

        val intent = if (role == "admin")
            Intent(this, AdminDashboardActivity::class.java)
        else
            Intent(this, UserDashboardActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ── UI steps ──────────────────────────────────────────────────────────────
    private fun showPhoneStep() {
        binding.layoutPhoneStep.visibility     = View.VISIBLE
        binding.layoutOtpStep.visibility       = View.GONE
        binding.layoutBottomActions.visibility = View.VISIBLE
        binding.progressBar.visibility         = View.GONE
        binding.btnSendOtp.isEnabled           = true
    }

    private fun showOtpStep() {
        binding.layoutPhoneStep.visibility     = View.GONE
        binding.layoutOtpStep.visibility       = View.VISIBLE
        binding.layoutBottomActions.visibility = View.GONE

        val phone = binding.etPhone.text.toString()
        binding.tvOtpSentTo.text = "We've sent a 6-digit code to +91 ••••••${phone.takeLast(4)}"

        clearOtpBoxes()
        startResendTimer()

        binding.layoutOtpStep.alpha = 0f
        binding.layoutOtpStep.translationY = 40f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutOtpStep, "alpha", 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(binding.layoutOtpStep, "translationY", 40f, 0f)
                    .apply { duration = 500; interpolator = DecelerateInterpolator() }
            )
            start()
        }
    }

    // ── OTP boxes ─────────────────────────────────────────────────────────────
    private fun getOtp() = otpBoxes.joinToString("") { it.text.toString() }

    private fun clearOtpBoxes() {
        otpBoxes.forEach { it.setText("") }
        refreshAllCircles()
        otpBoxes[0].requestFocus()
    }

    private fun refreshAllCircles() {
        val focusedIndex = otpBoxes.indexOfFirst { it.isFocused }
        otpFrames.forEachIndexed { i, frame ->
            val isActive = i == focusedIndex || otpBoxes[i].text.isNotEmpty()
            frame.setBackgroundResource(
                if (isActive) R.drawable.bg_otp_circle_active
                else          R.drawable.bg_otp_circle_empty
            )
        }
    }

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, editText ->
            editText.setOnFocusChangeListener { _, _ -> refreshAllCircles() }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    refreshAllCircles()
                    if (s?.length == 1 && index < otpBoxes.size - 1)
                        otpBoxes[index + 1].requestFocus()
                    if (index == otpBoxes.size - 1 && getOtp().length == 6)
                        verifyOtp(getOtp())
                }
            })
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    editText.text.isEmpty() && index > 0
                ) {
                    otpBoxes[index - 1].apply { requestFocus(); setText("") }
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    // ── Resend timer ──────────────────────────────────────────────────────────
    private fun startResendTimer() {
        binding.tvResendOtp.isEnabled = false
        binding.tvResendOtp.text      = "Resend in 60s"
        countDownTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(ms: Long) { binding.tvResendOtp.text = "Resend in ${ms / 1000}s" }
            override fun onFinish() { binding.tvResendOtp.isEnabled = true; binding.tvResendOtp.text = "Resend OTP" }
        }.start()
    }

    // ── Logo animation ────────────────────────────────────────────────────────
    private fun startLogoAnimation() {
        binding.layoutLogo.alpha = 0f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutLogo, "alpha", 0f, 1f)
                    .apply { duration = 900; interpolator = DecelerateInterpolator() },
                ObjectAnimator.ofFloat(binding.frameLogo, "scaleX", 0.75f, 1f)
                    .apply { duration = 900; interpolator = DecelerateInterpolator() },
                ObjectAnimator.ofFloat(binding.frameLogo, "scaleY", 0.75f, 1f)
                    .apply { duration = 900; interpolator = DecelerateInterpolator() }
            )
            start()
        }
        binding.layoutPhoneStep.alpha = 0f
        binding.layoutPhoneStep.translationY = 50f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.layoutPhoneStep, "alpha", 0f, 1f)
                    .apply { duration = 700; startDelay = 400; interpolator = DecelerateInterpolator() },
                ObjectAnimator.ofFloat(binding.layoutPhoneStep, "translationY", 50f, 0f)
                    .apply { duration = 700; startDelay = 400; interpolator = DecelerateInterpolator() }
            )
            start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.ringOuter, "scaleX", 1f, 1.18f, 1f)
                    .apply { duration = 2400; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.ringOuter, "scaleY", 1f, 1.18f, 1f)
                    .apply { duration = 2400; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.ringOuter, "alpha", 0.2f, 0.5f, 0.2f)
                    .apply { duration = 2400; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.ringMiddle, "scaleX", 1f, 1.1f, 1f)
                    .apply { duration = 2400; startDelay = 350; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.ringMiddle, "scaleY", 1f, 1.1f, 1f)
                    .apply { duration = 2400; startDelay = 350; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.ringMiddle, "alpha", 0.35f, 0.6f, 0.35f)
                    .apply { duration = 2400; startDelay = 350; repeatCount = ObjectAnimator.INFINITE },
                ObjectAnimator.ofFloat(binding.frameLogo, "translationY", 0f, -5f, 0f)
                    .apply { duration = 2800; startDelay = 900; repeatCount = ObjectAnimator.INFINITE }
            )
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}