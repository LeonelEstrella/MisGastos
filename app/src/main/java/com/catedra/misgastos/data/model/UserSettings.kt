package com.catedra.misgastos.data.model

data class UserSettings(
    val monthlyLimit: Double = 0.0,
    val notificationsEnabled: Boolean = true
)