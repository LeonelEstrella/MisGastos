package com.catedra.misgastos.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.catedra.misgastos.R
import com.catedra.misgastos.databinding.FragmentExpenseListBinding

class ExpenseListFragment: Fragment() {

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
        adapter = ExpenseAdapter { expense ->
            navigateToDetail(expense.id)
        }

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
    }

    private fun navigateToDetail(expenseId: String) {
        val detailFragment = ExpenseDetailFragment.newInstance(expenseId)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, detailFragment)
            .addToBackStack(null)
            .commit()
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