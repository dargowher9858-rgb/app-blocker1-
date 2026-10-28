package com.glassy.focus

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerView: TextView
    private lateinit var statusView: TextView
    private lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 80, 50, 80)
            setBackgroundColor(Color.parseColor("#0F0F13")) // Deep iPhone dark background
        }

        val title = TextView(this).apply {
            text = "Focus Vault"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(20, 0, 0, 40)
        }
        rootLayout.addView(title)

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout.addView(contentLayout)

        setContentView(rootLayout)
        startUIUpdater()
    }

    private fun startUIUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                renderUI()
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun renderUI() {
        val prefs = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_active", false)
        val endTime = prefs.getLong("end_time", 0L)
        val currentTime = System.currentTimeMillis()

        contentLayout.removeAllViews()

        if (isActive) {
            // --- ACTIVE SESSION UI ---
            val glassCard = createGlassCard()
            
            val statusLabel = TextView(this).apply {
                text = if (currentTime < endTime) "SESSION IN PROGRESS" else "SESSION COMPLETE"
                textSize = 14f
                setTextColor(if (currentTime < endTime) Color.parseColor("#FF9F0A") else Color.parseColor("#32D74B"))
                setPadding(0, 0, 0, 20)
            }
            
            timerView = TextView(this).apply {
                textSize = 54f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }

            if (currentTime < endTime) {
                val diff = endTime - currentTime
                val hours = (diff / (1000 * 60 * 60)) % 24
                val mins = (diff / (1000 * 60)) % 60
                val secs = (diff / 1000) % 60
                timerView.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs)
                
                val breaks = prefs.getStringSet("break_passes", emptySet()) ?: emptySet()
                statusView = TextView(this).apply {
                    text = "\nBreak Passwords Remaining: ${breaks.size}\n(Check Lock Screen to use them)"
                    setTextColor(Color.LTGRAY)
                    textSize = 16f
                }
                glassCard.addView(statusLabel)
                glassCard.addView(timerView)
                glassCard.addView(statusView)
            } else {
                timerView.text = "00:00:00"
                val finalPass = prefs.getString("final_pass", "ERROR")
                
                statusView = TextView(this).apply {
                    text = "\nYour Unlock Password is:\n$finalPass\n\nGo unlock your apps!"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                }
                
                val btnEnd = createButton("END SESSION & RESET") {
                    prefs.edit().putBoolean("is_active", false).apply()
                    stopService(Intent(this, FocusService::class.java))
                    renderUI()
                }
                
                glassCard.addView(statusLabel)
                glassCard.addView(timerView)
                glassCard.addView(statusView)
                glassCard.addView(btnEnd)
            }
            contentLayout.addView(glassCard)

        } else {
            // --- SETUP UI ---
            val glassCard = createGlassCard()
            
            val setupTitle = TextView(this).apply {
                text = "1. Select Apps to Block"
                setTextColor(Color.WHITE)
                textSize = 18f
                setPadding(0, 0, 0, 20)
            }
            glassCard.addView(setupTitle)

            val blockedApps = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val homeIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_HOME) }
            val safePackages = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet() + 
                               setOf("com.glassy.focus", "com.android.settings", "com.android.systemui")

            val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400) }
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

            val timeInput = EditText(this).apply {
                hint = "Focus Time (Minutes)"
                setHintTextColor(Color.GRAY)
                setTextColor(Color.WHITE)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                background = GradientDrawable().apply {
                    cornerRadius = 15f
                    setColor(Color.parseColor("#33FFFFFF"))
                }
                setPadding(40, 40, 40, 40)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 40, 0, 40)
                layoutParams = p
            }
            glassCard.addView(timeInput)

            val btnStart = createButton("START SESSION") {
                val mins = timeInput.text.toString().toLongOrNull() ?: 0L
                if (mins > 0 && blockedApps.isNotEmpty()) {
                    val finalP = generatePass(6)
                    val breaks = setOf(generatePass(4), generatePass(4), generatePass(4))
                    
                    prefs.edit()
                        .putBoolean("is_active", true)
                        .putLong("end_time", System.currentTimeMillis() + (mins * 60 * 1000))
                        .putString("final_pass", finalP)
                        .putStringSet("break_passes", breaks)
                        .commit()
                        
                    startForegroundService(Intent(this, FocusService::class.java))
                    renderUI()
                } else {
                    Toast.makeText(this, "Select apps and enter time!", Toast.LENGTH_SHORT).show()
                }
            }
            glassCard.addView(btnStart)
            
            // Permissions Shortcut
            val btnPerms = createButton("Fix Permissions") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            btnPerms.setBackgroundColor(Color.TRANSPARENT)
            btnPerms.setTextColor(Color.parseColor("#0A84FF"))
            glassCard.addView(btnPerms)

            contentLayout.addView(glassCard)
        }
    }

    private fun createGlassCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#1AFFFFFF")) // Frosted glass
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
                setColor(Color.parseColor("#0A84FF")) // iOS Blue
            }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.setMargins(0, 20, 0, 0)
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
