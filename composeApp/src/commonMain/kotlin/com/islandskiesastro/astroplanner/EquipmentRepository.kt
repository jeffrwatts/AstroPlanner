package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.EquipmentConfig as EquipmentConfigRow

class EquipmentRepository(driverFactory: DatabaseDriverFactory) {
    private val db      = AstroDatabase(driverFactory.createDriver())
    private val queries = db.equipmentConfigQueries

    fun getAll(): List<EquipmentConfig> =
        queries.selectAll().executeAsList().map { it.toDomain() }

    fun insert(config: EquipmentConfig) {
        queries.insert(
            config.name, config.otaName, config.cameraName,
            config.focalLength, config.aperture,
            config.focalReducerFactor, config.pixelSize,
            config.resolutionWidth.toLong(), config.resolutionHeight.toLong(),
            if (config.filtersSupported) 1L else 0L
        )
    }

    fun update(config: EquipmentConfig) {
        queries.update(
            config.name, config.otaName, config.cameraName,
            config.focalLength, config.aperture,
            config.focalReducerFactor, config.pixelSize,
            config.resolutionWidth.toLong(), config.resolutionHeight.toLong(),
            if (config.filtersSupported) 1L else 0L,
            config.id
        )
    }

    fun delete(id: Long) {
        queries.deleteById(id)
    }

    private fun EquipmentConfigRow.toDomain() = EquipmentConfig(
        id                 = id,
        name               = name,
        otaName            = otaName,
        cameraName         = cameraName,
        focalLength        = focalLength,
        aperture           = aperture,
        focalReducerFactor = focalReducerFactor,
        pixelSize          = pixelSize,
        resolutionWidth    = resolutionWidth.toInt(),
        resolutionHeight   = resolutionHeight.toInt(),
        filtersSupported   = filtersSupported != 0L
    )
}
