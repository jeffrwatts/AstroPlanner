package com.islandskiesastro.astroplanner

import kotlin.math.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class AltAzmData(val altitude: Double, val azimuth: Double)

object AstronomyService {

    // ── Angle helpers ─────────────────────────────────────────────────────────

    private fun Double.toRad() = this * PI / 180.0
    private fun Double.toDeg() = this * 180.0 / PI
    private fun norm360(deg: Double) = ((deg % 360.0) + 360.0) % 360.0

    // ── Time helpers ──────────────────────────────────────────────────────────

    private fun jdFromMs(ms: Long): Double = ms / 86_400_000.0 + 2_440_587.5

    private fun currentJd(): Double = jdFromMs(Clock.System.now().toEpochMilliseconds())

    private fun daysFromJ2000(): Double = currentJd() - 2_451_545.0

    // ── GMST (degrees, IAU formula) ───────────────────────────────────────────

    private fun gmstDeg(jd: Double): Double {
        val d = jd - 2_451_545.0
        val T = d / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * d + 0.000387933 * T * T - T * T * T / 38_710_000.0
        return norm360(gmst)
    }

    // ── Alt/Az ────────────────────────────────────────────────────────────────

    fun getAltAzm(ra: Double, dec: Double, lat: Double, lon: Double, altMeters: Double): AltAzmData {
        val jd = currentJd()
        val gmst = gmstDeg(jd)

        val lstDeg = norm360(gmst + lon)
        val haDeg = norm360(lstDeg - ra)

        val haR = haDeg.toRad()
        val decR = dec.toRad()
        val latR = lat.toRad()

        val sinAlt = sin(decR) * sin(latR) + cos(decR) * cos(latR) * cos(haR)
        val altGeom = asin(sinAlt.coerceIn(-1.0, 1.0)).toDeg()
        val altGeomR = altGeom.toRad()

        val cosAlt = cos(altGeomR)
        val az: Double = if (cosAlt < 1e-10) {
            0.0
        } else {
            val cosAz = (sin(decR) - sin(altGeomR) * sin(latR)) / (cosAlt * cos(latR))
            val azRaw = acos(cosAz.coerceIn(-1.0, 1.0)).toDeg()
            if (sin(haR) >= 0.0) 360.0 - azRaw else azRaw
        }

        // Bennett's atmospheric refraction (alt > -2°)
        val apparentAlt = if (altGeom > -2.0) {
            val r = 1.02 / tan((altGeom + 10.3 / (altGeom + 5.11)).toRad())
            altGeom + r / 60.0
        } else {
            altGeom
        }

        return AltAzmData(apparentAlt, az)
    }

    // ── Meridian Transit ──────────────────────────────────────────────────────

    fun getMeridianTransit(ra: Double, lon: Double, lat: Double): String {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val jd = jdFromMs(nowMs)
        val gmst = gmstDeg(jd)

        val lstDeg = norm360(gmst + lon)
        var haDeg = norm360(lstDeg - ra)
        if (haDeg > 180.0) haDeg -= 360.0  // HA in [-180, +180]

        // Sidereal degrees until HA = 0 (transit)
        val siderealDegUntilTransit = if (haDeg <= 0.0) -haDeg else 360.0 - haDeg

        // Convert sidereal degrees to solar milliseconds (1 sidereal day = 23.9344696 solar hours)
        val solarMsUntilTransit = ((siderealDegUntilTransit / 360.0) * 23.9344696 * 3_600_000.0).toLong()

        val transitInstant = Instant.fromEpochMilliseconds(nowMs + solarMsUntilTransit)
        val ldt = transitInstant.toLocalDateTime(TimeZone.currentSystemDefault())

        val hour = ldt.hour
        val minute = ldt.minute
        val ampm = if (hour < 12) "am" else "pm"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "${hour12}:${minute.toString().padStart(2, '0')} $ampm"
    }

    // ── Planet RA/Dec (Schlyter orbital elements + perturbations) ────────────

    fun getPlanetRaDec(planetObjectId: String): Pair<Double, Double> =
        getPlanetRaDecForD(planetObjectId, daysFromJ2000())

    internal fun getPlanetRaDecForD(planetObjectId: String, d: Double): Pair<Double, Double> {
        val earth = earthHeliocentric(d)

        // Mean anomalies needed for perturbation corrections
        val Mj = norm360(19.8950 + 0.0830853001 * d)
        val Ms = norm360(316.9670 + 0.0334442282 * d)
        val Mu = norm360(142.5905 + 0.011725806  * d)

        val (xh, yh, zh) = when (planetObjectId.lowercase()) {
            "mercury" -> mercury(d)
            "venus"   -> venus(d)
            "mars"    -> mars(d)
            "jupiter" -> perturbXYZ(jupiter(d), jupiterLonPertDeg(Mj, Ms))
            "saturn"  -> { val (dl, dr) = saturnPert(Mj, Ms); perturbXYZ(saturn(d), dl, dr) }
            "uranus"  -> perturbXYZ(uranus(d), uranusPertDeg(Mj, Ms, Mu))
            "neptune" -> neptune(d)
            else      -> return Pair(0.0, 0.0)
        }

        // Geocentric ecliptic: earthHeliocentric returns Sun's geocentric position (xs,ys),
        // so geocentric = planet_helio + sun_geocentric (= planet_helio - earth_helio)
        val xg = xh + earth.first
        val yg = yh + earth.second
        val zg = zh + earth.third

        // Obliquity of the ecliptic
        val eps = (23.4393 - 3.563e-7 * d).toRad()

        // Equatorial coordinates
        val xeq = xg
        val yeq = yg * cos(eps) - zg * sin(eps)
        val zeq = yg * sin(eps) + zg * cos(eps)

        val ra = norm360(atan2(yeq, xeq).toDeg())
        val dec = atan2(zeq, sqrt(xeq * xeq + yeq * yeq)).toDeg()

        return Pair(ra, dec)
    }

    // ── Kepler solver (Newton-Raphson) ────────────────────────────────────────

    private fun solveKepler(mDeg: Double, e: Double): Double {
        val mRad = mDeg.toRad()
        var E = (mDeg + e.toDeg() * sin(mRad) * (1.0 + e * cos(mRad))).toRad()
        repeat(10) {
            val dE = (E - e * sin(E) - mRad) / (1.0 - e * cos(E))
            E -= dE
            if (abs(dE) < 1e-6) return@repeat
        }
        return E
    }

    // ── Heliocentric rectangular XYZ ──────────────────────────────────────────

    private fun heliocentricXYZ(
        N: Double, i: Double, w: Double,
        a: Double, e: Double, mDeg: Double
    ): Triple<Double, Double, Double> {
        val E = solveKepler(mDeg, e)
        val xv = a * (cos(E) - e)
        val yv = a * sqrt(1.0 - e * e) * sin(E)
        val v = atan2(yv, xv).toDeg()
        val r = sqrt(xv * xv + yv * yv)

        val nR = N.toRad()
        val iR = i.toRad()
        val vwR = (v + w).toRad()

        val xh = r * (cos(nR) * cos(vwR) - sin(nR) * sin(vwR) * cos(iR))
        val yh = r * (sin(nR) * cos(vwR) + cos(nR) * sin(vwR) * cos(iR))
        val zh = r * sin(vwR) * sin(iR)

        return Triple(xh, yh, zh)
    }

    // ── Earth ─────────────────────────────────────────────────────────────────

    private fun earthHeliocentric(d: Double) = heliocentricXYZ(
        N = 0.0,
        i = 0.0,
        w = norm360(282.9404 + 4.70935e-5 * d),
        a = 1.000000,
        e = 0.016709 - 1.151e-9 * d,
        mDeg = norm360(356.0470 + 0.9856002585 * d)
    )

    // ── Planets (Schlyter orbital elements) ───────────────────────────────────

    private fun mercury(d: Double) = heliocentricXYZ(
        N = norm360(48.3313 + 3.24587e-5 * d),
        i = 7.0047 + 5.00e-8 * d,
        w = norm360(29.1241 + 1.01444e-5 * d),
        a = 0.387098,
        e = 0.205635 + 5.59e-10 * d,
        mDeg = norm360(168.6562 + 4.0923344368 * d)
    )

    private fun venus(d: Double) = heliocentricXYZ(
        N = norm360(76.6799 + 2.46590e-5 * d),
        i = 3.3946 + 2.75e-8 * d,
        w = norm360(54.8910 + 1.38374e-5 * d),
        a = 0.723330,
        e = 0.006773 - 1.302e-9 * d,
        mDeg = norm360(48.0052 + 1.6021302244 * d)
    )

    private fun mars(d: Double) = heliocentricXYZ(
        N = norm360(49.5574 + 2.11081e-5 * d),
        i = 1.8497 - 1.78e-8 * d,
        w = norm360(286.5016 + 2.92961e-5 * d),
        a = 1.523688,
        e = 0.093405 + 2.516e-9 * d,
        mDeg = norm360(18.6021 + 0.5240207766 * d)
    )

    private fun jupiter(d: Double) = heliocentricXYZ(
        N = norm360(100.4542 + 2.76854e-5 * d),
        i = 1.3030 - 1.557e-7 * d,
        w = norm360(273.8777 + 1.64505e-5 * d),
        a = 5.20256,
        e = 0.048498 + 4.469e-9 * d,
        mDeg = norm360(19.8950 + 0.0830853001 * d)
    )

    private fun saturn(d: Double) = heliocentricXYZ(
        N = norm360(113.6634 + 2.38980e-5 * d),
        i = 2.4886 - 1.081e-7 * d,
        w = norm360(339.3939 + 2.97661e-5 * d),
        a = 9.55475,
        e = 0.055546 - 9.499e-9 * d,
        mDeg = norm360(316.9670 + 0.0334442282 * d)
    )

    private fun uranus(d: Double) = heliocentricXYZ(
        N = norm360(74.0005 + 1.3978e-5 * d),
        i = 0.7733 + 1.9e-8 * d,
        w = norm360(96.6612 + 3.0565e-5 * d),
        a = 19.18171 - 1.55e-8 * d,
        e = 0.047318 + 7.45e-9 * d,
        mDeg = norm360(142.5905 + 0.011725806 * d)
    )

    private fun neptune(d: Double) = heliocentricXYZ(
        N = norm360(131.7806 + 3.0173e-5 * d),
        i = 1.7700 - 2.55e-7 * d,
        w = norm360(272.8461 - 6.027e-6 * d),
        a = 30.05826 + 3.313e-8 * d,
        e = 0.008606 + 2.15e-9 * d,
        mDeg = norm360(260.2471 + 0.005995147 * d)
    )

    // ── Perturbation corrections (Schlyter) ───────────────────────────────────
    // lonDeg is in degrees of ecliptic longitude; dr in AU.

    private fun perturbXYZ(
        xyz: Triple<Double, Double, Double>,
        lonDeg: Double,
        dr: Double = 0.0
    ): Triple<Double, Double, Double> {
        val (xh, yh, zh) = xyz
        val r    = sqrt(xh * xh + yh * yh + zh * zh)
        val rXY  = sqrt(xh * xh + yh * yh)
        val lon  = atan2(yh, xh)
        val lat  = atan2(zh, rXY)
        val rNew = r + dr
        val lonNew = lon + lonDeg.toRad()
        return Triple(
            rNew * cos(lat) * cos(lonNew),
            rNew * cos(lat) * sin(lonNew),
            rNew * sin(lat)
        )
    }

    // Jupiter: perturbations due to Saturn (~1° max)
    private fun jupiterLonPertDeg(Mj: Double, Ms: Double): Double {
        return (-0.332 * sin((2 * Mj - 5 * Ms - 67.6).toRad())
              - 0.056 * sin((2 * Mj - 2 * Ms + 21.0).toRad())
              + 0.042 * sin((3 * Mj - 5 * Ms + 20.9).toRad())
              - 0.036 * sin((    Mj - 2 * Ms        ).toRad())
              + 0.022 * cos((    Mj -     Ms        ).toRad())
              + 0.023 * sin((2 * Mj - 3 * Ms + 52.7).toRad())
              - 0.016 * sin((    Mj - 5 * Ms - 69.0).toRad()))
    }

    // Saturn: perturbations due to Jupiter (~3° max)
    private fun saturnPert(Mj: Double, Ms: Double): Pair<Double, Double> {
        val dlon = (+0.812 * sin((2 * Mj - 5 * Ms - 67.6).toRad())
                  - 0.229 * cos((2 * Mj - 4 * Ms -  2.0).toRad())
                  + 0.119 * sin((    Mj - 2 * Ms -  3.0).toRad())
                  + 0.046 * sin((2 * Mj - 6 * Ms - 69.0).toRad())
                  + 0.014 * sin((    Mj - 3 * Ms + 32.0).toRad()))
        val dr   = (-0.020 * cos((2 * Mj - 4 * Ms -  2.0).toRad())
                  + 0.018 * sin((2 * Mj - 6 * Ms - 49.5).toRad()))
        return Pair(dlon, dr)
    }

    // Uranus: perturbations due to Jupiter and Saturn (~0.1° max)
    private fun uranusPertDeg(Mj: Double, Ms: Double, Mu: Double): Double {
        return (+0.040 * sin((Ms - 2 * Mu +  6.0).toRad())
              + 0.035 * sin((Ms - 3 * Mu + 33.0).toRad())
              - 0.015 * sin((Mj -     Mu + 20.0).toRad()))
    }
}
