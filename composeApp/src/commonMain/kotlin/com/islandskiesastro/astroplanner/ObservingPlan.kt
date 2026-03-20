package com.islandskiesastro.astroplanner

data class ObservingPlan(
    val id: Long = 0L,
    val createdAtMs: Long,
    val targetObjectId: String,
    val targetDisplayName: String,
    val equipmentConfigId: Long,
    val equipmentSummary: String,
    val locationName: String,
    val locationAltitudeM: Double,
    val bortleScale: Int,
    val observingGoal: String,
    val availableMinutes: Int,
    val planMarkdown: String
)
