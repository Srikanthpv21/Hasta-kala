package com.example.hastakalashop.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.hastakalashop.MainActivity
import com.example.hastakalashop.R
import com.google.android.material.textfield.TextInputEditText
import java.security.MessageDigest
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        com.example.hastakalashop.data.FirebaseSyncManager.appContext = applicationContext

        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)

        if (!prefs.getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        if (prefs.getBoolean("is_logged_in", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val layoutEmail    = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPassword)
        val editEmail      = findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword   = findViewById<TextInputEditText>(R.id.editPassword)
        val btnLogin       = findViewById<Button>(R.id.btnLogin)
        val btnGoogle      = findViewById<Button>(R.id.btnGoogle)
        val textRegister   = findViewById<TextView>(R.id.textRegister)

        auth = Firebase.auth
        credentialManager = CredentialManager.create(this)

        btnLogin.setOnClickListener {
            val email    = editEmail.text.toString().trim()
            val password = editPassword.text.toString().trim()

            layoutEmail.error    = null
            layoutPassword.error = null

            if (email.isEmpty()) {
                layoutEmail.error = "Email is required"; return@setOnClickListener
            }
            if (password.isEmpty()) {
                layoutPassword.error = "Password is required"; return@setOnClickListener
            }

            val auth = Firebase.auth
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        prefs.edit().putBoolean("is_logged_in", true).apply()

                        // Trigger initial sync to upload any local data to the new cloud account
                        lifecycleScope.launch {
                            try {
                                val database = com.example.hastakalashop.data.AppDatabase.getDatabase(this@LoginActivity)
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
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } catch (e: Exception) {
                                Log.e(TAG, "Initial sync failed: ${e.localizedMessage}")
                                // Fallback navigate even on fail
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    } else {
                        val errorMessage = when (task.exception) {
                            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No account found with this email."
                            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Incorrect password. Please try again."
                            else -> task.exception?.localizedMessage ?: "Authentication failed."
                        }
                        layoutEmail.error = errorMessage
                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

        textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
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
                val result = credentialManager.getCredential(this@LoginActivity, request)
                handleSignIn(result.credential)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential error: ${e.localizedMessage}")
                val msg = e.localizedMessage ?: "Unknown error"
                Toast.makeText(this@LoginActivity, "Google Sign-In failed: $msg (Check Firebase SHA-1)", Toast.LENGTH_LONG).show()
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

                    // Trigger initial sync to upload any local data to the new cloud account
                    lifecycleScope.launch {
                        try {
                            val database = com.example.hastakalashop.data.AppDatabase.getDatabase(this@LoginActivity)
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
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "Initial sync failed: ${e.localizedMessage}")
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Firebase Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
