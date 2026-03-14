package com.islandskiesastro.astroplanner

enum class ObjectType { STAR, GALAXY, NEBULA, CLUSTER, PLANET, UNKNOWN }

data class CelestialObject(
    val id: Long,
    val displayName: String,
    val objectId: String,
    val ra: Double,
    val dec: Double,
    val type: ObjectType,
    val subType: String?,
    val constellation: String?,
    val recommended: Boolean
)
