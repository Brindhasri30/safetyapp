package com.example.womensafetyapp

import android.app.*
import android.content.Intent
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat

class SOSForegroundService : Service() {

    private lateinit var mediaRecorder: MediaRecorder
    private var screamCount = 0

    private val SCREAM_THRESHOLD = 15000
    private val SCREAM_TRIGGER_COUNT = 5

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification: Notification = NotificationCompat.Builder(this, "SOS_CHANNEL")
            .setContentTitle("Women Safety App")
            .setContentText("Protection Active (Listening...)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)

        startScreamDetection()

        return START_STICKY
    }

    private fun startScreamDetection() {

        mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder.setOutputFile("/dev/null")

        try {
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val handler = Handler(Looper.getMainLooper())

        handler.post(object : Runnable {
            override fun run() {

                try {
                    val amplitude = mediaRecorder.maxAmplitude

                    if (amplitude > SCREAM_THRESHOLD) {
                        screamCount++
                    } else {
                        screamCount = 0
                    }

                    if (screamCount >= SCREAM_TRIGGER_COUNT) {
                        sendSOSBroadcast()
                        screamCount = 0
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                handler.postDelayed(this, 500)
            }
        })
    }

    private fun sendSOSBroadcast() {
        val intent = Intent("SOS_TRIGGER")
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaRecorder.stop()
            mediaRecorder.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SOS_CHANNEL",
                "SOS Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}