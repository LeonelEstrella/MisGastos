package com.catedra.misgastos.ui.expenses

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.catedra.misgastos.R
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.catedra.misgastos.databinding.FragmentExpenseDetailBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.catedra.misgastos.data.model.ExpenseCategory

class ExpenseDetailFragment : Fragment() {

    private var _binding: FragmentExpenseDetailBinding? = null
    private val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var expenseId: String? = null
    private var googleMap: GoogleMap? = null

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
        loadExpense()

        binding.buttonDelete.setOnClickListener {
            confirmDeleteExpense()
        }

        binding.buttonEdit.setOnClickListener {
            expenseId?.let { id ->
                navigateToEdit(id)
            }
        }

        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadExpense() {
        val id = expenseId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true

            val expense = repository.getExpenseById(id)

            binding.progressBar.isVisible = false

            if (expense != null) {
                binding.textCategory.text = getCategoryLabel(expense.category)
                binding.textAmount.text = formatAmount(expense.amount)
                binding.textDescription.text = expense.description
                binding.textDate.text =
                    getString(R.string.date_label, formatDate(expense.date))

                if (!expense.imageUrl.isNullOrBlank()) {
                    binding.imageReceipt.isVisible = true

                    Glide.with(this@ExpenseDetailFragment)
                        .load(expense.imageUrl)
                        .into(binding.imageReceipt)
                } else {
                    binding.imageReceipt.isVisible = false
                }

                if (expense.latitude != null && expense.longitude != null) {
                    binding.mapContainer.isVisible = true
                    showMap(expense.latitude, expense.longitude)
                } else {
                    binding.mapContainer.isVisible = false
                }
            }
        }
    }

    private fun showMap(latitude: Double, longitude: Double) {
        val mapFragment = SupportMapFragment.newInstance()

        childFragmentManager.beginTransaction()
            .replace(R.id.mapContainer, mapFragment)
            .commit()

        mapFragment.getMapAsync { map ->
            val position = LatLng(latitude, longitude)

            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(getString(R.string.expense_location_marker))
            )
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
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

    private fun formatAmount(amount: Double): String {
        return "$ %.2f".format(amount)
    }

    private fun getCategoryLabel(category: String): String {
        val expenseCategory =
            if (ExpenseCategory.entries.any { it.code == category }) {
                ExpenseCategory.fromCode(category)
            } else {
                ExpenseCategory.fromLegacyText(category)
            }

        return getString(expenseCategory.labelResId)
    }

    private fun confirmDeleteExpense() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_expense_title))
            .setMessage(getString(R.string.delete_expense_detail_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteExpense()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteExpense() {
        val id = expenseId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true

                repository.deleteExpense(id)

                binding.progressBar.isVisible = false
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.delete_expense_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
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