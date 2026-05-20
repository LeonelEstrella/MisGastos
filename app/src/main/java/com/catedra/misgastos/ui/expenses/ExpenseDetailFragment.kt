package com.catedra.misgastos.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catedra.misgastos.R
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.catedra.misgastos.databinding.FragmentExpenseDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseDetailFragment: Fragment() {

    private var _binding: FragmentExpenseDetailBinding? = null
    private val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var expenseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expenseId = arguments?.getString(ARG_EXPENSE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadExpense()

        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonEdit.setOnClickListener {
            expenseId?.let { id ->
                navigateToEdit(id)
            }
        }
    }

    private fun loadExpense() {
        val id = expenseId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true

            val expense = repository.getExpenseById(id)

            binding.progressBar.isVisible = false

            if (expense != null) {
                binding.textCategory.text = expense.category
                binding.textAmount.text = "$${expense.amount}"
                binding.textDescription.text = expense.description
                binding.textDate.text = "Fecha: ${formatDate(expense.date)}"
            }
        }
    }

    private fun navigateToEdit(expenseId: String) {
        val fragment = ExpenseFormFragment.newInstance(expenseId)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun formatDate(dateMillis: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(dateMillis))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EXPENSE_ID = "expenseId"

        fun newInstance(expenseId: String): ExpenseDetailFragment {
            return ExpenseDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EXPENSE_ID, expenseId)
                }
            }
        }
    }
}