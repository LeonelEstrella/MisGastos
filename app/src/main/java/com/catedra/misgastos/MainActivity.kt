package com.catedra.misgastos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.catedra.misgastos.databinding.ActivityMainBinding
import com.catedra.misgastos.ui.auth.LoginFragment
import com.catedra.misgastos.ui.expenses.ExpenseListFragment
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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