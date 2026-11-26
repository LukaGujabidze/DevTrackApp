package com.omnidroid.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AndroidToolRegistry(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun launchApp(requestedName: String): AgentResult {
        val apps = launchableApps()
        val match = apps.firstOrNull { it.label.equals(requestedName, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(requestedName, ignoreCase = true) }

        if (match == null) {
            val suggestions = apps.take(12).joinToString { it.label }
            return AgentResult(false, "I couldn't find “$requestedName”. Some visible apps: $suggestions")
        }

        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(match.packageName)
                ?: return AgentResult(false, "${match.label} has no launchable activity.")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            AgentResult(true, "Launch request sent for ${match.label}. Android accepted the launch intent.", RiskLevel.LOW)
        } catch (e: Exception) {
            AgentResult(false, "Couldn't launch ${match.label}: ${e.javaClass.simpleName}.")
        }
    }

    fun openSettings(): AgentResult {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            AgentResult(true, "Opened Android Settings.", RiskLevel.LOW)
        } catch (e: Exception) {
            AgentResult(false, "Android refused to open Settings: ${e.javaClass.simpleName}.")
        }
    }

    fun listApps(): AgentResult {
        val names = launchableApps().map { it.label }.sorted()
        if (names.isEmpty()) return AgentResult(false, "No launchable apps were visible to this app.")
        return AgentResult(true, "Launchable apps (${names.size}):\n" + names.joinToString("\n"))
    }

    private data class AppEntry(val label: String, val packageName: String)

    private fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0).map {
            AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName)
        }.distinctBy { it.packageName }
    }
}
