package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.ObservingPlan as ObservingPlanRow

class PlanRepository(driverFactory: DatabaseDriverFactory) {
    private val db        = AstroDatabase(driverFactory.createDriver())
    private val planQ     = db.observingPlanQueries
    private val settingsQ = db.appSettingsQueries

    init {
        // Seed the API key from the build-time constant (local.properties) if the
        // DB doesn't already have one.  This lets devs keep their key out of source
        // control while avoiding the Settings-screen dance on every fresh install.
        if (getApiKey().isBlank() && ANTHROPIC_API_KEY_BUILD.isNotBlank()) {
            setApiKey(ANTHROPIC_API_KEY_BUILD)
        }
        // Seed a default filter inventory for new installs.
        if (getFilters().isBlank()) {
            setFilters("Optolong L-eXtreme\nOptolong UV/IR Cut")
        }
    }

    fun getAll(): List<ObservingPlan> =
        planQ.selectAll().executeAsList().map { it.toDomain() }

    fun insert(plan: ObservingPlan) {
        planQ.insert(
            createdAtMs       = plan.createdAtMs,
            targetObjectId    = plan.targetObjectId,
            targetDisplayName = plan.targetDisplayName,
            equipmentConfigId = plan.equipmentConfigId,
            equipmentSummary  = plan.equipmentSummary,
            locationName      = plan.locationName,
            locationAltitudeM = plan.locationAltitudeM,
            bortleScale       = plan.bortleScale.toLong(),
            observingGoal     = plan.observingGoal,
            availableMinutes  = plan.availableMinutes.toLong(),
            planMarkdown      = plan.planMarkdown
        )
    }

    fun delete(id: Long) {
        planQ.deleteById(id)
    }

    fun getApiKey(): String =
        settingsQ.getSettings().executeAsOneOrNull()?.anthropicApiKey ?: ""

    fun setApiKey(key: String) {
        val current = settingsQ.getSettings().executeAsOneOrNull()
        settingsQ.upsertSettings(key, current?.filterInventory ?: "")
    }

    fun getFilters(): String =
        settingsQ.getSettings().executeAsOneOrNull()?.filterInventory ?: ""

    fun setFilters(filters: String) {
        val current = settingsQ.getSettings().executeAsOneOrNull()
        settingsQ.upsertSettings(current?.anthropicApiKey ?: "", filters)
    }

    private fun ObservingPlanRow.toDomain() = ObservingPlan(
        id                = id,
        createdAtMs       = createdAtMs,
        targetObjectId    = targetObjectId,
        targetDisplayName = targetDisplayName,
        equipmentConfigId = equipmentConfigId,
        equipmentSummary  = equipmentSummary,
        locationName      = locationName,
        locationAltitudeM = locationAltitudeM,
        bortleScale       = bortleScale.toInt(),
        observingGoal     = observingGoal,
        availableMinutes  = availableMinutes.toInt(),
        planMarkdown      = planMarkdown
    )
}
