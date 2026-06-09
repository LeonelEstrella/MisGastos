package com.catedra.misgastos.ui.expenses

import android.annotation.SuppressLint
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
import kotlinx.coroutines.launch
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.google.android.material.chip.Chip
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.catedra.misgastos.data.repository.SettingsRepository
import com.catedra.misgastos.data.model.ExpenseCategory

class ExpenseListFragment: Fragment() {

    private val repository = ExpenseRepository()

    private var _binding: FragmentExpenseListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseListViewModel by viewModels()

    private lateinit var adapter: ExpenseAdapter

    private var allExpenses: List<Expense> = emptyList()
    private var selectedCategory: String? = null

    private var visibleExpenses: List<Expense> = emptyList()

    private val createPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                exportExpensesToPdf(uri)
            }
        }

    private val settingsRepository = SettingsRepository()

    private var monthlyLimit: Double = 0.0
    private var notificationsEnabled: Boolean = true


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

    @SuppressLint("StringFormatInvalid")
    private fun setupObservers() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            allExpenses = expenses
            setupCategoryChips(expenses)
            applyCategoryFilter()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.textError.isVisible = error != null

            binding.textError.text =
                if (error.isNullOrBlank()) {
                    getString(R.string.load_expenses_error)
                } else {
                    error
                }
        }
    }

    private fun setupListeners() {


        binding.fabAddExpense.setOnClickListener {
            navigateToForm()
        }

        binding.buttonExportPdf.setOnClickListener {
            if (visibleExpenses.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_expenses_to_export),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val categoryText = selectedCategory
                    ?.let { getCategoryLabel(it).lowercase(Locale.getDefault()).replace(" ", "_") }
                    ?: getString(R.string.all).lowercase(Locale.getDefault())

                createPdfLauncher.launch("reporte_gastos_$categoryText.pdf")
            }
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
            .setTitle(getString(R.string.delete_expense_title))
            .setMessage(getString(R.string.delete_expense_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteExpense(expense.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
                    e.message ?: getString(R.string.delete_expense_error),
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
        loadSettings()
        viewModel.loadExpenses()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val settings = settingsRepository.getSettings()

                monthlyLimit = settings.monthlyLimit
                notificationsEnabled = settings.notificationsEnabled

                val currentTotal = visibleExpenses.sumOf { it.amount }
                updateThresholdWarning(currentTotal)
            } catch (e: Exception) {}
        }
    }

    private fun setupCategoryChips(expenses: List<Expense>) {
        binding.chipGroupCategories.removeAllViews()

        val chipAll = Chip(requireContext()).apply {
            text = getString(R.string.all)
            isCheckable = true
            isChecked = selectedCategory == null
            setOnClickListener {
                selectedCategory = null
                applyCategoryFilter()
            }
        }

        binding.chipGroupCategories.addView(chipAll)

        val categories = expenses
            .map { expense -> getCategoryFromValue(expense.category) }
            .distinct()
            .sortedBy { getString(it.labelResId) }

        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = getString(category.labelResId)
                isCheckable = true
                isChecked = selectedCategory == category.code
                setOnClickListener {
                    selectedCategory = category.code
                    applyCategoryFilter()
                }
            }

            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun formatAmount(amount: Double): String {
        return "$ %.2f".format(amount)
    }

    private fun getCategoryFromValue(category: String): ExpenseCategory {
        return if (ExpenseCategory.entries.any { it.code == category }) {
            ExpenseCategory.fromCode(category)
        } else {
            ExpenseCategory.fromLegacyText(category)
        }
    }

    private fun getCategoryLabel(category: String): String {
        val expenseCategory = getCategoryFromValue(category)
        return getString(expenseCategory.labelResId)
    }

    @SuppressLint("StringFormatInvalid")
    private fun applyCategoryFilter() {
        val filteredExpenses = if (selectedCategory == null) {
            allExpenses
        } else {
            allExpenses.filter { expense ->
                getCategoryFromValue(expense.category).code == selectedCategory
            }
        }

        visibleExpenses = filteredExpenses

        adapter.submitList(filteredExpenses)

        val total = filteredExpenses.sumOf { it.amount }

        binding.textMonthlyTotal.text =
            getString(R.string.monthly_expenses, total)


        binding.textExpenseCount.text =
            if (filteredExpenses.size == 1) {
                getString(R.string.expense_count_one)
            } else {
                getString(R.string.expense_count, filteredExpenses.size)
            }

        updateThresholdWarning(total)
    }

    private fun updateThresholdWarning(total : Double) {
        val shouldShowWarning = notificationsEnabled && monthlyLimit > 0 && total > monthlyLimit

        binding.textThresholdWarning.isVisible = shouldShowWarning

        if (shouldShowWarning) {
            binding.textThresholdWarning.text =
                getString(R.string.threshold_warning, formatAmount(monthlyLimit))
        }
    }

    private fun createExpensesPdf(expenses: List<Expense>): ByteArray {
        val pdfDocument = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val lineHeight = 22

        val titlePaint = Paint().apply {
            textSize = 20f
            isFakeBoldText = true
        }

        val subtitlePaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
        }

        val  normalPaint = Paint().apply {
            textSize = 12f
        }

        val boldPaint = Paint().apply {
            textSize = 12f
            isFakeBoldText = true
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = margin
        }

        fun drawLine(text: String, paint: Paint = normalPaint) {
            if (y > pageHeight - margin) {
                newPage()
            }

            canvas.drawText(text, margin.toFloat(), y.toFloat(), paint)
            y += lineHeight
        }

        val total = expenses.sumOf { it.amount }
        val generatedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())

        val filterText =
            selectedCategory?.let { getCategoryLabel(it) } ?: getString(R.string.all)

            drawLine(getString(R.string.expenses_report_title), titlePaint)
            y += 8

            drawLine(getString(R.string.generated_date, generatedDate))
            drawLine(getString(R.string.applied_filter, filterText))
            drawLine(getString(R.string.exported_expense_count, expenses.size))
            drawLine(getString(R.string.exported_total, formatAmount(total)))
            y += 16

            drawLine(getString(R.string.expense_detail_title), subtitlePaint)
            y += 8

            expenses.forEachIndexed { index, expense ->
                drawLine("${index + 1}. ${getCategoryLabel(expense.category)}", boldPaint)
                drawLine(getString(R.string.date_label, formatDate(expense.date)))
                drawLine(getString(R.string.description_label, expense.description))
                drawLine(getString(R.string.amount_label, formatAmount(expense.amount)))
                y += 10
        }

        pdfDocument.finishPage(page)

        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()

        return  outputStream.toByteArray()
    }

    private fun formatDate(dateMillis: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return formatter.format(Date(dateMillis))
    }

    private fun exportExpensesToPdf(uri: Uri) {
        try {
            val pdfBytes = createExpensesPdf(visibleExpenses)

            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pdfBytes)
            }

            Toast.makeText(
                requireContext(),
                getString(R.string.pdf_exported_success),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                e.message ?: getString(R.string.pdf_export_error),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}