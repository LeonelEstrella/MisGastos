package com.catedra.misgastos.ui.expenses

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.data.repository.ExpenseRepository
import com.catedra.misgastos.databinding.FragmentExpenseFormBinding
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.catedra.misgastos.data.repository.SettingsRepository
import com.catedra.misgastos.utils.NotificationHelper
import android.widget.ArrayAdapter
import com.catedra.misgastos.R
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.catedra.misgastos.utils.ReceiptTextAnalyzer
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.google.mlkit.vision.text.Text
import com.catedra.misgastos.data.model.ExpenseCategory

class ExpenseFormFragment : Fragment() {

    private var _binding: FragmentExpenseFormBinding? = null
    private val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var expenseId: String? = null
    private var currentExpense: Expense? = null

    private val isEditMode: Boolean
        get() = expenseId != null

    private var selectedImageUri: Uri? = null
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null



    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun getCategories(): List<ExpenseCategory> {
        return ExpenseCategory.entries
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                getCurrentLocation()
            } else {
                showError(getString(R.string.location_permission_error))
                setLocationLoading(false)
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                val mimeType = requireContext().contentResolver.getType(uri)

                if (mimeType == "application/pdf") {
                    binding.imageReceiptPreview.setImageResource(R.drawable.ic_pdf)
                } else {
                    binding.imageReceiptPreview.setImageURI(uri)
                }
                binding.containerReceiptPreview.isVisible = true
            }
        }

    private val settingsRepository = SettingsRepository()

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
        setupCategorySpinner()
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        if (isEditMode) {
            binding.textFormTitle.text = getString(R.string.edit_expense)
            binding.buttonSave.text = getString(R.string.update)
            loadExpenseForEdit()
        } else {
            binding.textFormTitle.text = getString(R.string.new_expense)
            binding.buttonSave.text = getString(R.string.save)
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

                val categories = getCategories()

                binding.editAmount.setText(expense.amount.toString())

                val expenseCategory =
                    if (expense.category in categories.map { it.code }) {
                        ExpenseCategory.fromCode(expense.category)
                    } else {
                        ExpenseCategory.fromLegacyText(expense.category)
                    }

                val index = categories.indexOf(expenseCategory)

                if (index >= 0) {
                    binding.spinnerCategory.setSelection(index)
                }

                binding.editDescription.setText(expense.description)

                selectedLatitude = expense.latitude
                selectedLongitude = expense.longitude
                updateLocationText()

                if (!expense.imageUrl.isNullOrBlank()) {
                    binding.containerReceiptPreview.isVisible = true

                    Glide.with(this@ExpenseFormFragment)
                        .load(expense.imageUrl)
                        .into(binding.imageReceiptPreview)
                }
            } else {
                showError(getString(R.string.expense_not_found))
            }
        }
    }

    private fun setupCategorySpinner() {
        val categoryLabels = getCategories().map { category ->
            getString(category.labelResId)
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categoryLabels
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerCategory.adapter = adapter
    }

    private fun setupListeners() {
        binding.buttonSave.setOnClickListener {
            saveExpense()
        }

        binding.buttonCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.buttonSelectReceipt.setOnClickListener {
            pickImageLauncher.launch("*/*")
        }

        binding.buttonSuggestFromReceipt.setOnClickListener {
            suggestDataFromReceipt()
        }

        binding.buttonRemoveReceipt.setOnClickListener {
            removeSelectedReceipt()
        }

        binding.buttonUseCurrentLocation.setOnClickListener {
            requestCurrentLocation()
        }
    }

    private fun requestCurrentLocation() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }

    private fun getCurrentLocation() {

        setLocationLoading(true)

        val locationRequest =
            com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                1000
            ).build()

        val locationCallback =
            object : com.google.android.gms.location.LocationCallback() {

                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {

                    val location = result.lastLocation

                    if (location != null) {

                        selectedLatitude = location.latitude
                        selectedLongitude = location.longitude

                        updateLocationText()

                        fusedLocationClient.removeLocationUpdates(this)
                        setLocationLoading(false)

                    } else {
                        showError(getString(R.string.location_error))
                        setLocationLoading(false)
                    }
                }
            }

        try {

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                requireActivity().mainLooper
            )

        } catch (e: SecurityException) {

            showError(getString(R.string.location_security_error))
            setLocationLoading(false)

        }
    }

    private fun updateLocationText() {
        binding.textSelectedLocation.text =
            if (selectedLatitude != null && selectedLongitude != null) {
                getString(
                    R.string.selected_location,
                    selectedLatitude.toString(),
                    selectedLongitude.toString()
                )
            } else {
                getString(R.string.no_location)
            }
    }

    private fun removeSelectedReceipt() {
        selectedImageUri = null
        binding.imageReceiptPreview.setImageDrawable(null)
        binding.containerReceiptPreview.isVisible = false
    }

    private fun saveExpense() {
        val amountText = binding.editAmount.text.toString()
        val selectedCategory = getCategories()[binding.spinnerCategory.selectedItemPosition]
        val category = selectedCategory.code
        val description = binding.editDescription.text.toString().trim()
        if (description.isBlank()) {
            showError(getString(R.string.description_required))
            return
        }
        val amount = amountText.replace(",", ".").toDoubleOrNull()

        if (amount == null || amount <= 0) {
            showError(getString(R.string.amount_error))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true
            binding.textError.isVisible = false
            binding.buttonSave.isEnabled = false

            try {
                val previousMonthlyTotal = repository.getMonthlyTotal()

                if (isEditMode) {
                    updateExpense(amount, category, description)
                } else {
                    createExpense(amount, category, description)
                }

                checkMonthlyLimitAndNotify(previousMonthlyTotal)

                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.save_expense_error))
            } finally {
                binding.progressBar.isVisible = false
                binding.buttonSave.isEnabled = true
            }
        }
    }

    private suspend fun createExpense(
        amount: Double,
        category: String,
        description: String
    ) {
        val imageUrl = selectedImageUri?.let { uri ->
            uploadReceiptImage(uri)
        }

        val newExpense = Expense(
            amount = amount,
            category = category,
            description = description,
            date = System.currentTimeMillis(),
            imageUrl = imageUrl,
            latitude = selectedLatitude,
            longitude = selectedLongitude
        )

        repository.addExpense(newExpense)
    }

    private suspend fun updateExpense(
        amount: Double,
        category: String,
        description: String
    ) {
        val oldExpense = currentExpense ?: return

        val finalImageUrl = selectedImageUri?.let { uri ->
            uploadReceiptImage(uri)
        } ?: oldExpense.imageUrl

        val updatedExpense = oldExpense.copy(
            amount = amount,
            category = category,
            description = description,
            imageUrl = finalImageUrl,
            latitude = selectedLatitude,
            longitude = selectedLongitude
        )

        repository.updateExpense(updatedExpense)
    }

    private suspend fun uploadReceiptImage(uri: Uri): String {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw Exception(getString(R.string.not_authenticated))

        val fileName = "${System.currentTimeMillis()}.jpg"

        val storeRef = FirebaseStorage.getInstance()
            .reference
            .child("users")
            .child(userId)
            .child("receipts")
            .child(fileName)

        storeRef.putFile(uri).await()

        return storeRef.downloadUrl.await().toString()
    }

    private fun showError(message: String) {
        binding.textError.isVisible = true
        binding.textError.text = message
    }


    private fun setOcrLoading(isLoading: Boolean) {
        binding.progressOcr.isVisible = isLoading
        binding.buttonSuggestFromReceipt.isEnabled = !isLoading
        binding.buttonSelectReceipt.isEnabled = !isLoading
        binding.buttonSave.isEnabled = !isLoading

        binding.buttonSuggestFromReceipt.text =
            if (isLoading) {
                getString(R.string.reading_receipt)
            } else {
                getString(R.string.suggest_from_receipt)
            }
    }


    private fun suggestDataFromReceipt() {
        val uri = selectedImageUri

        if (uri == null) {
            showError(getString(R.string.add_receipt_first))
            return
        }

        try {
            setOcrLoading(true)
            binding.textError.isVisible = false

            val image = createInputImageFromUri(uri)

            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text

                    if (recognizedText.isBlank()) {
                        showError(getString(R.string.no_text_detected))
                        return@addOnSuccessListener
                    }

                    val suggestion = ReceiptTextAnalyzer.analyze(recognizedText)

                    applySuggestionToForm(suggestion)

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.receipt_suggestions_loaded),
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { exception ->
                    showError(exception.message ?: getString(R.string.receipt_read_error))
                }
                .addOnCompleteListener {
                    setOcrLoading(false)
                }

        } catch (e: Exception) {
            setOcrLoading(false)
            showError(e.message ?: getString(R.string.receipt_process_error))
        }
    }


    private fun applySuggestionToForm(
        suggestion: com.catedra.misgastos.utils.ExpenseSuggestion
    ) {
        suggestion.amount?.let { amount ->
            binding.editAmount.setText("%.2f".format(amount))
        }

        suggestion.category?.let { categoryText ->
            val categories = getCategories()
            val suggestedCategory = ExpenseCategory.fromLegacyText(categoryText)

            val index = categories.indexOf(suggestedCategory)

            if (index >= 0) {
                binding.spinnerCategory.setSelection(index)
            }
        }

        suggestion.description?.let { description ->
            if (binding.editDescription.text.isNullOrBlank()) {
                binding.editDescription.setText(description)
            }
        }
    }

    private fun createInputImageFromUri(uri: Uri): InputImage {
        val mimeType = requireContext().contentResolver.getType(uri)

        return if (mimeType == "application/pdf") {
            val bitmap = renderPdfFirstPage(uri)
            InputImage.fromBitmap(bitmap, 0)
        } else {
            InputImage.fromFilePath(requireContext(), uri)
        }
    }

    private fun renderPdfFirstPage(uri: Uri): Bitmap {
        val fileDescriptor =
            requireContext().contentResolver.openFileDescriptor(uri, "r")
                ?: throw Exception("No se pudo abrir el PDF")

        val renderer = PdfRenderer(fileDescriptor)
        val page = renderer.openPage(0)

        val bitmap = Bitmap.createBitmap(
            page.width * 2,
            page.height * 2,
            Bitmap.Config.ARGB_8888
        )

        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        page.render(
            bitmap,
            null,
            null,
            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
        )

        page.close()
        renderer.close()
        fileDescriptor.close()

        return bitmap
    }


    private suspend fun checkMonthlyLimitAndNotify(previousMonthlyTotal : Double) {
        val settings = settingsRepository.getSettings()

        if (!settings.notificationsEnabled || settings.monthlyLimit <= 0) {
            return
        }

        val newMonthlyTotal = repository.getMonthlyTotal()

        val wasBelowOrEqualLimit = previousMonthlyTotal <= settings.monthlyLimit
        val isNowOverLimit = newMonthlyTotal > settings.monthlyLimit

        if (wasBelowOrEqualLimit && isNowOverLimit) {
            NotificationHelper.showLimitExceededNotification(
                context = requireContext(),
                monthlyTotal = newMonthlyTotal,
                monthlyLimit = settings.monthlyLimit
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setLocationLoading(isLoading: Boolean) {
        binding.progressLocation.isVisible = isLoading
        binding.buttonUseCurrentLocation.isEnabled = !isLoading

        binding.buttonUseCurrentLocation.text =
            if (isLoading) {
                getString(R.string.getting_location)
            } else {
                getString(R.string.use_location)
            }
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