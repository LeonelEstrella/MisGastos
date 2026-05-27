package com.catedra.misgastos.data.repository

import com.catedra.misgastos.data.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.auth.User
import kotlinx.coroutines.tasks.await

class SettingsRepository {

    private val db = FirebaseFirestore.getInstance()

    private fun getSettingsDocument() = db.collection("usuarios")
        .document(FirebaseAuth.getInstance().currentUser!!.uid)
        .collection("settings")
        .document("main")

    suspend fun getSettings(): UserSettings {
        val document = getSettingsDocument()
            .get()
            .await()

        return document.toObject(UserSettings::class.java) ?: UserSettings()
    }

    suspend fun saveSettings(settings: UserSettings) {
        getSettingsDocument()
            .set(settings)
            .await()
    }
}