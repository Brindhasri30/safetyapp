package com.example.womensafetyapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {

    private lateinit var etGuardian1: EditText
    private lateinit var etGuardian2: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLogout: Button

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etGuardian1 = findViewById(R.id.etGuardian1)
        etGuardian2 = findViewById(R.id.etGuardian2)
        btnSave = findViewById(R.id.btnChangeGuardians)
        btnLogout = findViewById(R.id.btnLogout)

        sharedPreferences = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        // Load saved guardian numbers
        loadGuardians()

        btnSave.setOnClickListener {
            saveGuardians()
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun saveGuardians() {

        val guardian1 = etGuardian1.text.toString()
        val guardian2 = etGuardian2.text.toString()

        if (guardian1.isEmpty() || guardian2.isEmpty()) {
            Toast.makeText(this, "Enter both guardian numbers", Toast.LENGTH_SHORT).show()
            return
        }

        val editor = sharedPreferences.edit()
        editor.putString("guardian1", guardian1)
        editor.putString("guardian2", guardian2)
        editor.apply()

        Toast.makeText(this, "Guardian numbers saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadGuardians() {

        val guardian1 = sharedPreferences.getString("guardian1", "")
        val guardian2 = sharedPreferences.getString("guardian2", "")

        etGuardian1.setText(guardian1)
        etGuardian2.setText(guardian2)
    }

    private fun logoutUser() {

        auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }
}