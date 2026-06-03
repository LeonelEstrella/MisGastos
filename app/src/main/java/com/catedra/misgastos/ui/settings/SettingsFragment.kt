package com.catedra.misgastos.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.catedra.misgastos.data.model.UserSettings
import com.catedra.misgastos.data.repository.SettingsRepository
import com.catedra.misgastos.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar
import com.catedra.misgastos.R

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val repository = SettingsRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSettings()
        setupListeners()
    }

    private fun setupListeners() {
        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true

                val settings = repository.getSettings()

                if (settings.monthlyLimit > 0) {
                    binding.editMonthlyLimit.setText(settings.monthlyLimit.toString())
                }

                binding.checkNotificationsEnabled.isChecked = settings.notificationsEnabled
            } catch (e: Exception) {
                showError(e.message ?: "Error al cargar configuración")
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun saveSettings() {
        val limitText = binding.editMonthlyLimit.text.toString().replace(",", ".")
        val monthlyLimit = limitText.toDoubleOrNull()

        if (monthlyLimit == null || monthlyLimit <= 0) {
            showError("Ingresá un límite mensual válido")
            return
        }

        val settings = UserSettings(
            monthlyLimit = monthlyLimit,
            notificationsEnabled = binding.checkNotificationsEnabled.isChecked
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                binding.textError.isVisible = false
                binding.buttonSaveSettings.isEnabled = false

                repository.saveSettings(settings)

                Snackbar.make(
                    binding.root,
                    "Configuración guardada",
                    Snackbar.LENGTH_SHORT
                )
                    .setAnchorView(requireActivity().findViewById(R.id.bottomNavigation))
                    .show()

            } catch (e: Exception) {
                showError(e.message ?: "Error al guardar la configuración")
            } finally {
                binding.progressBar.isVisible = false
                binding.buttonSaveSettings.isEnabled = true
            }
        }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.isVisible = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}