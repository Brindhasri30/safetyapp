package com.example.womensafetyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.location.Location
import android.net.Uri
import android.os.*
import android.speech.*
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.sqrt
import java.util.*
import android.content.BroadcastReceiver
import android.content.IntentFilter


class MainActivity : AppCompatActivity(), SensorEventListener {
    private val sosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SOS_TRIGGER") {
                triggerSOS()
            }
        }
    }
    // ---------------- UI ----------------
    private lateinit var etG1: EditText
    private lateinit var etG2: EditText

    // ---------------- LOCATION ----------------
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ---------------- SHAKE ----------------
    private lateinit var sensorManager: SensorManager
    private var accel = 0f
    private var accelCurrent = 0f
    private var accelLast = 0f

    // ---------------- VOICE ----------------
    private lateinit var speechRecognizer: SpeechRecognizer

    // ---------------- FLAGS ----------------
    private var shakeDetected = false
    private var voiceDetected = false

    private val CONFIRMATION_WINDOW = 10_000L
    private val SOS_COOLDOWN = 30_000L
    private var lastSOSTime = 0L

    private val dangerKeywords = listOf(
        "help", "help me", "save me",
        "emergency", "please help"
    )


    // ==========================================================
    // ON CREATE
    // ==========================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        etG1 = findViewById(R.id.etGuardian1)
        etG2 = findViewById(R.id.etGuardian2)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveGuardians() }
        findViewById<Button>(R.id.btnSOS).setOnClickListener { triggerSOS() }

        val intent = Intent(this, SOSForegroundService::class.java)
//        startForegroundService(intent)

        // SETTINGS BUTTON
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accel = 10f
        accelCurrent = SensorManager.GRAVITY_EARTH
        accelLast = SensorManager.GRAVITY_EARTH

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startAppLogic()
        }
    }
    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    private fun startAppLogic() {
        loadGuardians()
    }

    // ==========================================================
    // PERMISSIONS
    // ==========================================================
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE
        )
        ActivityCompat.requestPermissions(this, permissions, 101)
    }

    override fun onRequestPermissionsResult(

        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {

            startVoiceListening()
            startAppLogic()
        }
    }

    // ==========================================================
    // LIFECYCLE
    // ==========================================================
    override fun onResume() {
        super.onResume()

        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
//        registerReceiver(sosReceiver, IntentFilter("SOS_TRIGGER"))
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceListening()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        try {
            unregisterReceiver(sosReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    // ==========================================================
    // SHAKE DETECTION (STABLE)
    // ==========================================================
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        accelLast = accelCurrent
        accelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = accelCurrent - accelLast
        accel = accel * 0.9f + delta

        if (accel > 12) {

            shakeDetected = true
            Toast.makeText(this, "Shake Detected!", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                shakeDetected = false
            }, CONFIRMATION_WINDOW)

            checkAndTriggerSOS()
        }
        if (intent.getBooleanExtra("triggerSOS", false)) {
            triggerSOS()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ==========================================================
    // VOICE DETECTION (STABLE)
    // ==========================================================
    private fun startVoiceListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech not available", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.let { checkForDangerWords(it) }

                restartListening(intent)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )?.let { checkForDangerWords(it) }
            }

            override fun onError(error: Int) {
                restartListening(intent)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun checkForDangerWords(matches: List<String>) {
        for (text in matches) {
            for (keyword in dangerKeywords) {
                if (text.contains(keyword, true)) {

                    voiceDetected = true
                    Toast.makeText(this, "Voice Triggered!", Toast.LENGTH_SHORT).show()

                    Handler(Looper.getMainLooper()).postDelayed({
                        voiceDetected = false
                    }, CONFIRMATION_WINDOW)

                    checkAndTriggerSOS()
                    return
                }
            }
        }
    }

    private fun restartListening(intent: Intent) {
        Handler(Looper.getMainLooper()).postDelayed({
            speechRecognizer.startListening(intent)
        }, 600)
    }

    // ==========================================================
    // FINAL TRIGGER (DUAL SECURITY)
    // ==========================================================
    private fun checkAndTriggerSOS() {

        if (shakeDetected && voiceDetected) {

            val now = System.currentTimeMillis()
            if (now - lastSOSTime > SOS_COOLDOWN) {

                lastSOSTime = now
                shakeDetected = false
                voiceDetected = false

                triggerSOS()
            }
        }
    }

    // ==========================================================
    // SOS LOGIC
    // ==========================================================
    private fun triggerSOS() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission missing", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->

                if (location != null) {
                    sendSOS(location)
                } else {
                    Toast.makeText(this, "Turn ON GPS and try again", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Location Failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sendSOS(location: Location) {

        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        val g1 = prefs.getString("G1", "")!!
        val g2 = prefs.getString("G2", "")!!

        if (g1.isEmpty() || g2.isEmpty()) {
            Toast.makeText(this, "Save guardian numbers first!", Toast.LENGTH_LONG).show()
            return
        }

        val message = "EMERGENCY! I need help.\nLocation: https://maps.google.com/?q=${location.latitude},${location.longitude}"

        try {

            val smsManager = getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(g1, null, message, null, null)
            smsManager.sendTextMessage(g2, null, message, null, null)

            Toast.makeText(this, "SOS SMS Sent", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "SMS Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        // Call Guardian 1
        callNumber(g1)

        Handler(Looper.getMainLooper()).postDelayed({

            // Try second only after delay
            callNumber(g2)

        }, 20000) // increase delay for real-world usage

    }

    private fun callNumber(number: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$number")
        startActivity(intent)
    }

    // ==========================================================
    // GUARDIANS (LOCAL + FIRESTORE)
    // ==========================================================
    private fun saveGuardians() {

        val g1 = etG1.text.toString()
        val g2 = etG2.text.toString()

        if (g1.isEmpty() || g2.isEmpty()) {
            Toast.makeText(this, "Enter both numbers", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        prefs.edit().putString("G1", g1).putString("G2", g2).apply()

        saveGuardiansToFirestore(g1, g2)
        Toast.makeText(this, "Saved Successfully", Toast.LENGTH_SHORT).show()
    }

    private fun loadGuardians() {
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        etG1.setText(prefs.getString("G1", ""))
        etG2.setText(prefs.getString("G2", ""))
    }

    private fun saveGuardiansToFirestore(g1: String, g2: String) {

        val userId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )

        val db = FirebaseFirestore.getInstance()

        val data = hashMapOf(
            "guardian1" to g1,
            "guardian2" to g2,
            "updatedAt" to System.currentTimeMillis()
        )

        db.collection("users").document(userId).set(data)
    }

}