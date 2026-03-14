package com.islandskiesastro.astroplanner

/**
 * Platform-specific Moon RA/Dec lookup.
 *
 * Android uses the cosinekitty astronomy library for an accurate result.
 * iOS returns (0.0, 0.0) as a first-pass stub until a pure-Kotlin Moon
 * algorithm is implemented in commonMain.
 *
 * @return Pair(ra, dec) in degrees (same convention as [AstronomyService.getPlanetRaDec]).
 */
expect fun moonRaDec(): Pair<Double, Double>
