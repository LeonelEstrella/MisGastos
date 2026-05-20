package com.catedra.misgastos.ui.expenses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catedra.misgastos.data.model.Expense
import com.catedra.misgastos.data.repository.ExpenseRepository
import kotlinx.coroutines.launch

class ExpenseListViewModel (
    private val repository: ExpenseRepository = ExpenseRepository()
    ): ViewModel() {

    private val _expenses = MutableLiveData<List<Expense>>(emptyList())
    val expenses: LiveData<List<Expense>> = _expenses

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _monthlyTotal  = MutableLiveData<Double>(0.0)
    val monthlyTotal: LiveData<Double> = _monthlyTotal

    fun loadExpenses() {
        viewModelScope.launch {
            try {
                _loading.value = true
                _error.value = null

                val result = repository.getExpenses()

                _expenses.value = result
                _monthlyTotal.value = result.sumOf { it.amount }

            } catch (e: Exception) {
                _error.value = e.message ?: "Error al cargar gatos"
            } finally {
                _loading.value = false
            }
        }
    }
}