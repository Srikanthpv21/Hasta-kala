package com.example.hastakalashop

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.hastakalashop.data.AppDatabase
import com.example.hastakalashop.data.InventoryRepository
import com.example.hastakalashop.data.Item
import com.example.hastakalashop.databinding.ActivityMainBinding
import com.example.hastakalashop.ui.dashboard.DashboardFragment
import com.example.hastakalashop.ui.income.IncomeLogFragment
import com.example.hastakalashop.ui.quickbill.QuickBillFragment
import com.example.hastakalashop.ui.stock.StockAlertFragment
import com.example.hastakalashop.viewmodel.InventoryViewModelFactory
import com.example.hastakalashop.viewmodel.MainViewModel
import androidx.fragment.app.Fragment
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hastakalashop.utils.NotificationHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_logged_in", false)) {
            startActivity(android.content.Intent(this, com.example.hastakalashop.ui.auth.LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Link global app context for background sync error toasts
        com.example.hastakalashop.data.FirebaseSyncManager.appContext = applicationContext

        // Initialize Repository and ViewModel
        val database = AppDatabase.getDatabase(this)
        val syncManager = com.example.hastakalashop.data.FirebaseSyncManager()
        val notificationHelper = NotificationHelper(this)
        val repository = InventoryRepository(database, syncManager, notificationHelper)
        val factory = InventoryViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Establish Cloud-First Source of Truth on App Start:
        // Instantly synchronize multi-device deltas down to the local cache.
        lifecycleScope.launch {
            try {
                repository.synchronizeFromCloud()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Sync error: ${e.localizedMessage}")
            }
        }

        requestNotificationPermission()

        // Fix #9: Seed data now runs in a single coroutine to prevent race conditions
        seedInitialData()

        binding.navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_quick_bill -> { loadFragment(QuickBillFragment()); true }
                R.id.navigation_dashboard -> { loadFragment(DashboardFragment()); true }
                R.id.navigation_stock_alert -> { loadFragment(StockAlertFragment()); true }
                R.id.navigation_income_log -> { loadFragment(IncomeLogFragment()); true }
                R.id.navigation_inventory -> { loadFragment(com.example.hastakalashop.ui.inventory.InventoryFragment()); true }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.navView.selectedItemId = R.id.navigation_quick_bill
        }
    }

    private fun loadFragment(fragment: Fragment) {
        val tag = fragment::class.java.simpleName
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Hide all existing fragments
        supportFragmentManager.fragments.forEach {
            transaction.hide(it)
        }
        
        if (existingFragment != null) {
            transaction.show(existingFragment)
        } else {
            transaction.add(R.id.nav_host_fragment, fragment, tag)
        }
        
        transaction.commit()
    }

    /**
     * Fix #9: All DB operations run inside a single coroutine so deleteAllItems()
     * is guaranteed to complete before any insertItem() calls start.
     * Fix #10: Items now have a proper 'category' field instead of relying on name matching.
     * Seed key bumped to v6 so existing items are re-seeded with the category field.
     */
    private fun seedInitialData() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_seeded_v6", false)) {
            viewModel.viewModelScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val existing = db.itemDao().getAllItemsList()
                
                // CRITICAL SAFEGUARD: If user just completed cloud restore, the DB is already populated.
                // Abort seeding entirely to prevent destroying newly loaded data.
                if (existing.isNotEmpty()) {
                    prefs.edit().putBoolean("is_seeded_v6", true).apply()
                    return@launch
                }

                val repo = InventoryRepository(db)
                val resPrefix = "android.resource://$packageName/drawable/"

                repo.deleteAllItems()
                repo.insertItem(Item(name = "Banana Fiber Bag", color = "Red",   price = 150.0, initialStock = 20, currentStock = 20, imageUri = "${resPrefix}banana_bag_red",    category = "Bags"))
                repo.insertItem(Item(name = "Banana Fiber Bag", color = "Blue",  price = 150.0, initialStock = 20, currentStock = 20, imageUri = "${resPrefix}banana_bag_blue",   category = "Bags"))
                repo.insertItem(Item(name = "Keychain",         color = "Wooden",price = 50.0,  initialStock = 50, currentStock = 50, imageUri = "${resPrefix}keychain_wooden",   category = "Keychains"))
                repo.insertItem(Item(name = "Keychain",         color = "Painted",price = 60.0, initialStock = 30, currentStock = 30, imageUri = "${resPrefix}keychain_painted",  category = "Keychains"))
                repo.insertItem(Item(name = "Table Mat",        color = "Natural",price = 200.0,initialStock = 10, currentStock = 2,  imageUri = "${resPrefix}table_mat_natural", category = "Mats"))

                prefs.edit().putBoolean("is_seeded_v6", true).apply()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_profile -> {
                loadFragment(com.example.hastakalashop.ui.profile.ProfileFragment())
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
