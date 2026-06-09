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

    private fun getCategories(): List<String> {
        return listOf(
            getString(R.string.clothes),
            getString(R.string.food),
            getString(R.string.transport),
            getString(R.string.health),
            getString(R.string.entertainment),
            getString(R.string.other)
        )
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                getCurrentLocation()
            } else {
                showError("Necesitás permitir ubicación para guardar la ubicación actual")
                setLocationLoading(false)
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                binding.imageReceiptPreview.setImageURI(uri)
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
            binding.buttonSave.text = "Actualizar"
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

                val categories= getCategories()

                binding.editAmount.setText(expense.amount.toString())
                val index = categories.indexOf(expense.category)
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
                showError("No se encontró el gasto")
            }
        }
    }

    private fun setupCategorySpinner() {
        val categories= getCategories()

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categories
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
            pickImageLauncher.launch("image/*")
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
                        showError("No se pudo obtener ubicación")
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

            showError("No hay permiso de ubicación")
            setLocationLoading(false)

        }
    }

    private fun updateLocationText() {
        if (selectedLatitude != null && selectedLongitude != null) {
            binding.textSelectedLocation.text =
                "Ubicación seleccionada: $selectedLatitude, $selectedLongitude"
        } else {
            binding.textSelectedLocation.text = "Sin ubicación"
        }
    }

    private fun removeSelectedReceipt() {
        selectedImageUri = null
        binding.imageReceiptPreview.setImageDrawable(null)
        binding.containerReceiptPreview.isVisible = false
    }

    private fun saveExpense() {
        val amountText = binding.editAmount.text.toString()
        val category = binding.spinnerCategory.selectedItem.toString()
        val description = binding.editDescription.text.toString().trim()
        if (description.isBlank()) {
            showError("Completá la descripción")
            return
        }
        val amount = amountText.replace(",", ".").toDoubleOrNull()

        if (amount == null || amount <= 0) {
            showError("Ingresá un monto válido")
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
                showError(e.message ?: "Error al guardar el gasto")
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
            ?: throw Exception("Usuario no autenticado")

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
                "Leyendo comprobante..."
            } else {
                "Sugerir datos desde comprobante"
            }
    }


    private fun suggestDataFromReceipt() {
        val uri = selectedImageUri

        if (uri == null) {
            showError("Primero agregá una imagen del comprobante")
            return
        }

        try {
            setOcrLoading(true)
            binding.textError.isVisible = false

            val image = InputImage.fromFilePath(requireContext(), uri)

            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text

                    if (recognizedText.isBlank()) {
                        showError("No se detectó texto en el comprobante")
                        return@addOnSuccessListener
                    }

                    val suggestion = ReceiptTextAnalyzer.analyze(recognizedText)

                    applySuggestionToForm(suggestion)

                    Toast.makeText(
                        requireContext(),
                        "Sugerencias cargadas. Revisalas antes de guardar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { exception ->
                    showError(exception.message ?: "No se pudo leer el comprobante")
                }
                .addOnCompleteListener {
                    setOcrLoading(false)
                }

        } catch (e: Exception) {
            setOcrLoading(false)
            showError(e.message ?: "Error al procesar la imagen")
        }
    }


    private fun applySuggestionToForm(
        suggestion: com.catedra.misgastos.utils.ExpenseSuggestion
    ) {
        suggestion.amount?.let { amount ->
            binding.editAmount.setText("%.2f".format(amount))
        }

        suggestion.category?.let { category ->
            val categories= getCategories()
            val index = categories.indexOf(category)
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
                "Obteniendo ubicación..."
            }
            else {
                "Usar ubicación actual"
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