package com.example.linkguard

enum class VirusTotalRisk(val displayName: String, val badgeColorHex: String, val iconEmoji: String) {
    MALICIOUS("MALICIOUS", "#EF4444", "🔴"),
    SUSPICIOUS("SUSPICIOUS", "#F59E0B", "🟡"),
    CLEAN("CLEAN / SAFE", "#10B981", "🟢"),
    UNRATED("UNRATED / NOT FOUND", "#64748B", "⚪"),
    ERROR("CHECK FAILED", "#94A3B8", "⚠️")
}

data class VirusTotalResult(
    val url: String,
    val risk: VirusTotalRisk,
    val maliciousCount: Int = 0,
    val suspiciousCount: Int = 0,
    val harmlessCount: Int = 0,
    val undetectedCount: Int = 0,
    val totalEngines: Int = 0,
    val message: String = "",
    val virusTotalWebUrl: String = "",
    val riskPercentage: Int = 0
)
