package com.example.academyhub.announcements

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.academyhub.databinding.ActivityAnnouncementRoleBinding

class AnnouncementRoleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnnouncementRoleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementRoleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Stagger entrance
        listOf(binding.btnNewAnnouncement, binding.btnHistory).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 28f
            v.animate().alpha(1f).translationY(0f)
                .setDuration(340).setStartDelay(160L + i * 80L)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
        }

        binding.btnNewAnnouncement.setOnClickListener {
            tapWithIndicator(it, binding.indicatorNew) {
                startActivity(Intent(this, CreateAnnouncementActivity::class.java))
            }
        }
        binding.btnHistory.setOnClickListener {
            tapWithIndicator(it, binding.indicatorHistory) {
                startActivity(Intent(this, AnnouncementHistoryActivity::class.java))
            }
        }
    }

    // Scale zoom + indicator line animation on tap
    private fun tapWithIndicator(card: View, indicator: View, action: () -> Unit) {
        // Scale card
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, "scaleX", 1f, 0.96f, 1f).apply { duration = 140 },
                ObjectAnimator.ofFloat(card, "scaleY", 1f, 0.96f, 1f).apply { duration = 140 }
            ); start()
        }
        // Animate indicator height
        indicator.visibility = View.VISIBLE
        val h = card.height
        ValueAnimator.ofInt(0, h).apply {
            duration = 200
            addUpdateListener { indicator.layoutParams.height = it.animatedValue as Int; indicator.requestLayout() }
            start()
        }
        card.postDelayed({
            action()
            // Reset indicator after 400ms
            card.postDelayed({
                ValueAnimator.ofInt(indicator.height, 0).apply {
                    duration = 160
                    addUpdateListener { indicator.layoutParams.height = it.animatedValue as Int; indicator.requestLayout() }
                    doOnEnd { indicator.visibility = View.INVISIBLE }
                    start()
                }
            }, 400)
        }, 120)
    }

    private fun ValueAnimator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { action() }
        })
    }
}
