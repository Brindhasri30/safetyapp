package com.example.womensafetyapp


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val SPLASH_TIME = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Apply animations for visual polish
        val center = findViewById<LinearLayout>(R.id.splash_center)
        val appIcon = findViewById<ImageView>(R.id.app_icon)
        center.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        appIcon.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))

        Handler(Looper.getMainLooper()).postDelayed({
            // Check if user is already logged in
            val prefs = getSharedPreferences("USER", MODE_PRIVATE)
            val loggedIn = prefs.getBoolean("isLoggedIn", false)

            if (loggedIn) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, SPLASH_TIME)
    }
}
