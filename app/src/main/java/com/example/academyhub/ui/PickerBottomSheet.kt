package com.example.academyhub.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.R
import com.example.academyhub.databinding.BottomSheetPickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PickerBottomSheet(
    private val title: String,
    private val items: List<String>,
    private val selectedIndex: Int = -1,
    private val onPick: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPickerBinding? = null
    private val binding get() = _binding!!

    private val filteredItems = mutableListOf<Pair<Int, String>>() // original index + name
    private lateinit var adapter: PickerAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand bottom sheet fully
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Header
        binding.tvPickerTitle.text = title
        binding.tvPickerCount.text = "${items.size} items"

        // Setup list
        filteredItems.addAll(items.mapIndexed { i, name -> Pair(i, name) })
        adapter = PickerAdapter(filteredItems, selectedIndex) { originalIndex ->
            onPick(originalIndex)
            dismiss()
        }
        binding.rvPickerItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPickerItems.adapter = adapter

        // Stagger entrance for items
        binding.rvPickerItems.post { staggerItems() }

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterItems(s.toString())
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun filterItems(query: String) {
        filteredItems.clear()
        val filtered = items.mapIndexed { i, name -> Pair(i, name) }
            .filter { it.second.contains(query, ignoreCase = true) }
        filteredItems.addAll(filtered)
        adapter.notifyDataSetChanged()
        binding.layoutEmpty.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
        if (filteredItems.isNotEmpty()) staggerItems()
    }

    private fun staggerItems() {
        val lm = binding.rvPickerItems.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last  = lm.findLastVisibleItemPosition()
        for (i in first..last) {
            val child = lm.findViewByPosition(i) ?: continue
            val delay = ((i - first) * 40).toLong()
            child.alpha = 0f
            child.translationX = 30f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(child, "alpha", 0f, 1f).apply { duration = 280 },
                    ObjectAnimator.ofFloat(child, "translationX", 30f, 0f).apply {
                        duration = 280; interpolator = OvershootInterpolator(0.6f)
                    }
                )
                startDelay = delay
                start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Adapter ──
    inner class PickerAdapter(
        private val data: List<Pair<Int, String>>,
        private val currentSelected: Int,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PickerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val bar: View        = view.findViewById(R.id.viewSelectedBar)
            val circle: FrameLayout = view.findViewById(R.id.iconCircle)
            val letter: TextView = view.findViewById(R.id.tvIconLetter)
            val name: TextView   = view.findViewById(R.id.tvOptionName)
            val check: ImageView = view.findViewById(R.id.iconCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_option, parent, false)
            return VH(v)
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val (originalIdx, name) = data[position]
            val isSelected = originalIdx == currentSelected
            val ctx = h.itemView.context
            val primaryColor = ContextCompat.getColor(ctx, R.color.primary_color)

            // Letter avatar
            h.letter.text = name.firstOrNull()?.uppercase() ?: "?"

            // Name
            h.name.text = name
            h.name.setTextColor(
                if (isSelected) primaryColor
                else ContextCompat.getColor(ctx, android.R.color.tab_indicator_text)
            )
            h.name.textSize = if (isSelected) 14.5f else 14f

            // Selected bar
            h.bar.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            // Circle color
            val circleBg = h.circle.background
            if (circleBg is android.graphics.drawable.GradientDrawable) {
                circleBg.setColor(
                    if (isSelected) primaryColor
                    else ContextCompat.getColor(ctx, R.color.primary_color).let {
                        android.graphics.Color.argb(30,
                            android.graphics.Color.red(it),
                            android.graphics.Color.green(it),
                            android.graphics.Color.blue(it))
                    }
                )
            }
            h.letter.setTextColor(
                if (isSelected) android.graphics.Color.WHITE
                else primaryColor
            )

            // Check icon
            h.check.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Click
            h.itemView.setOnClickListener {
                // Scale feedback
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(h.itemView, "scaleX", 1f, 0.97f, 1f).apply { duration = 150 },
                        ObjectAnimator.ofFloat(h.itemView, "scaleY", 1f, 0.97f, 1f).apply { duration = 150 }
                    )
                    start()
                }
                h.itemView.postDelayed({ onClick(originalIdx) }, 120)
            }
        }
    }
}
