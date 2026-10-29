package com.example.academyhub.teacher.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.academyhub.databinding.ItemSalaryBinding
import com.example.academyhub.models.Salary

class SalaryAdapter(
    private var salaries: List<Salary>,
    private val onItemClick: (Salary) -> Unit,
    private val onMarkPaidClick: (Salary) -> Unit
) : RecyclerView.Adapter<SalaryAdapter.SalaryViewHolder>() {

    inner class SalaryViewHolder(private val binding: ItemSalaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(salary: Salary) {
            // Month & Year
            binding.tvMonth.text = salary.month
            binding.tvYear.text = salary.year.toString()

            // Salary Components
            binding.tvBasicSalary.text = "Basic: ₹${String.format("%,.0f", salary.basicSalary)}"
            binding.tvAllowances.text = "Allowances: ₹${String.format("%,.0f", salary.allowances)}"
            
            if (salary.bonuses > 0) {
                binding.tvBonuses.text = "Bonus: ₹${String.format("%,.0f", salary.bonuses)}"
                binding.tvBonuses.visibility = View.VISIBLE
            } else {
                binding.tvBonuses.visibility = View.GONE
            }

            if (salary.deductions > 0) {
                binding.tvDeductions.text = "Deductions: -₹${String.format("%,.0f", salary.deductions)}"
                binding.tvDeductions.visibility = View.VISIBLE
            } else {
                binding.tvDeductions.visibility = View.GONE
            }

            // Net Salary
            binding.tvNetSalary.text = salary.getFormattedNetSalary()

            // Status
            binding.tvStatus.text = salary.status.uppercase()
            binding.tvStatus.setBackgroundColor(
                when (salary.status) {
                    "paid" -> android.graphics.Color.parseColor("#4CAF50")
                    "pending" -> android.graphics.Color.parseColor("#FF9800")
                    "cancelled" -> android.graphics.Color.parseColor("#F44336")
                    else -> android.graphics.Color.parseColor("#9E9E9E")
                }
            )

            // Payment Details
            if (salary.isPaid()) {
                binding.tvPaymentDate.text = "Paid on: ${salary.paymentDate}"
                binding.tvPaymentDate.visibility = View.VISIBLE
                binding.btnMarkPaid.visibility = View.GONE
            } else {
                binding.tvPaymentDate.visibility = View.GONE
                binding.btnMarkPaid.visibility = View.VISIBLE
            }

            // Remarks
            if (salary.remarks.isNotEmpty()) {
                binding.tvRemarks.text = "Note: ${salary.remarks}"
                binding.tvRemarks.visibility = View.VISIBLE
            } else {
                binding.tvRemarks.visibility = View.GONE
            }

            // Click listeners
            binding.root.setOnClickListener {
                onItemClick(salary)
            }

            binding.btnMarkPaid.setOnClickListener {
                onMarkPaidClick(salary)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalaryViewHolder {
        val binding = ItemSalaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SalaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SalaryViewHolder, position: Int) {
        holder.bind(salaries[position])
    }

    override fun getItemCount(): Int = salaries.size

    fun updateList(newList: List<Salary>) {
        salaries = newList
        notifyDataSetChanged()
    }
}
