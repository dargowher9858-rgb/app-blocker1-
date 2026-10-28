package com.glassy.focus

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class FocusService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Distracting apps to block (You can add more package names here)
    private val blockedApps = listOf("com.instagram.android", "com.google.android.youtube", "com.facebook.katana")
    
    private var finalPassword = ""
    private var breakPasswords = mutableListOf<String>()
    private var timerEndTime = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val minutes = intent?.getLongExtra("MINUTES", 1L) ?: 1L
        timerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000)

        // Generate Random Passwords
        finalPassword = generateRandomPass(6)
        breakPasswords.clear()
        repeat(3) { breakPasswords.add(generateRandomPass(4)) }

        createNotificationChannel()
        val notification = Notification.Builder(this, "focus_channel")
            .setContentTitle("Focus Session Active")
            .setContentText("Break Passwords: ${breakPasswords.joinToString(", ")}")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        startMonitoring()
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                val foregroundApp = getForegroundApp()
                
                if (blockedApps.contains(foregroundApp) && overlayView == null) {
                    showGlassOverlay()
                }

                // Check if timer is done
                if (System.currentTimeMillis() >= timerEndTime) {
                    updateNotification("Session Complete!", "Final Password: $finalPassword")
                    stopSelf()
                    return
                }

                handler.postDelayed(this, 2000) // Check every 2 seconds
            }
        })
    }

    private fun showGlassOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            
            // Glassy Dark Blur Effect
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6000000")) // 90% Black
            }
        }

        val title = TextView(this).apply {
            text = "Focus Mode Active\nTime remaining..."
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        val input = EditText(this).apply {
            hint = "Enter Password..."
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            textSize = 20f
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#4DFFFFFF")) // Translucent glass input
                setStroke(2, Color.WHITE)
            }
            setPadding(40, 40, 40, 40)
        }

        val btnUnlock = Button(this).apply {
            text = "UNLOCK"
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#B3FFFFFF"))
            }
            setOnClickListener {
                val typed = input.text.toString()
                if (typed == finalPassword) {
                    removeOverlay()
                    stopSelf()
                } else if (breakPasswords.contains(typed)) {
                    breakPasswords.remove(typed) // Consume the break password
                    removeOverlay()
                    // Re-trigger overlay check after 5 minutes (300,000 ms)
                    handler.postDelayed({ overlayView = null }, 300000)
                } else {
                    input.setText("")
                    input.hint = "Wrong Password!"
                }
            }
        }

        overlayView?.addView(title)
        overlayView?.addView(input)
        
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 40, 0, 0)
        overlayView?.addView(btnUnlock, params)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        windowManager.addView(overlayView, layoutParams)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun getForegroundApp(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 3000, time)
        var currentApp = ""
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
            }
        }
        return currentApp
    }

    private fun generateRandomPass(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed confusing chars like 0, O, I, 1
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("focus_channel", "Focus Timer", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notification = Notification.Builder(this, "focus_channel")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
