package com.myblocker.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent

class BlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Safety measure: Do not block the blocker app itself
            if (packageName == "com.myblocker.app") return

            val prefs = getSharedPreferences("block_prefs", Context.MODE_PRIVATE)
            val blockedApps = prefs.getStringSet("blocked_apps", setOf()) ?: setOf()
            
            if (blockedApps.contains(packageName)) {
                // Immediately kick the user back to the home screen
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {}
}
