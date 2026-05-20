package com.catedra.misgastos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.catedra.misgastos.databinding.ActivityMainBinding
import com.catedra.misgastos.ui.expenses.ExpenseListFragment
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ExpenseListFragment())
                .commit()
        }
    }
}