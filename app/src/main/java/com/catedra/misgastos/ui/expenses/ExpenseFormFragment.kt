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
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import com.bumptech.glide.Glide

class ExpenseFormFragment: Fragment() {

    private var _binding : FragmentExpenseFormBinding? = null
    private  val binding get() = _binding!!

    private val repository = ExpenseRepository()

    private var expenseId: String? = null
    private var currentExpense: Expense? = null

    private val isEditMode: Boolean
        get() = expenseId != null

    private var selectedImageUri: Uri? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                binding.imageReceiptPreview.setImageURI(uri)
                binding.imageReceiptPreview.isVisible = true
            }
        }

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

                if (!expense.imageUrl.isNullOrBlank()) {
                    binding.imageReceiptPreview.isVisible = true

                    Glide.with(this@ExpenseFormFragment)
                        .load(expense.imageUrl)
                        .into(binding.imageReceiptPreview)
                }
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

        binding.buttonSelectReceipt.setOnClickListener {
            pickImageLauncher.launch("image/*")
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

        val normalizedAmountText = amountText.replace(",", ".")
        val amount = normalizedAmountText.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            showError("Ingresá un monto válido")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.isVisible = true
            binding.textError.isVisible = false
            binding.buttonSave.isEnabled = false

            try {
                if (isEditMode) {
                    updateExpense(amount, category, description)
                } else {
                    createExpense(amount, category, description)
                }

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
            imageUrl = imageUrl
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
            imageUrl = finalImageUrl
        )

        repository.updateExpense(updatedExpense)
    }

    private  suspend fun  uploadReceiptImage (uri: Uri): String {
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