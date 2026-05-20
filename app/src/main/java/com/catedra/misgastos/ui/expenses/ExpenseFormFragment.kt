package com.catedra.misgastos.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.catedra.misgastos.databinding.FragmentExpenseFormBinding
import kotlinx.coroutines.launch

class ExpenseFormFragment: Fragment() {

    private var _binding : FragmentExpenseFormBinding? = null
    private  val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var expenseId: String? = null
    private var currentExpense: Expense? = null

    private val isEditMode: Boolean
        get() = expenseId != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        expenseId = arguments?.getString(ARG_EXPENSE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        if (isEditMode) {
            binding.textFormTitle.text = "Editar gasto"
            binding.buttonSave.text = "Actualizar"

            loadExpenseForEdit()
        } else {
            binding.textFormTitle.text = "Nuevo gasto"
            binding.buttonSave.text = "Guardar"
        }
    }

    private fun loadExpenseForEdit() {
        val id = expenseId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true

            val expense = repository.getExpenseById(id)

            binding.progressBar.isVisible = false

            if (expense != null) {
                currentExpense = expense

                binding.editAmount.setText(expense.amount.toString())
                binding.editCategory.setText(expense.category)
                binding.editDescription.setText(expense.description)
            } else {
                showError("No se encontró el gasto")
            }
        }
    }

    private fun setupListeners() {
        binding.buttonSave.setOnClickListener {
            saveExpense()
        }

        binding.buttonCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun saveExpense() {
        val amountText = binding.editAmount.text.toString()
        val category = binding.editCategory.text.toString().trim()
        val description = binding.editDescription.text.toString().trim()

        if (amountText.isBlank() || category.isBlank() || description.isBlank()) {
            showError("Completá todos los campos")
            return
        }

        val amount = amountText.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            showError("Ingresá im monto válido")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true
            binding.textError.isVisible = false

            if (isEditMode) {
                updateExpense(amount, category, description)
            } else {
                createExpense(amount, category, description)
            }

            binding.progressBar.isVisible = false
            parentFragmentManager.popBackStack()
        }
    }

    private suspend fun createExpense(
        amount: Double,
        category: String,
        description: String
    ) {
        val newExpense = Expense(
            amount = amount,
            category = category,
            description = description,
            date = System.currentTimeMillis()
        )

        repository.addExpense(newExpense)
    }

    private suspend fun updateExpense(
        amount: Double,
        category: String,
        description: String
    ) {

        val oldExpense = currentExpense ?: return

        val updatedExpense = oldExpense.copy(
            amount = amount,
            category = category,
            description = description,
        )

        repository.updateExpense(updatedExpense)
    }

    private fun showError(message: String) {
        binding.textError.isVisible = true
        binding.textError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EXPENSE_ID = "expenseId"

        fun newInstance(expenseId: String): ExpenseFormFragment {
            return ExpenseFormFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EXPENSE_ID, expenseId)
                }
            }
        }
    }
}