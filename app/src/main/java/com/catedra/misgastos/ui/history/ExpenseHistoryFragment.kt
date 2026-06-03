package com.catedra.misgastos.ui.history

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.data.repository.ExpenseRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseHistoryFragment : Fragment() {

    private val repository = ExpenseRepository()

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var textError: TextView

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val scrollView = ScrollView(requireContext()).apply {
            setBackgroundColor(0xFFFDF7FF.toInt())
        }

        container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(requireContext()).apply {
            text = "Histórico de gastos"
            textSize = 28f
            setTextColor(0xFF1F1F1F.toInt())
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(requireContext()).apply {
            text = "Resumen mensual de tus gastos"
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 8, 0, 24)
        }

        progressBar = ProgressBar(requireContext()).apply {
            isVisible = false
        }

        textError = TextView(requireContext()).apply {
            setTextColor(0xFFB00020.toInt())
            isVisible = false
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(progressBar)
        container.addView(textError)

        scrollView.addView(container)

        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadHistory()
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressBar.isVisible = true
                textError.isVisible = false

                val expenses = repository.getExpenses()
                showHistory(expenses)

            } catch (e: Exception) {
                textError.text = e.message ?: "Error al cargar histórico"
                textError.isVisible = true
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    private fun showHistory(expenses: List<Expense>) {
        val groupedExpenses = expenses
            .groupBy { expense -> monthKey(expense.date) }
            .toSortedMap(compareByDescending { it })

        if (groupedExpenses.isEmpty()) {
            val emptyText = TextView(requireContext()).apply {
                text = "Todavía no hay gastos registrados"
                textSize = 16f
                setTextColor(0xFF666666.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 0)
            }

            container.addView(emptyText)
            return
        }

        groupedExpenses.forEach { (month, monthExpenses) ->
            val total = monthExpenses.sumOf { it.amount }

            val item = TextView(requireContext()).apply {
                text = "$month\nTotal: ${formatAmount(total)}\nCantidad de gastos: ${monthExpenses.size}"
                textSize = 18f
                setTextColor(0xFF1F1F1F.toInt())
                setPadding(24, 24, 24, 24)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }

            container.addView(item, params)
        }
    }

    private fun monthKey(dateMillis: Long): String {
        val formatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return formatter.format(Date(dateMillis)).replaceFirstChar { it.uppercase() }
    }

    private fun formatAmount(amount: Double): String {
        return "$%.2f".format(amount)
    }
}