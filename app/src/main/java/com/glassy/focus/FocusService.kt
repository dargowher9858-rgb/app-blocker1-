package com.glassy.focus

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class FocusService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("focus_chan", "Focus", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notif = Notification.Builder(this, "focus_chan")
            .setContentTitle("Focus Vault Active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .build()
        startForeground(1, notif)

        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                val isActive = prefs.getBoolean("is_active", false)
                if (!isActive) {
                    removeOverlay()
                    stopSelf()
                    return
                }

                val tempUnlockUntil = prefs.getLong("temp_unlock_until", 0L)
                val currentTime = System.currentTimeMillis()
                
                // If on a break, do nothing
                if (currentTime < tempUnlockUntil) {
                    removeOverlay()
                    handler.postDelayed(this, 2000)
                    return
                }

                val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
                val foregroundApp = getForegroundApp()
                
                if (foregroundApp.isNotEmpty() && blockedApps.contains(foregroundApp)) {
                    if (overlayView == null) showGlassOverlay()
                } else {
                    // Only remove overlay if they leave the blocked app, allowing them to use safe apps
                    removeOverlay() 
                }

                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun showGlassOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            background = GradientDrawable().apply { setColor(Color.parseColor("#F2121212")) } // Solid frosted dark
        }

        val title = TextView(this).apply {
            text = "APP LOCKED"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        
        val endTime = prefs.getLong("end_time", 0L)
        val breaks = prefs.getStringSet("break_passes", emptySet())?.toMutableSet() ?: mutableSetOf()
        val finalPass = prefs.getString("final_pass", "")
        val isTimerOver = System.currentTimeMillis() >= endTime

        val subTitle = TextView(this).apply {
            text = if (isTimerOver) "Session Complete!\nUnlock Password: $finalPass" 
                   else "Focus session active.\nBreaks remaining: ${breaks.size}\nBreak Passwords: ${breaks.joinToString(", ")}"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        val input = EditText(this).apply {
            hint = "Enter Password"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setPadding(40, 40, 40, 40)
        }

        val btnUnlock = Button(this).apply {
            text = "UNLOCK"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0A84FF"))
            }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 30, 0, 0)
            layoutParams = p
            
            setOnClickListener {
                val typed = input.text.toString().uppercase()
                
                if (isTimerOver && typed == finalPass) {
                    prefs.edit().putBoolean("is_active", false).apply()
                    removeOverlay()
                } else if (!isTimerOver && breaks.contains(typed)) {
                    breaks.remove(typed)
                    prefs.edit()
                        .putStringSet("break_passes", HashSet(breaks))
                        .putLong("temp_unlock_until", System.currentTimeMillis() + (5 * 60 * 1000)) // 5 minute break
                        .apply()
                    removeOverlay()
                    Toast.makeText(this@FocusService, "5 Minute Break Started!", Toast.LENGTH_LONG).show()
                } else {
                    input.setText("")
                    input.hint = "Incorrect Password"
                }
            }
        }

        overlayView?.addView(title)
        overlayView?.addView(subTitle)
        overlayView?.addView(input)
        overlayView?.addView(btnUnlock)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
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
        val events = usm.queryEvents(time - 2000, time)
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

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
