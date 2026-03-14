package com.islandskiesastro.astroplanner

import kotlinx.datetime.Clock

actual fun moonRaDec(): Pair<Double, Double> {
    val ms = Clock.System.now().toEpochMilliseconds()
    val d = ms / 86_400_000.0 + 2_440_587.5 - 2_451_545.0
    return AstronomyService.moonRaDecForD(d)
}
