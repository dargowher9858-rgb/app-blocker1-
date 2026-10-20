package com.myblocker.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // 1. Button to open Android Accessibility Settings
        val btnService = Button(this).apply {
            text = "Step 1: Enable Accessibility Service"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        layout.addView(btnService)

        // 2. Master ON/OFF Switch so you don't have to untick apps to pause blocking
        val switchMaster = Switch(this).apply {
            text = "Step 2: Blocker Active (ON / OFF)"
            textSize = 18f
            setPadding(0, 40, 0, 40)
            isChecked = prefs.getBoolean("is_enabled", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("is_enabled", isChecked).commit()
            }
        }
        layout.addView(switchMaster)

        val title = TextView(this).apply {
            text = "Select Apps to Block:"
            textSize = 20f
            setPadding(0, 10, 0, 20)
        }
        layout.addView(title)

        val scroll = ScrollView(this)
        val appListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

        // Detect default Home Launcher so we never block the Home Screen
        val homeIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_HOME) }
        val homePackages = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()

        // Critical packages that must NEVER be blocked
        val safePackages = setOf(
            "com.myblocker.app",
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller"
        ) + homePackages

        pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { !safePackages.contains(it) }
            .sortedBy { pkg ->
                try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase() }
                catch (e: Exception) { pkg }
            }
            .forEach { pkgName ->
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString()
                } catch (e: Exception) {
                    pkgName
                }
                val cb = CheckBox(this).apply {
                    text = appName
                    textSize = 18f
                    setPadding(10, 25, 10, 25)
                    isChecked = blockedApps.contains(pkgName)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) blockedApps.add(pkgName) else blockedApps.remove(pkgName)
                        // Save a fresh HashSet copy and commit synchronously to avoid Android's Set caching bug
                        prefs.edit().putStringSet("blocked_apps", HashSet(blockedApps)).commit()
                    }
                }
                appListLayout.addView(cb)
            }

        scroll.addView(appListLayout)
        layout.addView(scroll)
        setContentView(layout)
    }
}
