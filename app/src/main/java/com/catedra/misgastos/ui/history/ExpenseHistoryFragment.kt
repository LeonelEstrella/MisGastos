package com.catedra.misgastos.ui.history

import android.app.DatePickerDialog
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.catedra.misgastos.databinding.FragmentExpenseHistoryBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.catedra.misgastos.R
import com.catedra.misgastos.data.model.ExpenseCategory

class ExpenseHistoryFragment : Fragment() {

    private var _binding: FragmentExpenseHistoryBinding? = null
    private val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var allExpenses: List<Expense> = emptyList()
    private var visibleExpenses: List<Expense> = emptyList()

    private var selectedCategory: String? = null
    private var startDateMillis: Long? = null
    private var endDateMillis: Long? = null

    private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val createPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                exportHistoryToPdf(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseHistoryBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupListeners()
        loadHistory()
    }

    private fun setupListeners() {
        binding.buttonStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                startDateMillis = startOfDay(selectedDate)
                binding.buttonStartDate.text =
                    "${getString(R.string.from)} ${displayDateFormat.format(Date(startDateMillis!!))}"
                applyFilters()
            }
        }

        binding.buttonEndDate.setOnClickListener {
            showDatePicker { selectedDate ->
                endDateMillis = endOfDay(selectedDate)
                binding.buttonEndDate.text =
                    "${getString(R.string.to)} ${displayDateFormat.format(Date(endDateMillis!!))}"
                applyFilters()
            }
        }

        binding.buttonClearFilters.setOnClickListener {
            selectedCategory = null
            startDateMillis = null
            endDateMillis = null

            binding.buttonStartDate.text = getString(R.string.from)
            binding.buttonEndDate.text = getString(R.string.to)

            setupCategoryChips(allExpenses)
            applyFilters()
        }

        binding.buttonExportHistoryPdf.setOnClickListener {
            if (visibleExpenses.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.no_expenses_to_export),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                createPdfLauncher.launch(getString(R.string.history_pdf_file_name))
            }
        }
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                binding.textError.isVisible = false

                allExpenses = repository.getExpenses()
                setupCategoryChips(allExpenses)
                applyFilters()

            } catch (e: Exception) {
                binding.textError.text = e.message ?: getString(R.string.history_load_error)
                binding.textError.isVisible = true
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun setupCategoryChips(expenses: List<Expense>) {
        binding.chipGroupHistoryCategories.removeAllViews()

        val chipAll = Chip(requireContext()).apply {
            text = getString(R.string.all_female)
            isCheckable = true
            isChecked = selectedCategory == null

            setOnClickListener {
                selectedCategory = null
                applyFilters()
            }
        }

        binding.chipGroupHistoryCategories.addView(chipAll)

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
                    applyFilters()
                }
            }

            binding.chipGroupHistoryCategories.addView(chip)
        }
    }

    private fun applyFilters() {
        val filtered = allExpenses.filter { expense ->
            val matchesCategory =
                selectedCategory == null ||
                        getCategoryFromValue(expense.category).code == selectedCategory

            val matchesStartDate =
                startDateMillis == null || expense.date >= startDateMillis!!

            val matchesEndDate =
                endDateMillis == null || expense.date <= endDateMillis!!

            matchesCategory && matchesStartDate && matchesEndDate
        }

        visibleExpenses = filtered.sortedByDescending { it.date }

        updateHeader()
        showCategorySummary()
        showMonthlySummary()
    }

    private fun updateHeader() {
        val total = visibleExpenses.sumOf { it.amount }

        binding.textHistoryTotal.text =
            getString(R.string.total_history, formatAmount(total))

        binding.textHistoryCount.text =
            if (visibleExpenses.size == 1) {
                getString(R.string.history_expense_count_one)
            } else {
                getString(R.string.history_expense_count, visibleExpenses.size)
            }
    }

    private fun showCategorySummary() {
        binding.containerCategorySummary.removeAllViews()

        if (visibleExpenses.isEmpty()) {
            addEmptyText(
                binding.containerCategorySummary,
                getString(R.string.no_expenses_for_filters)
            )
            return
        }

        val groupedByCategory = visibleExpenses
            .groupBy { expense -> getCategoryFromValue(expense.category).code }
            .toList()
            .sortedByDescending { (_, expenses) -> expenses.sumOf { it.amount } }

        groupedByCategory.forEach { (categoryCode, expenses) ->
            val total = expenses.sumOf { it.amount }
            val count = expenses.size

            val expenseWord =
                if (count == 1) getString(R.string.expense_singular)
                else getString(R.string.expense_plural)

            val text =
                "${getCategoryLabel(categoryCode)}\n${formatAmount(total)} · $count $expenseWord"

            addSummaryCard(text)
        }
    }

    private fun showMonthlySummary() {
        binding.containerMonthlySummary.removeAllViews()

        if (visibleExpenses.isEmpty()) {
            addEmptyText(
                binding.containerMonthlySummary,
                getString(R.string.no_movements)
            )
            return
        }

        val groupedByMonth = visibleExpenses
            .groupBy { monthKey(it.date) }
            .toList()
            .sortedByDescending { (_, expenses) -> expenses.maxOf { it.date } }

        groupedByMonth.forEach { (month, expenses) ->
            val total = expenses.sumOf { it.amount }
            val count = expenses.size

            val text =
                "$month\n${getString(R.string.total_label, formatAmount(total))}\n${getString(R.string.expense_quantity, count)}"

            addMonthlyCard(text)
        }
    }

    private fun addSummaryCard(text: String) {
        val parts = text.split("\n")

        val titleText = parts.getOrNull(0).orEmpty()
        val detailText = parts.getOrNull(1).orEmpty()

        val card = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(com.catedra.misgastos.R.drawable.bg_history_card)
        }

        val title = TextView(requireContext()).apply {
            this.text = titleText
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1F1F1F.toInt())
        }

        val detail = TextView(requireContext()).apply {
            this.text = detailText
            textSize = 15f
            setTextColor(0xFF0B6B2B.toInt())
            setPadding(0, 6, 0, 0)
        }

        card.addView(title)
        card.addView(detail)

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 12)
        }

        binding.containerCategorySummary.addView(card, params)
    }

    private fun addMonthlyCard(text: String) {
        val parts = text.split("\n")

        val monthText = parts.getOrNull(0).orEmpty()
        val totalText = parts.getOrNull(1).orEmpty()
        val countText = parts.getOrNull(2).orEmpty()

        val card = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundResource(com.catedra.misgastos.R.drawable.bg_history_card)
        }

        val month = TextView(requireContext()).apply {
            this.text = monthText
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF1F1F1F.toInt())
        }

        val total = TextView(requireContext()).apply {
            this.text = totalText
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF0B6B2B.toInt())
            setPadding(0, 8, 0, 0)
        }

        val count = TextView(requireContext()).apply {
            this.text = countText
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 4, 0, 0)
        }

        card.addView(month)
        card.addView(total)
        card.addView(count)

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 12)
        }

        binding.containerMonthlySummary.addView(card, params)
    }

    private fun addEmptyText(
        container: android.widget.LinearLayout,
        message: String
    ) {
        val emptyText = TextView(requireContext()).apply {
            text = message
            textSize = 15f
            setTextColor(0xFF666666.toInt())
            setPadding(24, 20, 24, 20)
            setBackgroundResource(com.catedra.misgastos.R.drawable.bg_history_card)
        }

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 12)
        }

        container.addView(emptyText, params)
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()

        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }

                onDateSelected(selectedCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        dialog.show()
    }

    private fun startOfDay(dateMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun endOfDay(dateMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun monthKey(dateMillis: Long): String {
        return monthFormat
            .format(Date(dateMillis))
            .replaceFirstChar { it.uppercase() }
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

    private fun formatDate(dateMillis: Long): String {
        return displayDateFormat.format(Date(dateMillis))
    }

    private fun exportHistoryToPdf(uri: Uri) {
        try {
            val pdfBytes = createHistoryPdf()

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

    private fun createHistoryPdf(): ByteArray {
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

        val normalPaint = Paint().apply {
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

        val total = visibleExpenses.sumOf { it.amount }
        val generatedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date())

        val categoryText =
            selectedCategory?.let { getCategoryLabel(it) } ?: getString(R.string.all_female)

        val startText =
            startDateMillis?.let { formatDate(it) } ?: getString(R.string.no_filter)

        val endText =
            endDateMillis?.let { formatDate(it) } ?: getString(R.string.no_filter)

        drawLine(getString(R.string.history_report_title), titlePaint)
        y += 8

        drawLine(getString(R.string.generated_date, generatedDate))
        drawLine(getString(R.string.category_label, categoryText))
        drawLine(getString(R.string.from_label, startText))
        drawLine(getString(R.string.to_label, endText))
        drawLine(getString(R.string.exported_expense_count, visibleExpenses.size))
        drawLine(getString(R.string.exported_total, formatAmount(total)))
        y += 16

        drawLine(getString(R.string.expense_detail_title), subtitlePaint)
        y += 8

        visibleExpenses.forEachIndexed { index, expense ->
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

        return outputStream.toByteArray()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}