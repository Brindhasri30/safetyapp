package com.example.womensafetyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private var lastPressTime = 0L
        private var pressCount = 0
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_SCREEN_ON) {

            val currentTime = SystemClock.elapsedRealtime()

            if (currentTime - lastPressTime < 1500) {
                pressCount++
            } else {
                pressCount = 1
            }

            lastPressTime = currentTime

            if (pressCount >= 3) {

                pressCount = 0

                Toast.makeText(context, "Emergency Triggered!", Toast.LENGTH_LONG).show()

                val sosIntent = Intent(context, MainActivity::class.java)
                sosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                sosIntent.putExtra("triggerSOS", true)

                context.startActivity(sosIntent)
            }
        }
    }
}