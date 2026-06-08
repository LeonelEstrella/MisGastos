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
import com.catedra.misgastos.MainActivity
import com.catedra.misgastos.data.repository.SettingsRepository
import java.nio.DoubleBuffer

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
    private fun setupObservers(){
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
            binding.textError.text = error.orEmpty()
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
                    "No hay gastos para exportar",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val categoryText = selectedCategory ?: "todos"
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
            text = "Todos"
            isCheckable = true
            isChecked = selectedCategory == null
            setOnClickListener {
                selectedCategory = null
                applyCategoryFilter()
            }
        }

        binding.chipGroupCategories.addView(chipAll)

        val categories = expenses
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = selectedCategory == category
                setOnClickListener {
                    selectedCategory = category
                    applyCategoryFilter()
                }
            }

            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun formatAmount(amount: Double): String {
        return "$ %.2f".format(amount)
    }

    @SuppressLint("StringFormatInvalid")
    private fun applyCategoryFilter() {
        val filteredExpenses = if (selectedCategory == null) {
            allExpenses
        } else {
            allExpenses.filter { it.category == selectedCategory }
        }

        visibleExpenses = filteredExpenses

        adapter.submitList(filteredExpenses)

        val total = filteredExpenses.sumOf { it.amount }

        binding.textMonthlyTotal.text =
            getString(R.string.monthly_expenses, total)


        binding.textExpenseCount.text =
            if (filteredExpenses.size == 1) {
                getString(R.string.expense_count, filteredExpenses.size)
            } else {
                getString(R.string.expense_count, filteredExpenses.size)
            }

        updateThresholdWarning(total)
    }

    private fun updateThresholdWarning(total : Double) {
        val shoueldShowWarning = notificationsEnabled && monthlyLimit > 0 && total > monthlyLimit

        binding.textThresholdWarning.isVisible = shoueldShowWarning

        if (shoueldShowWarning) {
            binding.textThresholdWarning.text = "Superaste tu umbral mensual de ${formatAmount(monthlyLimit)}"
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

        val filterText = selectedCategory ?: "Todos"

        drawLine("Mis Gastos - Reporte", titlePaint)
        y += 8

        drawLine("Fecha de generación: $generatedDate")
        drawLine("Filtro aplicado: $filterText")
        drawLine("Cantidad de gastos: ${expenses.size}")
        drawLine("Total exportado: ${formatAmount(total)}")
        y += 16

        drawLine("Detalle de gastos", subtitlePaint)
        y += 8

        expenses.forEachIndexed { index, expense ->
            drawLine("${index + 1}. ${expense.category}", boldPaint)
            drawLine("Fecha: ${formatDate(expense.date)}")
            drawLine("Descripción: ${expense.description}")
            drawLine("Monto: ${formatAmount(expense.amount)}")
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
                "PDF exportado correctamente",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                e.message ?: "Error al exportar PDF",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}