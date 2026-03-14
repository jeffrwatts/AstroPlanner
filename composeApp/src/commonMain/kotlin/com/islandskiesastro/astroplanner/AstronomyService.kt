package com.islandskiesastro.astroplanner

import kotlin.math.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Holds the apparent altitude and azimuth of a celestial object as seen from the observer.
 *
 * @param altitude Degrees above the horizon (positive = above, negative = below). Includes
 *                 atmospheric refraction so it matches what the eye sees.
 * @param azimuth  Degrees clockwise from true North (0° = N, 90° = E, 180° = S, 270° = W).
 */
data class AltAzmData(val altitude: Double, val azimuth: Double)

/**
 * Pure-Kotlin astronomy engine. No platform dependencies — runs identically on Android and iOS.
 *
 * All angles are in **degrees** unless the variable name ends in `R` (radians).
 * All distances are in **AU** (astronomical units).
 * Time is anchored to **J2000.0** (2000-Jan-01 12:00 TT = JD 2 451 545.0).
 *
 * Algorithm sources:
 *   - Alt/Az, GMST, transit: standard spherical astronomy (e.g. Meeus "Astronomical Algorithms")
 *   - Planet positions: Paul Schlyter's orbital-element tables (stjarnhimlen.se)
 *   - Atmospheric refraction: Bennett's formula
 *   - Perturbations: Schlyter's first-order correction terms for Jupiter, Saturn, Uranus
 *
 * Accuracy: deep-sky objects (fixed RA/Dec from catalog) are limited only by the
 * alt/az math, which is exact. Planets are ~1° for inner planets and ~0.5° for
 * outer planets — sufficient for a visual sky-planner.
 */
object AstronomyService {

    // ── Angle helpers ─────────────────────────────────────────────────────────

    /** Convert degrees to radians. */
    private fun Double.toRad() = this * PI / 180.0

    /** Convert radians to degrees. */
    private fun Double.toDeg() = this * 180.0 / PI

    /**
     * Normalize an angle to [0°, 360°).
     * Handles large positive values and negative values correctly.
     */
    private fun norm360(deg: Double) = ((deg % 360.0) + 360.0) % 360.0

    // ── Time helpers ──────────────────────────────────────────────────────────

    /**
     * Convert a Unix timestamp (milliseconds since 1970-01-01 00:00 UTC) to
     * Julian Date. The constant 2 440 587.5 is the JD of the Unix epoch.
     */
    private fun jdFromMs(ms: Long): Double = ms / 86_400_000.0 + 2_440_587.5

    /** Julian Date of the current moment. */
    private fun currentJd(): Double = jdFromMs(Clock.System.now().toEpochMilliseconds())

    /**
     * Days elapsed since J2000.0 (the standard astronomical epoch).
     * Positive = after 2000-Jan-01 12:00, negative = before.
     * Used as the independent variable in all Schlyter orbital-element formulae.
     */
    private fun daysFromJ2000(): Double = currentJd() - 2_451_545.0

    // ── GMST ──────────────────────────────────────────────────────────────────

    /**
     * Greenwich Mean Sidereal Time (GMST) in degrees, using the IAU polynomial formula.
     *
     * Sidereal time is the hour angle of the vernal equinox. GMST at Greenwich
     * advances ~360.985° per solar day (one extra rotation per year compared to
     * solar time). The cubic terms correct for the slow precession of Earth's axis.
     *
     * Formula:
     *   T  = (JD − 2451545) / 36525          (Julian centuries from J2000)
     *   GMST = 280.46061837
     *          + 360.98564736629 · (JD−2451545)
     *          + 0.000387933 · T²
     *          − T³ / 38 710 000
     *
     * @param jd Julian Date of the instant of interest.
     * @return GMST in degrees, normalized to [0°, 360°).
     */
    private fun gmstDeg(jd: Double): Double {
        val d = jd - 2_451_545.0
        val T = d / 36525.0
        val gmst = 280.46061837 + 360.98564736629 * d + 0.000387933 * T * T - T * T * T / 38_710_000.0
        return norm360(gmst)
    }

    // ── Alt/Az ────────────────────────────────────────────────────────────────

    /**
     * Compute the apparent altitude and azimuth of a celestial object.
     *
     * **Pipeline:**
     * 1. Compute GMST → Local Sidereal Time (LST) by adding the observer's longitude.
     * 2. Hour Angle (HA) = LST − RA  (how far the object has moved west of the meridian).
     * 3. Spherical-trig conversion from equatorial (RA/Dec) to horizontal (Alt/Az):
     *      sin(alt) = sin(dec)·sin(lat) + cos(dec)·cos(lat)·cos(HA)
     *      cos(az)  = [sin(dec) − sin(alt)·sin(lat)] / [cos(alt)·cos(lat)]
     *    Azimuth quadrant: if sin(HA) ≥ 0 the object is moving westward → az = 360° − az_raw.
     * 4. Apply Bennett's atmospheric refraction formula to get apparent (observed) altitude.
     *
     * **Atmospheric refraction (Bennett's formula):**
     * The atmosphere bends light toward the horizon, lifting objects slightly above their
     * geometric position. The correction is largest near the horizon (~0.5° at alt=0°) and
     * negligible overhead. Bennett's formula:
     *   R = 1.02 / tan(alt + 10.3 / (alt + 5.11))   [arcminutes]
     *   apparent_alt = geometric_alt + R / 60
     * Applied only when alt > −2° (below that the geometry is too uncertain).
     *
     * @param ra        Right Ascension of the object in degrees (0–360).
     * @param dec       Declination of the object in degrees (−90 to +90).
     * @param lat       Observer's geographic latitude in degrees (+N, −S).
     * @param lon       Observer's geographic longitude in degrees (+E, −W).
     * @param altMeters Observer's elevation above sea level (not used in the trig directly,
     *                  retained for API compatibility with future dip-of-horizon corrections).
     * @return [AltAzmData] with refraction-corrected altitude and true azimuth.
     */
    fun getAltAzm(ra: Double, dec: Double, lat: Double, lon: Double, altMeters: Double): AltAzmData {
        val jd = currentJd()
        val gmst = gmstDeg(jd)

        // Local Sidereal Time: GMST shifted by observer's longitude
        val lstDeg = norm360(gmst + lon)
        // Hour Angle: how far the object has rotated west of the meridian
        val haDeg = norm360(lstDeg - ra)

        val haR  = haDeg.toRad()
        val decR = dec.toRad()
        val latR = lat.toRad()

        // Geometric altitude via the fundamental spherical-trig formula
        val sinAlt = sin(decR) * sin(latR) + cos(decR) * cos(latR) * cos(haR)
        val altGeom  = asin(sinAlt.coerceIn(-1.0, 1.0)).toDeg()
        val altGeomR = altGeom.toRad()

        // Azimuth: guard the near-zenith / near-nadir singularity where cos(alt)→0
        val cosAlt = cos(altGeomR)
        val az: Double = if (cosAlt < 1e-10) {
            0.0
        } else {
            val cosAz = (sin(decR) - sin(altGeomR) * sin(latR)) / (cosAlt * cos(latR))
            val azRaw = acos(cosAz.coerceIn(-1.0, 1.0)).toDeg()
            // sin(HA) > 0 means the object is west of the meridian → northern hemisphere az > 180°
            if (sin(haR) >= 0.0) 360.0 - azRaw else azRaw
        }

        // Bennett's atmospheric refraction formula (arcminutes → degrees)
        val apparentAlt = if (altGeom > -2.0) {
            val r = 1.02 / tan((altGeom + 10.3 / (altGeom + 5.11)).toRad())
            altGeom + r / 60.0
        } else {
            altGeom
        }

        return AltAzmData(apparentAlt, az)
    }

    // ── Meridian Transit ──────────────────────────────────────────────────────

    /**
     * Predict the next meridian transit time for a celestial object and return it
     * as a 12-hour clock string (e.g. `"9:34 pm"`).
     *
     * An object transits the meridian when its Hour Angle is exactly 0° — it is
     * due south (in the northern hemisphere) and at its highest point in the sky.
     *
     * **Pipeline:**
     * 1. Compute the current Hour Angle of the object.
     * 2. Express the remaining angle to HA=0 in sidereal degrees.
     * 3. Convert sidereal degrees → solar milliseconds:
     *      1 sidereal day = 23.9344696 solar hours (Earth's rotation period relative to stars).
     * 4. Add to the current time and format in the device's local timezone.
     *
     * If the object is already past the meridian (HA > 0), the time reported is for
     * the *next* transit, which may be tomorrow.
     *
     * @param ra  Right Ascension of the object in degrees.
     * @param lon Observer's longitude in degrees (+E, −W).
     * @param lat Observer's latitude (unused in the calculation but kept for API symmetry).
     * @return Next transit time formatted as `"h:mm am"` or `"h:mm pm"`.
     */
    fun getMeridianTransit(ra: Double, lon: Double, lat: Double): String {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val jd    = jdFromMs(nowMs)
        val gmst  = gmstDeg(jd)

        val lstDeg = norm360(gmst + lon)
        // Fold HA into [-180°, +180°]: negative = object hasn't reached meridian yet
        var haDeg = norm360(lstDeg - ra)
        if (haDeg > 180.0) haDeg -= 360.0

        // Sidereal degrees the sky must rotate until HA = 0
        val siderealDegUntilTransit = if (haDeg <= 0.0) -haDeg else 360.0 - haDeg

        // Convert to wall-clock milliseconds (sidereal day ≠ solar day)
        val solarMsUntilTransit =
            ((siderealDegUntilTransit / 360.0) * 23.9344696 * 3_600_000.0).toLong()

        val transitInstant = Instant.fromEpochMilliseconds(nowMs + solarMsUntilTransit)
        val ldt = transitInstant.toLocalDateTime(TimeZone.currentSystemDefault())

        val hour   = ldt.hour
        val minute = ldt.minute
        val ampm   = if (hour < 12) "am" else "pm"
        val hour12 = when {
            hour == 0  -> 12
            hour > 12  -> hour - 12
            else       -> hour
        }
        return "${hour12}:${minute.toString().padStart(2, '0')} $ampm"
    }

    // ── Planet RA/Dec ─────────────────────────────────────────────────────────

    /**
     * Return the current geocentric equatorial coordinates of a solar-system planet.
     *
     * Delegates to [getPlanetRaDecForD] using the current epoch.
     *
     * @param planetObjectId Case-insensitive planet name matching the database IDs:
     *   `"mercury"`, `"venus"`, `"mars"`, `"jupiter"`, `"saturn"`, `"uranus"`, `"neptune"`.
     * @return `Pair(ra, dec)` in degrees, or `(0.0, 0.0)` for an unrecognised ID.
     */
    fun getPlanetRaDec(planetObjectId: String): Pair<Double, Double> =
        getPlanetRaDecForD(planetObjectId, daysFromJ2000())

    /**
     * Compute geocentric equatorial coordinates of a planet for a specific epoch.
     *
     * Exposed as `internal` so unit tests can pass a fixed `d` value and compare
     * against reference ephemerides without mocking the clock.
     *
     * **Pipeline:**
     * 1. Compute the Sun's geocentric position `(xs, ys)` via [earthHeliocentric].
     *    (Schlyter's "Earth" elements describe the Sun's geocentric orbit, so the
     *    result equals `−Earth_heliocentric`.)
     * 2. Compute the planet's heliocentric ecliptic rectangular coordinates `(xh, yh, zh)`
     *    using [heliocentricXYZ] with Schlyter's orbital elements.
     * 3. Apply gravitational perturbation corrections for Jupiter, Saturn, and Uranus
     *    (see [perturbXYZ]).
     * 4. Convert to geocentric ecliptic:
     *      `xg = xh + xs`  (adding Sun's geocentric = subtracting Earth's heliocentric)
     * 5. Rotate by the obliquity of the ecliptic ε to get equatorial rectangular:
     *      `yeq = yg·cos(ε) − zg·sin(ε)`
     *      `zeq = yg·sin(ε) + zg·cos(ε)`
     * 6. Derive RA = atan2(yeq, xg) and Dec = atan2(zeq, √(xg²+yeq²)).
     *
     * @param planetObjectId Planet name (see [getPlanetRaDec]).
     * @param d Days from J2000.0.
     * @return `Pair(ra, dec)` in degrees.
     */
    internal fun getPlanetRaDecForD(planetObjectId: String, d: Double): Pair<Double, Double> {
        // Step 1: Sun's geocentric position (= −Earth's heliocentric position)
        val earth = earthHeliocentric(d)

        // Mean anomalies of outer planets — needed by perturbation correction functions
        val Mj = norm360(19.8950 + 0.0830853001 * d)  // Jupiter
        val Ms = norm360(316.9670 + 0.0334442282 * d) // Saturn
        val Mu = norm360(142.5905 + 0.011725806  * d) // Uranus

        // Step 2+3: heliocentric position, with perturbations where applicable
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

        // Step 4: geocentric ecliptic rectangular coordinates.
        // earthHeliocentric returns Sun's geocentric (xs, ys), so:
        //   planet_geocentric = planet_helio − earth_helio
        //                     = planet_helio − (−sun_geocentric)
        //                     = planet_helio + sun_geocentric
        val xg = xh + earth.first
        val yg = yh + earth.second
        val zg = zh + earth.third

        // Step 5: rotate ecliptic → equatorial by obliquity ε (rotation around the x-axis)
        val eps = (23.4393 - 3.563e-7 * d).toRad()
        val xeq = xg
        val yeq = yg * cos(eps) - zg * sin(eps)
        val zeq = yg * sin(eps) + zg * cos(eps)

        // Step 6: spherical coordinates from rectangular
        val ra  = norm360(atan2(yeq, xeq).toDeg())
        val dec = atan2(zeq, sqrt(xeq * xeq + yeq * yeq)).toDeg()

        return Pair(ra, dec)
    }

    // ── Kepler solver ─────────────────────────────────────────────────────────

    /**
     * Solve Kepler's equation `E − e·sin(E) = M` for the eccentric anomaly E,
     * using Newton-Raphson iteration.
     *
     * Kepler's equation relates the **mean anomaly** M (uniform angular motion from
     * perihelion, as if the orbit were circular) to the **eccentric anomaly** E
     * (the angle used to parameterise position on the actual ellipse). Once E is
     * known, the true distance and direction from the focus can be computed exactly.
     *
     * **Newton-Raphson step:**
     *   ΔE = (E − e·sin(E) − M) / (1 − e·cos(E))
     *   E  ← E − ΔE
     * Converges in ≤ 4 iterations for all solar-system planet eccentricities.
     * The loop runs up to 10 times as a safety bound.
     *
     * @param mDeg Mean anomaly in degrees.
     * @param e    Orbital eccentricity (dimensionless, 0 = circle, <1 = ellipse).
     * @return Eccentric anomaly E in **radians**.
     */
    private fun solveKepler(mDeg: Double, e: Double): Double {
        val mRad = mDeg.toRad()
        // Initial estimate: first-order expansion of Kepler's equation in e
        var E = (mDeg + e.toDeg() * sin(mRad) * (1.0 + e * cos(mRad))).toRad()
        repeat(10) {
            val dE = (E - e * sin(E) - mRad) / (1.0 - e * cos(E))
            E -= dE
            if (abs(dE) < 1e-6) return@repeat
        }
        return E
    }

    // ── Heliocentric rectangular XYZ ──────────────────────────────────────────

    /**
     * Compute the heliocentric ecliptic rectangular coordinates of a body given its
     * Keplerian orbital elements at epoch `d`.
     *
     * **Orbital elements:**
     * | Symbol | Meaning |
     * |--------|---------|
     * | N      | Longitude of the ascending node — where the orbit crosses the ecliptic plane heading north |
     * | i      | Inclination — tilt of the orbit relative to the ecliptic |
     * | w      | Argument of perihelion — angle from the ascending node to the closest approach point |
     * | a      | Semi-major axis (AU) |
     * | e      | Eccentricity |
     * | mDeg   | Mean anomaly — uniform proxy for position along the orbit |
     *
     * **Steps:**
     * 1. Solve Kepler's equation → eccentric anomaly E.
     * 2. Compute position in the orbital plane:
     *      `xv = a·(cos E − e)`,  `yv = a·√(1−e²)·sin E`
     * 3. True anomaly `v = atan2(yv, xv)`, heliocentric distance `r = √(xv²+yv²)`.
     * 4. Rotate from orbital plane to ecliptic plane using N, i, and (v+w):
     *      `xh = r·[cos N·cos(v+w) − sin N·sin(v+w)·cos i]`
     *      `yh = r·[sin N·cos(v+w) + cos N·sin(v+w)·cos i]`
     *      `zh = r·sin(v+w)·sin i`
     *
     * @return Heliocentric ecliptic rectangular coordinates in AU.
     */
    private fun heliocentricXYZ(
        N: Double, i: Double, w: Double,
        a: Double, e: Double, mDeg: Double
    ): Triple<Double, Double, Double> {
        val E  = solveKepler(mDeg, e)
        val xv = a * (cos(E) - e)
        val yv = a * sqrt(1.0 - e * e) * sin(E)
        val v  = atan2(yv, xv).toDeg()    // true anomaly
        val r  = sqrt(xv * xv + yv * yv)  // heliocentric distance

        val nR  = N.toRad()
        val iR  = i.toRad()
        val vwR = (v + w).toRad()          // argument of latitude

        val xh = r * (cos(nR) * cos(vwR) - sin(nR) * sin(vwR) * cos(iR))
        val yh = r * (sin(nR) * cos(vwR) + cos(nR) * sin(vwR) * cos(iR))
        val zh = r * sin(vwR) * sin(iR)

        return Triple(xh, yh, zh)
    }

    // ── Earth / Sun ───────────────────────────────────────────────────────────

    /**
     * Compute the Sun's geocentric ecliptic position using Earth's orbital elements.
     *
     * Schlyter's "Sun" orbital elements describe the Sun's *geocentric* orbit (i.e.,
     * as seen from Earth), so the returned vector `(xs, ys, 0)` points from Earth
     * *toward* the Sun. Equivalently, `(xs, ys) = −(Earth's heliocentric position)`.
     *
     * The orbit lies in the ecliptic plane (N = 0, i = 0), so zh = 0 always.
     *
     * Elements at J2000.0 and their drift rates (Schlyter):
     * - w  = 282.9404° + 4.70935×10⁻⁵ d  (longitude of perihelion)
     * - a  = 1.000 AU (fixed)
     * - e  = 0.016709 − 1.151×10⁻⁹ d
     * - M  = 356.0470° + 0.9856002585 d   (mean anomaly; ≈0° means near perihelion = early Jan)
     *
     * @param d Days from J2000.0.
     * @return Sun's geocentric ecliptic rectangular position in AU.
     */
    private fun earthHeliocentric(d: Double) = heliocentricXYZ(
        N    = 0.0,
        i    = 0.0,
        w    = norm360(282.9404 + 4.70935e-5 * d),
        a    = 1.000000,
        e    = 0.016709 - 1.151e-9 * d,
        mDeg = norm360(356.0470 + 0.9856002585 * d)
    )

    // ── Planet orbital elements (Schlyter) ────────────────────────────────────
    //
    // Each function encodes Schlyter's linear ephemeris:
    //   element(d) = element(J2000) + drift_rate × d
    //
    // Parameters:  N (°), i (°), w (°), a (AU), e, M (°)
    // All angular elements are normalized to [0°, 360°) before use.

    /** Mercury — innermost planet, high eccentricity (e≈0.206), 88-day orbit. */
    private fun mercury(d: Double) = heliocentricXYZ(
        N    = norm360(48.3313 + 3.24587e-5 * d),
        i    = 7.0047 + 5.00e-8 * d,
        w    = norm360(29.1241 + 1.01444e-5 * d),
        a    = 0.387098,
        e    = 0.205635 + 5.59e-10 * d,
        mDeg = norm360(168.6562 + 4.0923344368 * d)
    )

    /** Venus — brightest planet, nearly circular orbit (e≈0.007), 225-day orbit. */
    private fun venus(d: Double) = heliocentricXYZ(
        N    = norm360(76.6799 + 2.46590e-5 * d),
        i    = 3.3946 + 2.75e-8 * d,
        w    = norm360(54.8910 + 1.38374e-5 * d),
        a    = 0.723330,
        e    = 0.006773 - 1.302e-9 * d,
        mDeg = norm360(48.0052 + 1.6021302244 * d)
    )

    /** Mars — red planet, noticeable eccentricity (e≈0.093), 687-day orbit. */
    private fun mars(d: Double) = heliocentricXYZ(
        N    = norm360(49.5574 + 2.11081e-5 * d),
        i    = 1.8497 - 1.78e-8 * d,
        w    = norm360(286.5016 + 2.92961e-5 * d),
        a    = 1.523688,
        e    = 0.093405 + 2.516e-9 * d,
        mDeg = norm360(18.6021 + 0.5240207766 * d)
    )

    /** Jupiter — largest planet, 12-year orbit. Perturbed by Saturn (see [jupiterLonPertDeg]). */
    private fun jupiter(d: Double) = heliocentricXYZ(
        N    = norm360(100.4542 + 2.76854e-5 * d),
        i    = 1.3030 - 1.557e-7 * d,
        w    = norm360(273.8777 + 1.64505e-5 * d),
        a    = 5.20256,
        e    = 0.048498 + 4.469e-9 * d,
        mDeg = norm360(19.8950 + 0.0830853001 * d)
    )

    /** Saturn — ringed planet, 29-year orbit. Significantly perturbed by Jupiter (see [saturnPert]). */
    private fun saturn(d: Double) = heliocentricXYZ(
        N    = norm360(113.6634 + 2.38980e-5 * d),
        i    = 2.4886 - 1.081e-7 * d,
        w    = norm360(339.3939 + 2.97661e-5 * d),
        a    = 9.55475,
        e    = 0.055546 - 9.499e-9 * d,
        mDeg = norm360(316.9670 + 0.0334442282 * d)
    )

    /** Uranus — ice giant, 84-year orbit. Mildly perturbed by Jupiter and Saturn (see [uranusPertDeg]). */
    private fun uranus(d: Double) = heliocentricXYZ(
        N    = norm360(74.0005 + 1.3978e-5 * d),
        i    = 0.7733 + 1.9e-8 * d,
        w    = norm360(96.6612 + 3.0565e-5 * d),
        a    = 19.18171 - 1.55e-8 * d,
        e    = 0.047318 + 7.45e-9 * d,
        mDeg = norm360(142.5905 + 0.011725806 * d)
    )

    /** Neptune — outermost planet, 165-year orbit. No significant perturbations at this precision. */
    private fun neptune(d: Double) = heliocentricXYZ(
        N    = norm360(131.7806 + 3.0173e-5 * d),
        i    = 1.7700 - 2.55e-7 * d,
        w    = norm360(272.8461 - 6.027e-6 * d),
        a    = 30.05826 + 3.313e-8 * d,
        e    = 0.008606 + 2.15e-9 * d,
        mDeg = norm360(260.2471 + 0.005995147 * d)
    )

    // ── Perturbation corrections ───────────────────────────────────────────────

    /**
     * Apply first-order gravitational perturbations to a planet's heliocentric position.
     *
     * The unperturbed Keplerian orbit is an ellipse; nearby massive planets slightly
     * distort the orbit over time. Rather than solving the full n-body problem,
     * Schlyter pre-fits sinusoidal correction terms that capture the dominant effects.
     *
     * This function adjusts the **ecliptic longitude** by `lonDeg` and the
     * **heliocentric distance** by `dr` while preserving the ecliptic latitude:
     *   - Extract spherical `(r, lon, lat)` from rectangular `(xh, yh, zh)`.
     *   - Apply corrections: `r' = r + dr`, `lon' = lon + lonDeg`.
     *   - Re-encode as rectangular `(xh', yh', zh')`.
     *
     * @param xyz    Unperturbed heliocentric rectangular coordinates (AU).
     * @param lonDeg Longitude correction in degrees (from perturbation formulae).
     * @param dr     Distance correction in AU (default 0 — only Saturn has a radial term).
     * @return Corrected heliocentric rectangular coordinates.
     */
    private fun perturbXYZ(
        xyz: Triple<Double, Double, Double>,
        lonDeg: Double,
        dr: Double = 0.0
    ): Triple<Double, Double, Double> {
        val (xh, yh, zh) = xyz
        val r    = sqrt(xh * xh + yh * yh + zh * zh)
        val rXY  = sqrt(xh * xh + yh * yh)
        val lon  = atan2(yh, xh)       // ecliptic longitude (radians)
        val lat  = atan2(zh, rXY)      // ecliptic latitude  (radians)
        val rNew = r + dr
        val lonNew = lon + lonDeg.toRad()
        return Triple(
            rNew * cos(lat) * cos(lonNew),
            rNew * cos(lat) * sin(lonNew),
            rNew * sin(lat)
        )
    }

    /**
     * Longitude perturbation for Jupiter due to Saturn's gravity (up to ~1°).
     *
     * Each term is a sinusoid whose argument is a linear combination of Jupiter's
     * mean anomaly `Mj` and Saturn's mean anomaly `Ms`. The coefficients and phases
     * are from Schlyter's fitted tables.
     *
     * @param Mj Jupiter's mean anomaly in degrees.
     * @param Ms Saturn's mean anomaly in degrees.
     * @return Longitude correction in degrees.
     */
    private fun jupiterLonPertDeg(Mj: Double, Ms: Double): Double {
        return (-0.332 * sin((2 * Mj - 5 * Ms - 67.6).toRad())
              - 0.056 * sin((2 * Mj - 2 * Ms + 21.0).toRad())
              + 0.042 * sin((3 * Mj - 5 * Ms + 20.9).toRad())
              - 0.036 * sin((    Mj - 2 * Ms        ).toRad())
              + 0.022 * cos((    Mj -     Ms        ).toRad())
              + 0.023 * sin((2 * Mj - 3 * Ms + 52.7).toRad())
              - 0.016 * sin((    Mj - 5 * Ms - 69.0).toRad()))
    }

    /**
     * Longitude and distance perturbations for Saturn due to Jupiter's gravity (up to ~3°).
     *
     * Saturn experiences the largest perturbations of any naked-eye planet because
     * Jupiter — almost three times Saturn's mass — is relatively nearby in the outer
     * solar system. Without these corrections Saturn's position can be off by several
     * degrees.
     *
     * @param Mj Jupiter's mean anomaly in degrees.
     * @param Ms Saturn's mean anomaly in degrees.
     * @return `Pair(dlon, dr)` — longitude correction (degrees) and distance correction (AU).
     */
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

    /**
     * Longitude perturbation for Uranus due to Jupiter and Saturn (up to ~0.1°).
     *
     * Much smaller than the Jupiter-Saturn interaction because Uranus is far from
     * both perturbers.
     *
     * @param Mj Jupiter's mean anomaly in degrees.
     * @param Ms Saturn's mean anomaly in degrees.
     * @param Mu Uranus's mean anomaly in degrees.
     * @return Longitude correction in degrees.
     */
    private fun uranusPertDeg(Mj: Double, Ms: Double, Mu: Double): Double {
        return (+0.040 * sin((Ms - 2 * Mu +  6.0).toRad())
              + 0.035 * sin((Ms - 3 * Mu + 33.0).toRad())
              - 0.015 * sin((Mj -     Mu + 20.0).toRad()))
    }
}
