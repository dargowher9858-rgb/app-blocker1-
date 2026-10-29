package com.glassy.focus

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#0F0F13"))
        }

        val title = TextView(this).apply {
            text = "Focus Vault"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(10, 0, 0, 30)
        }
        rootLayout.addView(title)

        // Make the main interface scrollable so buttons never cut off
        val mainScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        mainScroll.addView(contentLayout)
        rootLayout.addView(mainScroll)
        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        checkStateAndRender()
    }

    private fun checkStateAndRender() {
        handler.removeCallbacksAndMessages(null)
        contentLayout.removeAllViews()
        
        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_active", false)
        val endTime = prefs.getLong("end_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (isActive && currentTime >= endTime) {
            renderFinishedUI(prefs)
        } else if (isActive) {
            renderActiveUI(prefs)
        } else {
            renderSetupUI(prefs)
        }
    }

    private fun renderSetupUI(prefs: android.content.SharedPreferences) {
        val glassCard = createGlassCard()
        
        val setupTitle = TextView(this).apply {
            text = "1. Grant Permissions"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 10)
        }
        glassCard.addView(setupTitle)

        val btnUsage = createButton("Allow Usage Access") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnUsage.setBackgroundColor(Color.parseColor("#33FFFFFF"))
        
        val btnOverlay = createButton("Allow Display Over Apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnOverlay.setBackgroundColor(Color.parseColor("#33FFFFFF"))
        
        val btnBattery = createButton("Ignore Battery Optimization") {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Battery already optimized!", Toast.LENGTH_SHORT).show()
            }
        }
        btnBattery.setBackgroundColor(Color.parseColor("#33FFFFFF"))

        glassCard.addView(btnUsage)
        glassCard.addView(btnOverlay)
        glassCard.addView(btnBattery)

        val appTitle = TextView(this).apply {
            text = "\n2. Select Apps to Block"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 20, 0, 20)
        }
        glassCard.addView(appTitle)

        val blockedApps = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val homeIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_HOME) }
        val safePackages = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet() + 
                           setOf("com.glassy.focus", "com.android.settings", "com.android.systemui")

        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 350) }
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        pm.queryIntentActivities(mainIntent, 0).map { it.activityInfo.packageName }.distinct()
            .filter { !safePackages.contains(it) }
            .sortedBy { pkg -> try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase() } catch(e: Exception) { pkg } }
            .forEach { pkgName ->
                val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString() } catch(e: Exception) { pkgName }
                val cb = CheckBox(this).apply {
                    text = appName
                    setTextColor(Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0A84FF"))
                    setPadding(10, 15, 10, 15)
                    isChecked = blockedApps.contains(pkgName)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) blockedApps.add(pkgName) else blockedApps.remove(pkgName)
                        prefs.edit().putStringSet("blocked_apps", HashSet(blockedApps)).commit()
                    }
                }
                listLayout.addView(cb)
            }
        scroll.addView(listLayout)
        glassCard.addView(scroll)

        val clockTitle = TextView(this).apply {
            text = "\n3. Select Focus Duration"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 10, 0, 10)
        }
        glassCard.addView(clockTitle)

        // --- VINTAGE SCROLLABLE CLOCK WHEELS ---
        val pickerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setPadding(10, 10, 10, 10)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 10, 0, 30)
            layoutParams = p
        }

        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 24
            value = 0
            wrapSelectorWheel = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { textColor = Color.WHITE }
        }

        val hourLabel = TextView(this).apply {
            text = "h  "
            setTextColor(Color.LTGRAY)
            textSize = 18f
        }

        val minPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            value = 1 // Default 1 min for easy testing
            wrapSelectorWheel = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { textColor = Color.WHITE }
        }

        val minLabel = TextView(this).apply {
            text = "m"
            setTextColor(Color.LTGRAY)
            textSize = 18f
        }

        pickerLayout.addView(hourPicker)
        pickerLayout.addView(hourLabel)
        pickerLayout.addView(minPicker)
        pickerLayout.addView(minLabel)
        
        glassCard.addView(pickerLayout)

        // --- PROMINENT START BUTTON ---
        val btnStart = createButton("START SESSION") {
            val mins = (hourPicker.value * 60L) + minPicker.value
            
            if (mins > 0 && blockedApps.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Please allow Display Over Apps first!", Toast.LENGTH_LONG).show()
                    return@createButton
                }

                val finalP = generatePass(6)
                val breaks = setOf(generatePass(4), generatePass(4), generatePass(4))
                
                prefs.edit()
                    .putBoolean("is_active", true)
                    .putLong("end_time", System.currentTimeMillis() + (mins * 60 * 1000))
                    .putString("final_pass", finalP)
                    .putStringSet("break_passes", breaks)
                    .commit()
                    
                startForegroundService(Intent(this, FocusService::class.java))
                checkStateAndRender()
            } else {
                Toast.makeText(this, "Select apps and scroll the timer wheels!", Toast.LENGTH_SHORT).show()
            }
        }
        btnStart.setBackgroundColor(Color.parseColor("#32D74B")) // Bright green for visibility
        btnStart.textSize = 18f
        glassCard.addView(btnStart)

        contentLayout.addView(glassCard)
    }

    private fun renderActiveUI(prefs: android.content.SharedPreferences) {
        val glassCard = createGlassCard()
        
        val statusLabel = TextView(this).apply {
            text = "SESSION IN PROGRESS"
            textSize = 14f
            setTextColor(Color.parseColor("#FF9F0A"))
            setPadding(0, 0, 0, 20)
        }
        
        val timerView = TextView(this).apply {
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        
        val breaks = prefs.getStringSet("break_passes", emptySet()) ?: emptySet()
        val statusView = TextView(this).apply {
            text = "\nBreak Passwords Remaining: ${breaks.size}\n(Check Lock Screen to use them)"
            setTextColor(Color.LTGRAY)
            textSize = 16f
        }

        glassCard.addView(statusLabel)
        glassCard.addView(timerView)
        glassCard.addView(statusView)
        contentLayout.addView(glassCard)

        handler.post(object : Runnable {
            override fun run() {
                val endTime = prefs.getLong("end_time", 0L)
                val currentTime = System.currentTimeMillis()
                
                if (currentTime < endTime) {
                    val diff = endTime - currentTime
                    val hours = (diff / (1000 * 60 * 60)) % 24
                    val mins = (diff / (1000 * 60)) % 60
                    val secs = (diff / 1000) % 60
                    timerView.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
                    handler.postDelayed(this, 1000)
                } else {
                    checkStateAndRender()
                }
            }
        })
    }

    private fun renderFinishedUI(prefs: android.content.SharedPreferences) {
        val glassCard = createGlassCard()
        
        val statusLabel = TextView(this).apply {
            text = "SESSION COMPLETE"
            textSize = 14f
            setTextColor(Color.parseColor("#32D74B"))
            setPadding(0, 0, 0, 20)
        }
        
        val timerView = TextView(this).apply {
            text = "00:00:00"
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        val finalPass = prefs.getString("final_pass", "ERROR")
        val statusView = TextView(this).apply {
            text = "\nYour Unlock Password is:\n$finalPass\n\nGo unlock your apps!"
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        
        val btnEnd = createButton("END SESSION & RESET") {
            prefs.edit().putBoolean("is_active", false).apply()
            stopService(Intent(this, FocusService::class.java))
            checkStateAndRender()
        }
        btnEnd.setBackgroundColor(Color.parseColor("#FF453A"))
        
        glassCard.addView(statusLabel)
        glassCard.addView(timerView)
        glassCard.addView(statusView)
        glassCard.addView(btnEnd)
        
        contentLayout.addView(glassCard)
    }

    private fun createGlassCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#1AFFFFFF")) 
                setStroke(2, Color.parseColor("#33FFFFFF"))
            }
        }
    }

    private fun createButton(txt: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0A84FF"))
            }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 15, 0, 15)
            layoutParams = p
            setOnClickListener { onClick() }
        }
    }

    private fun generatePass(len: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..len).map { chars.random() }.joinToString("")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
