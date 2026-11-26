package com.omnidroid.agent

/**
 * The policy layer is deliberately separate from language parsing.
 * Future LLM output must pass through this layer before any Android tool runs.
 */
class PolicyEngine {
    fun isAllowedWithoutConfirmation(risk: RiskLevel): Boolean =
        risk == RiskLevel.READ_ONLY || risk == RiskLevel.LOW
}
