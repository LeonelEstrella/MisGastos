package com.catedra.misgastos.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.catedra.misgastos.R
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.databinding.FragmentExpenseListBinding
import com.google.firebase.auth.FirebaseAuth
import com.catedra.misgastos.ui.auth.LoginFragment
import kotlinx.coroutines.launch
import com.catedra.misgastos.data.repository.ExpenseRepository

class ExpenseListFragment: Fragment() {

    private val repository = ExpenseRepository()

    private var _binding: FragmentExpenseListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseListViewModel by viewModels()

    private lateinit var adapter: ExpenseAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View {
        _binding = FragmentExpenseListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = ExpenseAdapter(
            onItemClick = { expense ->
                navigateToDetail(expense.id)
            },
            onDeleteClick = { expense ->
                confirmDeleteExpense(expense)
            }
        )


        binding.recyclerExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerExpenses.adapter = adapter
    }

    private fun setupObservers(){
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            adapter.submitList(expenses)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textError.isVisible = error != null
            binding.textError.text = error.orEmpty()
        }

        viewModel.monthlyTotal.observe(viewLifecycleOwner) { total ->
            binding.textMonthlyTotal.text = "Total del mes: $${total}"
        }
    }

    private fun setupListeners() {
        binding.buttonAddExpense.setOnClickListener {
            navigateToForm()
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LoginFragment())
                .commit()
        }
    }

    private fun navigateToDetail(expenseId: String) {
        val detailFragment = ExpenseDetailFragment.newInstance(expenseId)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun confirmDeleteExpense(expense: Expense) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar gasto")
            .setMessage("¿Desea eliminar este gasto?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteExpense(expense.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteExpense(expenseId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.deleteExpense(expenseId)
                viewModel.loadExpenses()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.message ?: "Error al eliminar el gasto",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun navigateToForm() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ExpenseFormFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadExpenses()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}