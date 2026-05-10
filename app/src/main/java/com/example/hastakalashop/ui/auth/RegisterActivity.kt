package com.example.hastakalashop.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hastakalashop.R
import com.google.android.material.textfield.TextInputEditText
import java.security.MessageDigest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.example.hastakalashop.MainActivity

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private val TAG = "RegisterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val layoutName     = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutName)
        val layoutEmail    = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPassword)
        val layoutPlace    = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPlace)
        val layoutPhone    = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPhone)

        val editName     = findViewById<TextInputEditText>(R.id.editName)
        val editEmail    = findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword = findViewById<TextInputEditText>(R.id.editPassword)
        val editPlace    = findViewById<TextInputEditText>(R.id.editPlace)
        val editPhone    = findViewById<TextInputEditText>(R.id.editPhone)
        val btnRegister  = findViewById<Button>(R.id.btnRegister)
        val btnGoogle    = findViewById<Button>(R.id.btnGoogle)
        val textLogin    = findViewById<TextView>(R.id.textLogin)

        auth = Firebase.auth
        credentialManager = CredentialManager.create(this)

        btnRegister.setOnClickListener {
            val name     = editName.text.toString().trim()
            val email    = editEmail.text.toString().trim()
            val password = editPassword.text.toString().trim()
            val place    = editPlace.text.toString().trim()
            val phone    = editPhone.text.toString().trim()

            // Reset errors
            layoutName.error     = null
            layoutEmail.error    = null
            layoutPassword.error = null
            layoutPlace.error    = null
            layoutPhone.error    = null

            if (name.isEmpty()) {
                layoutName.error = "Name is required"; return@setOnClickListener
            }
            // Fix #2: Validate email format with Android's built-in pattern
            if (email.isEmpty()) {
                layoutEmail.error = "Email is required"; return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                layoutEmail.error = "Enter a valid email address"; return@setOnClickListener
            }
            if (password.length < 6) {
                layoutPassword.error = "Password must be at least 6 characters"; return@setOnClickListener
            }
            if (place.isEmpty()) {
                layoutPlace.error = "Location is required"; return@setOnClickListener
            }
            if (phone.isEmpty()) {
                layoutPhone.error = "Phone number is required"; return@setOnClickListener
            }
            if (phone.length != 10 || !phone.all { it.isDigit() }) {
                layoutPhone.error = "Please enter a valid 10-digit phone number"; return@setOnClickListener
            }

            val auth = Firebase.auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        prefs.edit()
                            .putString("name", name)
                            .putString("email", email)
                            .putString("place", place)
                            .putString("phone", phone)
                            .apply()

                        // Sync Profile to Cloud
                        val syncManager = com.example.hastakalashop.data.FirebaseSyncManager()
                        syncManager.syncProfile(mapOf(
                            "name" to name,
                            "email" to email,
                            "place" to place,
                            "phone" to phone
                        ))

                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        val msg = when (task.exception) {
                            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "An account already exists with this email address."
                            is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Password is too weak."
                            else -> task.exception?.localizedMessage ?: "Registration failed."
                        }
                        layoutEmail.error = msg
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

        textLogin.setOnClickListener { finish() }
    }

    private fun signInWithGoogle() {
        val rawNonce = java.util.UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.joinToString("") { "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@RegisterActivity, request)
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential error: ${e.localizedMessage}")
                val msg = e.localizedMessage ?: "Unknown error"
                Toast.makeText(this@RegisterActivity, "Google Sign-In failed: $msg (Check Firebase SHA-1)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleSignIn(credential: Credential) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                Log.e(TAG, "ID Token error: ${e.localizedMessage}")
            }
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                    prefs.edit().putBoolean("is_logged_in", true).apply()
                    
                    // Trigger initial sync
                    lifecycleScope.launch {
                        try {
                            val database = com.example.hastakalashop.data.AppDatabase.getDatabase(this@RegisterActivity)
                            val syncManager = com.example.hastakalashop.data.FirebaseSyncManager()
                            val repository = com.example.hastakalashop.data.InventoryRepository(database, syncManager)
                            
                            // Restore Profile from Cloud
                            syncManager.fetchProfile { profile ->
                                profile?.let {
                                    prefs.edit()
                                        .putString("name", it["name"] as? String)
                                        .putString("email", it["email"] as? String)
                                        .putString("place", it["place"] as? String)
                                        .putString("phone", it["phone"] as? String)
                                        .apply()
                                }
                            }
                            
                            repository.triggerInitialSync()
                        } catch (e: Exception) {
                            Log.e(TAG, "Initial sync failed: ${e.localizedMessage}")
                        }
                    }

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
