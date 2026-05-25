package com.catedra.misgastos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.catedra.misgastos.databinding.ActivityMainBinding
import com.catedra.misgastos.ui.auth.LoginFragment
import com.catedra.misgastos.ui.expenses.ExpenseListFragment
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = androidx.core.view.WindowInsetsControllerCompat(
            window,
            window.decorView
        )

        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        if (savedInstanceState == null) {
            val fragment = if (auth.currentUser == null) {
                LoginFragment()
            } else {
                ExpenseListFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        }
    }
}