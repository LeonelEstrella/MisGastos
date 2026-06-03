package com.catedra.misgastos.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.catedra.misgastos.R
import com.catedra.misgastos.ui.expenses.ExpenseListFragment
import com.google.firebase.auth.FirebaseAuth
import com.catedra.misgastos.MainActivity

class LoginFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 48, 48, 48)

        val email = EditText(requireContext())
        email.hint = "Email"
        email.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val password = EditText(requireContext())
        password.hint = "Contraseña"
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val errorText = TextView(requireContext())
        errorText.setTextColor(0xFFB00020.toInt())
        errorText.textSize = 14f
        errorText.isVisible = false

        val loginButton = Button(requireContext())
        loginButton.text = "Iniciar sesión"
        loginButton.isEnabled = false

        val registerButton = Button(requireContext())
        registerButton.text = "Registrarme"

        layout.addView(email)
        layout.addView(password)
        layout.addView(errorText)
        layout.addView(loginButton)
        layout.addView(registerButton)

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {

            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val emailText = email.text.toString().trim()
                val passwordText = password.text.toString().trim()

                loginButton.isEnabled = emailText.isNotEmpty() && passwordText.isNotEmpty()

                if (errorText.isVisible) {
                    errorText.isVisible = false
                }
            }

            override fun afterTextChanged(text: Editable?) {

            }
        }

        email.addTextChangedListener(textWatcher)
        password.addTextChangedListener(textWatcher)

        loginButton.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (!Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                showError(errorText, "Email o contraseña inválidos")
                return@setOnClickListener
            }

            if (passwordText.length < 6) {
                showError(errorText, "Email o contraseña inválidos")
                return@setOnClickListener
            }

            loginButton.isEnabled = false
            registerButton.isEnabled = false

            auth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnSuccessListener {
                    (requireActivity() as MainActivity).openMain()
                }
                .addOnFailureListener {
                    loginButton.isEnabled = true
                    registerButton.isEnabled = true

                    showError(
                        errorText,
                        "Email o contraseña incorrectos"
                    )
                }
        }

        registerButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegisterFragment())
                .addToBackStack(null)
                .commit()
        }

        return layout
    }

    private fun showError(errorText: TextView, message: String) {
        errorText.text = message
        errorText.isVisible = true
    }
}