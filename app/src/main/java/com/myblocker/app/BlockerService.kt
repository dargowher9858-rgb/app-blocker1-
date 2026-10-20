package com.myblocker.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class BlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Hardcoded safety bypass: Never block our own app or Android Settings
            if (packageName == "com.myblocker.app" || packageName == "com.android.settings") return

            val prefs = getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
            
            // Check if the Master ON/OFF Switch is turned on
            val isEnabled = prefs.getBoolean("is_enabled", true)
            if (!isEnabled) return

            val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

            if (blockedApps.contains(packageName)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {}
}
