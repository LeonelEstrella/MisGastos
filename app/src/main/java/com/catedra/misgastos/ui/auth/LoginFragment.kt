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
import com.catedra.misgastos.R
import com.catedra.misgastos.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupInitialState()
        setupListeners()
    }

    private fun setupInitialState() {
        binding.buttonLogin.isEnabled = false
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
                updateLoginButtonState()

                if (binding.textError.isVisible) {
                    binding.textError.isVisible = false
                }
            }

            override fun afterTextChanged(text: Editable?) {}
        }

        binding.editEmail.addTextChangedListener(textWatcher)
        binding.editPassword.addTextChangedListener(textWatcher)

        binding.buttonLogin.setOnClickListener {
            login()
        }

        binding.buttonRegister.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateLoginButtonState() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        binding.buttonLogin.isEnabled = email.isNotEmpty() && password.isNotEmpty()
    }

    private fun login() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email o contraseña inválidos")
            return
        }

        if (password.length < 6) {
            showError("Email o contraseña inválidos")
            return
        }

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                (requireActivity() as MainActivity).openMain()
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Email o contraseña incorrectos")
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.buttonLogin.isEnabled = !isLoading
        binding.buttonRegister.isEnabled = !isLoading

        if (!isLoading) {
            updateLoginButtonState()
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