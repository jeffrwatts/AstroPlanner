package com.islandskiesastro.astroplanner

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import org.junit.Test

/**
 * Prints cosinekitty reference RA/Dec values for two fixed epochs.
 * Run with: ./gradlew :composeApp:testDebugUnitTest --tests "*.PlanetRaDecReferenceGenerator"
 * Capture stdout to populate AstronomyServiceTest in commonTest.
 */
class PlanetRaDecReferenceGenerator {

    // 2026-03-14 00:00:00 UTC
    private val epoch1Ms = 1741910400000L
    // 2025-07-04 12:00:00 UTC  (different season, different planet geometry)
    private val epoch2Ms = 1751630400000L

    private val planets = listOf(
        "moon"    to Body.Moon,
        "mercury" to Body.Mercury,
        "venus"   to Body.Venus,
        "mars"    to Body.Mars,
        "jupiter" to Body.Jupiter,
        "saturn"  to Body.Saturn,
        "uranus"  to Body.Uranus,
        "neptune" to Body.Neptune,
    )

    @Test
    fun generateReferenceValues() {
        for ((label, epochMs) in listOf("epoch1" to epoch1Ms, "epoch2" to epoch2Ms)) {
            val d = epochMs / 86_400_000.0 + 2_440_587.5 - 2_451_545.0
            println("\n// $label  ms=$epochMs  d=${"%.4f".format(d)}")

            val time = Time.fromMillisecondsSince1970(epochMs)
            val observer = Observer(0.0, 0.0, 0.0)

            for ((id, body) in planets) {
                val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
                val ra  = eq.ra * 15.0   // hours → degrees (matches AstronomyService convention)
                val dec = eq.dec
                // Also run Schlyter/commonMain to see the diff
                val (sRa, sDec) = if (id == "moon") AstronomyService.moonRaDecForD(d)
                                  else AstronomyService.getPlanetRaDecForD(id, d)
                val dRa  = ra  - sRa
                val dDec = dec - sDec
                println(
                    "  %-8s  ref ra=%8.3f dec=%7.3f   schlyter ra=%8.3f dec=%7.3f   Δra=%6.3f Δdec=%6.3f"
                        .format(id, ra, dec, sRa, sDec, dRa, dDec)
                )
            }
        }
    }
}
