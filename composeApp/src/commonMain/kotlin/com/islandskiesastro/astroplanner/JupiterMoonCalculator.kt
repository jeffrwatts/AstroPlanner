package com.islandskiesastro.astroplanner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class JupiterMoonPosition(
    val name: String,
    val xJR: Double,   // East-West offset in Jupiter radii (+ = West = right with N-up, sky-chart convention)
    val yJR: Double    // North-South offset (+ = North)
)

data class JupiterSystemState(
    val moons: List<JupiterMoonPosition>,
    val grsVisible: Boolean,
    val grsCmDiff: Double   // GRS CM longitude minus CM II, degrees (±90 = visible)
)

object JupiterMoonCalculator {

    private fun norm360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    private fun normPM180(deg: Double): Double {
        var d = norm360(deg)
        if (d > 180.0) d -= 360.0
        return d
    }

    private fun toRad(deg: Double): Double = deg * PI / 180.0

    /**
     * Compute the Galilean moon positions and GRS visibility for the given time.
     *
     * @param d Days from J2000.0 (use AstronomyService.daysFromJ2000())
     */
    fun calculate(d: Double): JupiterSystemState {
        // Days from JDE 2443000.5 (the epoch used by Meeus Ch.44 low-accuracy method)
        val t = d + 8544.5

        // Mean longitudes of the four Galilean moons (degrees)
        val l1 = norm360(106.07719 + 203.48895634 * t)   // Io
        val l2 = norm360(175.73161 + 101.37472940 * t)   // Europa
        val l3 = norm360(120.55883 +  50.31760920 * t)   // Ganymede
        val l4 = norm360( 84.44459 +  21.57107117 * t)   // Callisto

        // Accurate geocentric reference direction: the ecliptic longitude of Jupiter
        // as seen from Earth, computed from full orbital mechanics (equation of centre
        // + Saturn perturbation + Earth's actual position). Adding 180° converts from
        // "Earth→Jupiter" to "Jupiter→Earth", which is the projection axis needed to
        // determine which side of Jupiter each moon falls on as seen from Earth.
        val jupGeoLon = AstronomyService.jupiterGeocentricEclipticLon(d)
        val v = norm360(jupGeoLon + 180.0)

        // Semi-major axes in Jupiter equatorial radii (Meeus Table 44.a)
        val a1 = 5.9026
        val a2 = 9.3972
        val a3 = 14.9904
        val a4 = 26.3657

        val moons = listOf(
            JupiterMoonPosition("Io",       a1 * sin(toRad(l1 - v)), 0.0),
            JupiterMoonPosition("Eu",       a2 * sin(toRad(l2 - v)), 0.0),
            JupiterMoonPosition("Ga",       a3 * sin(toRad(l3 - v)), 0.0),
            JupiterMoonPosition("Ca",       a4 * sin(toRad(l4 - v)), 0.0)
        )

        // Great Red Spot visibility
        //
        // CML System II — Project Pluto formula (matches Sky & Telescope predictions).
        // The IAU W angle (43.3 + 870.270*d) is the prime-meridian orientation, NOT the
        // observer's Central Meridian Longitude. The correct formula uses:
        //   • Rate 870.1869147°/day (empirically fitted to observed transits)
        //   • Julian Date as the time argument
        //   • A multi-term correction for Jupiter's orbital eccentricity and synodic cycle
        val jd = d + 2451545.0
        val jupMean  = norm360((jd - 2455636.938) * 360.0 / 4332.89709)
        val eqnCtr   = 5.55  * sin(toRad(jupMean))
        val angle    = norm360((jd - 2451870.628) * 360.0 / 398.884 - eqnCtr)
        val correction = 11.0 * sin(toRad(angle)) + 5.0 * cos(toRad(angle)) -
                          1.25 * cos(toRad(jupMean)) - eqnCtr
        val cmII = norm360(181.62 + 870.1869147 * jd + correction)

        // GRS System II longitude.
        // Sky & Telescope reports 85° on 2026-Feb-01, drifting +1.75°/month.
        // Reference epoch d0 = 9527.5 (2026-Feb-01 00:00 UTC from J2000.0).
        val grsLon   = norm360(85.0 + (1.75 / 30.0) * (d - 9527.5))
        val grsCmDiff = normPM180(grsLon - cmII)
        val grsVisible = abs(grsCmDiff) < 90.0

        return JupiterSystemState(moons, grsVisible, grsCmDiff)
    }
}
