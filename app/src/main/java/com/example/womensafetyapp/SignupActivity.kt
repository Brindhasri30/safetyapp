package com.example.womensafetyapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var btnBackToLogin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnBackToLogin = findViewById(R.id.btnBackToLogin)

        btnCreateAccount.setOnClickListener {
            createAccount()
        }

        btnBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun createAccount() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Enter a valid email"
            return
        }

        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            return
        }

        btnCreateAccount.isEnabled = false
        btnCreateAccount.text = "Creating..."

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->

                btnCreateAccount.isEnabled = true
                btnCreateAccount.text = "Create Secure Account"

                if (task.isSuccessful) {

                    // Send Email Verification (Important for safety apps)
                    auth.currentUser?.sendEmailVerification()

                    Toast.makeText(
                        this,
                        "Account created. Please verify your email.",
                        Toast.LENGTH_LONG
                    ).show()

                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()

                } else {
                    Toast.makeText(
                        this,
                        task.exception?.message ?: "Sign Up failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}