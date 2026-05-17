package com.islandskiesastro.astroplanner

enum class FilterBand(val label: String) {
    Ha("Hα 656nm"),
    OIII("OIII 500nm"),
    SII("SII 672nm"),
    Hbeta("Hβ 486nm"),
    Luminance("Luminance"),
    RGB("RGB"),
    LightPollution("Light Pollution Reduction"),
    UVIRCut("UV/IR Cut")
}

data class FilterSpec(
    val name: String,
    val passbands: List<FilterBand>,
    val bandwidthNm: Double? = null
) {
    val passbandSummary: String get() {
        val bands = passbands.joinToString(" + ") { it.label }
        val bw = bandwidthNm?.let { " · ${it.toInt()}nm" } ?: ""
        return "$bands$bw"
    }

    val promptText: String get() {
        val bands = passbands.joinToString(" + ") { it.label }
        val bw = bandwidthNm?.let { ", ${it.toInt()}nm" } ?: ""
        return "$name ($bands$bw)"
    }
}

object FilterCatalog {
    val filters = listOf(
        FilterSpec("Optolong L-Extreme", listOf(FilterBand.Ha, FilterBand.OIII), bandwidthNm = 7.0),
        FilterSpec("Optolong L-Synergy", listOf(FilterBand.SII, FilterBand.OIII), bandwidthNm = 7.0),
        FilterSpec("Optolong UV-IR Cut", listOf(FilterBand.UVIRCut)),
    )

    fun findByName(name: String): FilterSpec? = filters.firstOrNull { it.name == name }
}
