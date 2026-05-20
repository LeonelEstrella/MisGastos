package com.catedra.misgastos.data.model

data class Expense (
    val id: String = "",
    val amount: Double = 0.0,
    val category: String = "",
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val latitude : Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)