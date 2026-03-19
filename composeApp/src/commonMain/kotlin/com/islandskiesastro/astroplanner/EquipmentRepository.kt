package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.EquipmentConfig as EquipmentConfigRow

class EquipmentRepository(driverFactory: DatabaseDriverFactory) {
    private val db      = AstroDatabase(driverFactory.createDriver())
    private val queries = db.equipmentConfigQueries

    init {
        // Seed defaults on first run so FOV screen is immediately usable
        if (queries.selectAll().executeAsList().isEmpty()) {
            queries.insert("C8 + 0.63x Reducer + ASI294MC Pro", 2032.0, 203.2, 0.63, 4.63, 4144, 2822)
            queries.insert("RedCat 61 + ASI2600MC Duo",          360.0,  61.0,  1.0,  3.76, 6248, 4176)
        }
    }

    fun getAll(): List<EquipmentConfig> =
        queries.selectAll().executeAsList().map { it.toDomain() }

    fun insert(config: EquipmentConfig) {
        queries.insert(
            config.name, config.focalLength, config.aperture,
            config.focalReducerFactor, config.pixelSize,
            config.resolutionWidth.toLong(), config.resolutionHeight.toLong()
        )
    }

    fun update(config: EquipmentConfig) {
        queries.update(
            config.name, config.focalLength, config.aperture,
            config.focalReducerFactor, config.pixelSize,
            config.resolutionWidth.toLong(), config.resolutionHeight.toLong(),
            config.id
        )
    }

    fun delete(id: Long) {
        queries.deleteById(id)
    }

    private fun EquipmentConfigRow.toDomain() = EquipmentConfig(
        id                 = id,
        name               = name,
        focalLength        = focalLength,
        aperture           = aperture,
        focalReducerFactor = focalReducerFactor,
        pixelSize          = pixelSize,
        resolutionWidth    = resolutionWidth.toInt(),
        resolutionHeight   = resolutionHeight.toInt()
    )
}
