package com.example.womensafetyapp
import com.google.firebase.database.FirebaseDatabase

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.*
import kotlin.math.sqrt
import android.widget.ImageView

class MainActivity : AppCompatActivity(), SensorEventListener {
    private fun saveGuardiansToFirebase(g1: String, g2: String) {
        val userId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )

        val database = FirebaseDatabase.getInstance()
        val userRef = database.getReference("users").child(userId)

        val guardianData = mapOf(
            "guardian1" to g1,
            "guardian2" to g2
        )

        userRef.setValue(guardianData)
            .addOnSuccessListener {
                Toast.makeText(this, "Saved to Firebase", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Firebase failed", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- DUAL TRIGGER FLAGS ----------
    private var shakeDetected = false
    private var voiceDetected = false
    private val CONFIRMATION_WINDOW = 10_000L // 10 seconds


    private val dangerKeywords = listOf(
        "help",
        "help me",
        "save me",
        "emergency",
        "bachao",
        "please help"
    )

    // ---------- UI & SYSTEM ----------
    private lateinit var etG1: EditText
    private lateinit var etG2: EditText
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var speechRecognizer: SpeechRecognizer

    // ---------- SHAKE VARIABLES ----------
    private var accel = 0f
    private var accelCurrent = 0f
    private var accelLast = 0f

    private var lastSOSTime = 0L
    private val SOS_COOLDOWN = 30_000 // 30 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etG1 = findViewById(R.id.etGuardian1)
        etG2 = findViewById(R.id.etGuardian2)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveGuardians() }
        findViewById<Button>(R.id.btnSOS).setOnClickListener { triggerSOS() }

        loadGuardians()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )

        accel = 10f
        accelCurrent = SensorManager.GRAVITY_EARTH
        accelLast = SensorManager.GRAVITY_EARTH

        requestAppPermissions()
        requestCallPermission()

        val settingsBtn = findViewById<ImageView>(R.id.btnSettings)

        settingsBtn.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        startVoiceListening()
    }

    // ---------- PERMISSIONS ----------
    private fun requestAppPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        ActivityCompat.requestPermissions(this, permissions, 101)
    }

    private fun requestCallPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                102
            )
        }
    }

    // ---------- GUARDIANS ----------
    private fun saveGuardians() {
        val g1 = etG1.text.toString()
        val g2 = etG2.text.toString()

        if (g1.isEmpty() || g2.isEmpty()) {
            Toast.makeText(this, "Enter both numbers", Toast.LENGTH_SHORT).show()
            return
        }

        // Save locally
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("G1", g1)
            .putString("G2", g2)
            .apply()

        // Save online (Firebase)
        saveGuardiansToFirebase(g1, g2)
    }


    private fun loadGuardians() {
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        etG1.setText(prefs.getString("G1", ""))
        etG2.setText(prefs.getString("G2", ""))
    }

    // ---------- SOS ----------
    private fun triggerSOS() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAppPermissions()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) sendSOS(location)
            else Toast.makeText(this, "Location not found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSOS(location: Location) {
        val prefs = getSharedPreferences("GUARDIANS", Context.MODE_PRIVATE)
        val g1 = prefs.getString("G1", "")!!
        val g2 = prefs.getString("G2", "")!!

        val message = """
        [EMERGENCY ALERT]
        I need help!
        My location: https://maps.google.com/?q=${location.latitude},${location.longitude}
    """.trimIndent()

        sendRepeatedSOS(g1, g2, message)

        callNumber(g1)

        Handler(Looper.getMainLooper()).postDelayed({
            callNumber(g2)
        }, 20_000L)
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

    // ---------- SHAKE DETECTION ----------
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

            Handler(Looper.getMainLooper()).postDelayed({
                shakeDetected = false
            }, CONFIRMATION_WINDOW)

            checkAndTriggerSOS()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- VOICE DETECTION ----------
    private fun startVoiceListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
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
                if (text.contains(keyword, ignoreCase = true)) {

                    voiceDetected = true

                    Handler(Looper.getMainLooper()).postDelayed({
                        voiceDetected = false
                    }, CONFIRMATION_WINDOW)

                    // checkAndTriggerSOS()  ❌ REMOVED
                    return
                }
            }
        }
    }

    private fun restartListening(intent: Intent) {
        Handler(Looper.getMainLooper()).postDelayed({
            speechRecognizer.startListening(intent)
        }, 500)
    }

    // ---------- FINAL DECISION ----------
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
    private fun sendRepeatedSOS(g1: String, g2: String, message: String) {
        val handler = Handler(Looper.getMainLooper())
        var count = 0

        val runnable = object : Runnable {
            override fun run() {
                if (count < 5) {

                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        SmsManager.getDefault().sendTextMessage(g1, null, message, null, null)
                        SmsManager.getDefault().sendTextMessage(g2, null, message, null, null)
                    }

                    count++
                    handler.postDelayed(this, 30_000L) // 30 seconds gap
                }
            }
        }

        handler.post(runnable)
    }

}
