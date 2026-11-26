package com.omnidroid.agent

enum class RiskLevel { READ_ONLY, LOW, SENSITIVE, DESTRUCTIVE }

sealed interface AgentCommand {
    data class LaunchApp(val appName: String) : AgentCommand
    data object OpenSettings : AgentCommand
    data object ListApps : AgentCommand
    data class Unknown(val original: String) : AgentCommand
}

data class AgentResult(
    val success: Boolean,
    val message: String,
    val risk: RiskLevel = RiskLevel.READ_ONLY
)

interface AndroidTool {
    val name: String
    val risk: RiskLevel
    fun execute(): AgentResult
}
