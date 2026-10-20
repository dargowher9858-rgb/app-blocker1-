package com.myblocker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
        val blockedApps = prefs.getStringSet("blocked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val btnService = Button(this).apply {
            text = "Enable Blocker in Android Settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        layout.addView(btnService)
        
        val title = TextView(this).apply {
            text = "\nSelect Apps to Block:"
            textSize = 20f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        val scroll = ScrollView(this)
        val appListLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        
        pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.distinct()
            .sortedBy { pkg -> try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch(e: Exception) { pkg } }
            .forEach { pkgName ->
                val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0)).toString() } catch(e: Exception) { pkgName }
                val cb = CheckBox(this).apply {
                    text = appName
                    textSize = 18f
                    setPadding(0, 20, 0, 20)
                    isChecked = blockedApps.contains(pkgName)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) blockedApps.add(pkgName) else blockedApps.remove(pkgName)
                        prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
                    }
                }
                appListLayout.addView(cb)
            }

        scroll.addView(appListLayout)
        layout.addView(scroll)
        setContentView(layout)
    }
}

