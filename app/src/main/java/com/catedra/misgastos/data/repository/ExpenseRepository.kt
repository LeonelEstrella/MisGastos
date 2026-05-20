package com.catedra.misgastos.data.repository

import com.catedra.misgastos.data.model.Expense
import kotlinx.coroutines.delay

class ExpenseRepository {

    companion object{
        private val expenses = mutableListOf(
            Expense(
                id = "1",
                amount = 8500.0,
                category = "Comida",
                description = "Almuerzo",
                date = System.currentTimeMillis()
            ),
            Expense(
                id = "2",
                amount = 32000.0,
                category = "Supermercado",
                description = "Compra Semanal",
                date = System.currentTimeMillis()
            ),
            Expense(
                id = "3",
                amount = 1200.0,
                category = "Transporte",
                description = "Subte",
                date = System.currentTimeMillis()
            )
        )
    }

    suspend fun getExpenses(): List<Expense> {
        delay(500)
        return expenses.toList()
    }

    suspend fun getExpenseById(id: String): Expense? {
        delay(300)
        return expenses.find { it.id == id }
    }

    suspend fun addExpense(expense: Expense) {
        delay(300)

        val newExpense = expense.copy(
            id = System.currentTimeMillis().toString(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        expenses.add(newExpense)
    }

    suspend fun updateExpense(expense: Expense) {
        delay(300)
        val index = expenses.indexOfFirst { it.id == expense.id }

        if (index != -1) {
            expenses[index] = expense.copy(
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}