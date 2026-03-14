package com.islandskiesastro.astroplanner

data class CelestialObjectImage(
    val objectId: String,
    val url: String,
    val fullPath: String?,
    val thumbPath: String?,
    val thumbX: Int?,
    val thumbY: Int?,
    val thumbDim: Int?
)
