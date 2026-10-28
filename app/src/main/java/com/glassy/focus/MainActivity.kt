package com.glassy.focus

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Glassy Background Style
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 80, 60, 80)
            setBackgroundColor(Color.parseColor("#E0E5EC")) // Light modern background
        }

        val glassBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#99FFFFFF")) // Translucent white (Glass)
                setStroke(3, Color.parseColor("#4DFFFFFF")) // Subtle border
            }
        }

        // PERMISSION BUTTONS
        val btnUsage = createGlassButton("1. Allow Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        val btnOverlay = createGlassButton("2. Allow Display Over Apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val btnBattery = createGlassButton("3. Ignore Battery Optimization") {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Battery already optimized!", Toast.LENGTH_SHORT).show()
            }
        }

        glassBox.addView(btnUsage)
        glassBox.addView(btnOverlay)
        glassBox.addView(btnBattery)

        // TIMER INPUT
        val timeInput = EditText(this).apply {
            hint = "Enter Focus Minutes (e.g. 60)"
            textSize = 18f
            setPadding(0, 40, 0, 40)
        }
        glassBox.addView(timeInput)

        // START BUTTON
        val btnStart = createGlassButton("START FOCUS SESSION") {
            val mins = timeInput.text.toString().toLongOrNull() ?: 0L
            if (mins > 0) {
                val intent = Intent(this, FocusService::class.java).apply {
                    putExtra("MINUTES", mins)
                }
                startForegroundService(intent)
                Toast.makeText(this, "Session Started! Close app to begin.", Toast.LENGTH_LONG).show()
            }
        }
        btnStart.setBackgroundColor(Color.parseColor("#4CAF50"))
        btnStart.setTextColor(Color.WHITE)
        glassBox.addView(btnStart)

        // INFO TEXT
        val infoText = TextView(this).apply {
            text = "\nCheck notifications for your 3 Break Passwords. The Final Password will appear when the timer ends."
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        glassBox.addView(infoText)

        rootLayout.addView(glassBox)
        setContentView(rootLayout)
    }

    private fun createGlassButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#B3FFFFFF"))
            }
            setPadding(0, 20, 0, 20)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 15, 0, 15)
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }
}
