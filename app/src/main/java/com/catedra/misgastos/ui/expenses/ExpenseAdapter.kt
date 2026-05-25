package com.catedra.misgastos.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.databinding.ItemExpenseBinding

class ExpenseAdapter(
    private val onItemClick: (Expense) -> Unit,
    private val onDeleteClick: (Expense) -> Unit
): RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var expenses: List<Expense> = emptyList()

    fun submitList(newExpense: List<Expense>) {
        expenses = newExpense
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount(): Int = expenses.size

    private fun formatAmount(amount: Double): String {
        return "$ %.2f".format(amount)
    }

    inner class ExpenseViewHolder(
        private val binding: ItemExpenseBinding
    ): RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: Expense) {
            binding.textCategory.text = expense.category
            binding.textDescription.text = expense.description
            binding.textAmount.text = formatAmount(expense.amount)

            binding.root.setOnClickListener {
                onItemClick(expense)
            }

            binding.buttonDeleteExpense.setOnClickListener {
                onDeleteClick(expense)
            }
        }
    }
}