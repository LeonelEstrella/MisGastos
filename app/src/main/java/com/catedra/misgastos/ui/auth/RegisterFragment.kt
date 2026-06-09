package com.catedra.misgastos.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.catedra.misgastos.MainActivity
import com.catedra.misgastos.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.catedra.misgastos.R

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        binding.buttonRegister.isEnabled = false
    }

    private fun setupListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                updateRegisterButtonState()

                if (binding.textError.isVisible) {
                    binding.textError.isVisible = false
                }
            }

            override fun afterTextChanged(text: Editable?) {}
        }

        binding.editEmail.addTextChangedListener(textWatcher)
        binding.editPassword.addTextChangedListener(textWatcher)

        binding.buttonRegister.setOnClickListener {
            register()
        }

        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun updateRegisterButtonState() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        binding.buttonRegister.isEnabled = email.isNotEmpty() && password.isNotEmpty()
    }

    private fun register() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(getString(R.string.valid_email_error))
            return
        }

        if (password.length < 6) {
            showError(getString(R.string.password_length_error))
            return
        }

        setLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                (requireActivity() as MainActivity).openMain()
            }
            .addOnFailureListener { exception ->
                setLoading(false)
                showError(getRegisterErrorMessage(exception))
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.buttonRegister.isEnabled = !isLoading
        binding.buttonBack.isEnabled = !isLoading

        if (!isLoading) {
            updateRegisterButtonState()
        }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.isVisible = true
    }

    private fun getRegisterErrorMessage(exception: Exception): String {
        val message = exception.message.orEmpty()

        return when {
            message.contains("email address is already in use", ignoreCase = true) ->
                getString(R.string.email_already_registered)

            message.contains("badly formatted", ignoreCase = true) ->
                getString(R.string.valid_email_error)

            message.contains("password", ignoreCase = true) ->
                getString(R.string.password_requirements_error)

            else ->
                getString(R.string.register_error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}