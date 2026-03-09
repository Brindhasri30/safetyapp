package com.example.womensafetyapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class UserDetailsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_details)

        db = FirebaseFirestore.getInstance()

        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            saveToFirestore()
        }
    }

    private fun saveToFirestore() {

        val userData = hashMapOf(
            "name" to findViewById<EditText>(R.id.etName).text.toString(),
            "phone" to findViewById<EditText>(R.id.etPhone).text.toString(),
            "email" to findViewById<EditText>(R.id.etEmail).text.toString(),
            "address" to findViewById<EditText>(R.id.etAddress).text.toString(),
            "guardian1Name" to findViewById<EditText>(R.id.etG1Name).text.toString(),
            "guardian1Phone" to findViewById<EditText>(R.id.etG1Phone).text.toString(),
            "guardian2Name" to findViewById<EditText>(R.id.etG2Name).text.toString(),
            "guardian2Phone" to findViewById<EditText>(R.id.etG2Phone).text.toString()
        )

        db.collection("Users")
            .add(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
