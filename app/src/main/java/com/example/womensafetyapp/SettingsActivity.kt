package com.example.womensafetyapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {

    private lateinit var etG1: EditText
    private lateinit var etG2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etG1 = findViewById(R.id.etGuardian1)
        etG2 = findViewById(R.id.etGuardian2)
        btnSave = findViewById(R.id.btnChangeGuardians)
        btnLogout = findViewById(R.id.btnLogout)

        loadGuardians()

        btnSave.setOnClickListener { saveGuardians() }
        btnLogout.setOnClickListener { logoutUser() }
    }

    private fun loadGuardians() {
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        etG1.setText(prefs.getString("G1", ""))
        etG2.setText(prefs.getString("G2", ""))
    }

    private fun saveGuardians() {
        val g1 = etG1.text.toString().trim()
        val g2 = etG2.text.toString().trim()

        if (g1.isEmpty() || g2.isEmpty()) {
            Toast.makeText(this, "Enter both guardian numbers", Toast.LENGTH_SHORT).show()
            return
        }

        // Save locally
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        prefs.edit().putString("G1", g1).putString("G2", g2).apply()

        // Save to Firebase
        val userId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("users").child(userId)

        val guardianData = mapOf("guardian1" to g1, "guardian2" to g2)
        userRef.setValue(guardianData)
            .addOnSuccessListener {
                Toast.makeText(this, "Guardians Updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Firebase update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logoutUser() {
        // Clear login session
        val prefs = getSharedPreferences("USER", MODE_PRIVATE)
        prefs.edit().putBoolean("isLoggedIn", false).apply()

        // Sign out Firebase
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
