package com.example.academyhub.reports

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityReportsBinding

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupCards()
        animateEntrance()
    }

    private fun setupCards() {
        // Fees Report — scale zoom + indicator on click
        binding.cardFeesReport.setOnClickListener {
            animateCardClick(it, binding.indicatorFees, Color.parseColor("#075E54"))
            it.postDelayed({ startActivity(Intent(this, FeesReportActivity::class.java)) }, 200)
        }

        // Attendance Report
        binding.cardAttendanceReport.setOnClickListener {
            animateCardClick(it, binding.indicatorAttendance, Color.parseColor("#1565C0"))
            it.postDelayed({ startActivity(Intent(this, AttendanceReportActivity::class.java)) }, 200)
        }

        // Student Report
        binding.cardStudentReport.setOnClickListener {
            animateCardClick(it, binding.indicatorStudent, Color.parseColor("#E65100"))
            it.postDelayed({ startActivity(Intent(this, StudentReportActivity::class.java)) }, 200)
        }
    }

    // Scale zoom + indicator bar appear
    private fun animateCardClick(card: View, indicator: View, indicatorColor: Int) {
        // Scale zoom: 1 → 1.04 → 1
        ObjectAnimator.ofFloat(card, "scaleX", 1f, 1.04f, 1f).apply { duration = 220; start() }
        ObjectAnimator.ofFloat(card, "scaleY", 1f, 1.04f, 1f).apply { duration = 220; start() }

        // Indicator flash
        indicator.visibility = View.VISIBLE
        indicator.setBackgroundColor(indicatorColor)
        indicator.alpha = 0f
        indicator.animate().alpha(1f).setDuration(160).start()
        indicator.postDelayed({ indicator.animate().alpha(0f).setDuration(300).withEndAction {
            indicator.visibility = View.INVISIBLE
        }.start() }, 300)
    }

    // Stagger slide-up entrance for each card
    private fun animateEntrance() {
        val cards = listOf(binding.cardFeesReport, binding.cardAttendanceReport, binding.cardStudentReport)
        cards.forEachIndexed { i, card ->
            card.alpha = 0f; card.translationY = 30f
            card.animate().alpha(1f).translationY(0f)
                .setDuration(280).setStartDelay(i * 80L)
                .setInterpolator(OvershootInterpolator(0.6f)).start()
        }
    }
}
