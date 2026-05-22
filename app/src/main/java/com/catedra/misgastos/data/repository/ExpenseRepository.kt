package com.catedra.misgastos.data.repository

import com.catedra.misgastos.data.model.Expense
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ExpenseRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun getExpensesCollection() = db.collection("usuarios")
        .document(FirebaseAuth.getInstance().currentUser!!.uid)
        .collection("gastos")

    suspend fun getExpenses(): List<Expense> {

        val snapshot = getExpensesCollection()
            .get()
            .await()

        return snapshot.documents.mapNotNull {
            it.toObject(Expense::class.java)
        }
    }

    suspend fun getExpenseById(id: String): Expense? {

        val document = getExpensesCollection()
            .document(id)
            .get()
            .await()

        return document.toObject(Expense::class.java)
    }

    suspend fun addExpense(expense: Expense) {

        val document = getExpensesCollection().document()

        val newExpense = expense.copy(
            id = document.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        document.set(newExpense).await()
    }

    suspend fun updateExpense(expense: Expense) {

        val updatedExpense = expense.copy(
            updatedAt = System.currentTimeMillis()
        )

        getExpensesCollection()
            .document(expense.id)
            .set(updatedExpense)
            .await()
    }
}