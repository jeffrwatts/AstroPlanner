package com.islandskiesastro.astroplanner

enum class ObjectType { STAR, GALAXY, NEBULA, CLUSTER, PLANET, VARIABLE_STAR, UNKNOWN }

data class CelestialObject(
    val id: Long,
    val displayName: String,
    val objectId: String,
    val ra: Double,
    val dec: Double,
    val type: ObjectType,
    val subType: String?,
    val constellation: String?,
    val recommended: Boolean,
    val magnitude: Double?,
    val angularSizeMajor: Double?,
    val angularSizeMinor: Double?,
    val userAdded: Boolean = false,
    val variablePeriodDays: Double? = null,
    val variableEpochJd: Double? = null,
    val variableEpochType: String? = null
)
