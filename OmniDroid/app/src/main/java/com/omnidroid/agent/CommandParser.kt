package com.omnidroid.agent

class CommandParser {
    fun parse(raw: String): AgentCommand {
        val text = raw.trim()
        val normalized = text.lowercase()

        if (normalized in setOf("settings", "open settings", "go to settings", "launch settings")) {
            return AgentCommand.OpenSettings
        }
        if (normalized in setOf("list apps", "show apps", "what apps are installed", "show installed apps")) {
            return AgentCommand.ListApps
        }

        val launchPrefixes = listOf("open ", "launch ", "start ")
        val prefix = launchPrefixes.firstOrNull { normalized.startsWith(it) }
        if (prefix != null) {
            val name = text.drop(prefix.length).trim()
            if (name.isNotBlank()) return AgentCommand.LaunchApp(name)
        }
        return AgentCommand.Unknown(text)
    }
}
