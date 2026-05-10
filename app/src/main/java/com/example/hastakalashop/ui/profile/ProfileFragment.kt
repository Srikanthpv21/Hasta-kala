package com.example.hastakalashop.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.hastakalashop.R
import com.example.hastakalashop.ui.auth.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log

class ProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    private var isEditing = false
    private lateinit var viewModel: com.example.hastakalashop.viewmodel.MainViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[com.example.hastakalashop.viewmodel.MainViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        
        val editName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_profile_name)
        val editEmail = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_profile_email)
        val editPlace = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_profile_place)
        val editPhone = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_profile_phone)
        val displayName = view.findViewById<TextView>(R.id.display_name)
        val displayEmail = view.findViewById<TextView>(R.id.display_email)
        val statItems = view.findViewById<TextView>(R.id.stat_total_items)
        val statSales = view.findViewById<TextView>(R.id.stat_total_sales)
        
        val btnEdit = view.findViewById<MaterialButton>(R.id.btn_edit_profile)
        val btnLogout = view.findViewById<MaterialButton>(R.id.btn_logout)

        // Load data
        // Load data from Firebase Auth
        val user = Firebase.auth.currentUser
        val name = user?.displayName ?: prefs.getString("name", "Artisan")
        val email = user?.email ?: prefs.getString("email", "artisan@hastakala.com")
        
        editName.setText(name)
        editEmail.setText(email)
        editPlace.setText(prefs.getString("place", "Not Set"))
        editPhone.setText(prefs.getString("phone", "Not Set"))
        
        displayName.text = name
        displayEmail.text = email

        // Load Stats
        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            statItems.text = items.size.toString()
        }
        viewModel.filteredSales.observe(viewLifecycleOwner) { sales ->
            val total = sales.sumOf { it.totalPrice }
            statSales.text = String.format("₹%.0f", total)
        }

        btnEdit.setOnClickListener {
            if (!isEditing) {
                isEditing = true
                btnEdit.text = "Save"
                btnEdit.setIconResource(android.R.drawable.ic_menu_save)
                
                editName.isEnabled = true
                editEmail.isEnabled = true
                editPlace.isEnabled = true
                editPhone.isEnabled = true
                editName.requestFocus()
            } else {
                val newName = editName.text.toString().trim()
                val newEmail = editEmail.text.toString().trim()
                val newPlace = editPlace.text.toString().trim()
                val newPhone = editPhone.text.toString().trim()

                if (newName.isEmpty() || newEmail.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "Name and Email are required", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                prefs.edit()
                    .putString("name", newName)
                    .putString("email", newEmail)
                    .putString("place", newPlace)
                    .putString("phone", newPhone)
                    .apply()

                displayName.text = newName
                displayEmail.text = newEmail

                // Sync to Cloud
                val syncManager = com.example.hastakalashop.data.FirebaseSyncManager()
                val profileData = mapOf(
                    "name" to newName,
                    "email" to newEmail,
                    "place" to newPlace,
                    "phone" to newPhone
                )
                syncManager.syncProfile(profileData)

                isEditing = false
                btnEdit.text = "Edit"
                btnEdit.setIconResource(android.R.drawable.ic_menu_edit)
                
                editName.isEnabled = false
                editEmail.isEnabled = false
                editPlace.isEnabled = false
                editPhone.isEnabled = false
                
                android.widget.Toast.makeText(requireContext(), "Profile Updated!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            // CRITICAL SECURITY FIX: Purge local cache completely to prevent data bleeding between users
            viewModel.clearAllLocalData()
            prefs.edit().clear().apply() // Reset all shared preferences too

            // Firebase Sign Out
            Firebase.auth.signOut()

            // Clear Credential Manager state
            val credentialManager = CredentialManager.create(requireContext())
            lifecycleScope.launch {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Failed to clear credentials: ${e.message}")
                }
            }

            // is_logged_in should technically be cleared by the edit().clear().apply() but explicitly setting it helps prevent flow bugs
            prefs.edit().putBoolean("is_logged_in", false).putBoolean("onboarding_completed", true).apply() 

            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }
}
