package com.catedra.misgastos.data.model

import com.catedra.misgastos.R

enum class ExpenseCategory(
    val code: String,
    val labelResId: Int
) {
    CLOTHES(
        code = "CLOTHES",
        labelResId = R.string.clothes
    ),
    FOOD(
        code = "FOOD",
        labelResId = R.string.food
    ),
    TRANSPORT(
        code = "TRANSPORT",
        labelResId = R.string.transport
    ),
    HEALTH(
        code = "HEALTH",
        labelResId = R.string.health
    ),
    ENTERTAINMENT(
        code = "ENTERTAINMENT",
        labelResId = R.string.entertainment
    ),
    OTHER(
        code = "OTHER",
        labelResId = R.string.other
    );

    companion object {
        fun fromCode(code: String?): ExpenseCategory {
            return entries.firstOrNull { it.code == code } ?: OTHER
        }

        fun fromLegacyText(text: String?): ExpenseCategory {
            return when (text) {
                "Ropa", "Clothes" -> CLOTHES
                "Comida", "Food" -> FOOD
                "Transporte", "Transport" -> TRANSPORT
                "Salud", "Health" -> HEALTH
                "Entretenimiento", "Entertainment" -> ENTERTAINMENT
                "Otros", "Other" -> OTHER
                else -> OTHER
            }
        }
    }
}