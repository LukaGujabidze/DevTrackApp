package com.omnidroid.agent

class AgentOrchestrator(
    private val parser: CommandParser,
    private val tools: AndroidToolRegistry,
    private val policy: PolicyEngine = PolicyEngine()
) {
    fun handle(input: String): AgentResult {
        val command = parser.parse(input)
        val result = when (command) {
            is AgentCommand.OpenSettings -> tools.openSettings()
            is AgentCommand.ListApps -> tools.listApps()
            is AgentCommand.LaunchApp -> tools.launchApp(command.appName)
            is AgentCommand.Unknown -> AgentResult(
                false,
                "I don't have a tool for that yet. This MVP supports opening apps, opening Settings, and listing launchable apps."
            )
        }

        if (!policy.isAllowedWithoutConfirmation(result.risk)) {
            return AgentResult(false, "This action requires an explicit confirmation workflow.", result.risk)
        }
        return result
    }
}
