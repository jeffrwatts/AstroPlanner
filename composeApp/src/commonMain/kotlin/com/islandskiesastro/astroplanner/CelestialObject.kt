package com.islandskiesastro.astroplanner

enum class ObjectType { STAR, GALAXY, NEBULA, CLUSTER, PLANET, VARIABLE_STAR, STANDARD_FIELD, UNKNOWN }

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

data class ComparisonStar(
    val id: Long,
    val variableStarObjectId: String,
    val auid: String,
    val ra: Double,
    val dec: Double,
    val label: String,
    val mag: Double?,
    val bands: Map<String, Double>? = null
)
