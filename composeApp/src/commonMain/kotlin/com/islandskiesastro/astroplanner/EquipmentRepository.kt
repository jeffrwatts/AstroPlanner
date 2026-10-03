package com.islandskiesastro.astroplanner

import com.islandskiesastro.astroplanner.database.AstroDatabase
import com.islandskiesastro.astroplanner.database.EquipmentConfig as EquipmentConfigRow

class EquipmentRepository(driverFactory: DatabaseDriverFactory) {
    private val db      = AstroDatabase(driverFactory.createDriver())
    private val queries = db.equipmentConfigQueries

    init {
        // Seed defaults on first run so the FOV and planner screens are immediately usable
        if (queries.selectAll().executeAsList().isEmpty()) {
            defaultConfigs().forEach { insert(it) }
        }
    }

    private fun defaultConfigs(): List<EquipmentConfig> {
        fun ota(name: String) = EquipmentCatalog.otas.first { it.name == name }
        fun camera(name: String) = EquipmentCatalog.cameras.first { it.name == name }
        fun config(name: String, ota: OtaSpec, camera: CameraSpec, reducer: Double = 1.0) = EquipmentConfig(
            name               = name,
            otaName            = ota.name,
            cameraName         = camera.name,
            focalLength        = ota.focalLength,
            aperture           = ota.aperture,
            focalReducerFactor = reducer,
            pixelSize          = camera.pixelSize,
            resolutionWidth    = camera.resolutionWidth,
            resolutionHeight   = camera.resolutionHeight,
            filtersSupported   = ota.filtersSupported
        )
        val s30Pro = ota("SeeStar S30 Pro")
        return listOf(
            config("C8 + 0.63x Reducer + ASI294MC Pro", ota("Celestron C8"), camera("ZWO ASI 294MC Pro"), reducer = 0.63),
            config("RedCat 61 + ASI2600MC Duo", ota("William Optics RedCat 61"), camera("ZWO ASI 2600MC Duo")),
            config("SeeStar S30 Pro", s30Pro, s30Pro.builtInCamera!!)
        )
    }

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
