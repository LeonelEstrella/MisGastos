package com.catedra.misgastos

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.catedra.misgastos.databinding.ActivityMainBinding
import com.catedra.misgastos.ui.auth.LoginFragment
import com.catedra.misgastos.ui.expenses.ExpenseListFragment
import com.catedra.misgastos.ui.history.ExpenseHistoryFragment
import com.catedra.misgastos.ui.settings.SettingsFragment
import com.catedra.misgastos.utils.LocaleManager
import com.catedra.misgastos.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth = FirebaseAuth.getInstance()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // La app sigue funcionando aunque el usuario rechace el permiso.
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()

        NotificationHelper.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        setupBottomNavigation()

        if (savedInstanceState == null) {
            if (auth.currentUser == null) {
                openLogin()
            } else {
                openMain()
            }
        }
    }

    private fun setupSystemBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val insetsController = WindowInsetsControllerCompat(
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
                0
            )

            insets
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_expenses -> {
                    openRootFragment(ExpenseListFragment())
                    true
                }

                R.id.nav_history -> {
                    openRootFragment(ExpenseHistoryFragment())
                    true
                }

                R.id.nav_settings -> {
                    openRootFragment(SettingsFragment())
                    true
                }

                else -> false
            }
        }
    }

    fun openMain() {
        binding.bottomNavigation.isVisible = true

        if (binding.bottomNavigation.selectedItemId != R.id.nav_expenses) {
            binding.bottomNavigation.selectedItemId = R.id.nav_expenses
        } else {
            openRootFragment(ExpenseListFragment())
        }
    }

    fun openLogin() {
        binding.bottomNavigation.isVisible = false

        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, LoginFragment())
            .commit()
    }

    private fun openRootFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    override fun attachBaseContext(newBase: Context) {
        val language = LocaleManager.getLanguage(newBase)
        val locale = Locale(language)

        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)

        val context = newBase.createConfigurationContext(config)

        super.attachBaseContext(context)
    }
}