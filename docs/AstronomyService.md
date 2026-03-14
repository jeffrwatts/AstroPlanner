# AstronomyService

**File:** `composeApp/src/commonMain/kotlin/com/islandskiesastro/astroplanner/AstronomyService.kt`

Pure-Kotlin astronomy engine used by the Sky Planner screen. Runs identically on Android and iOS — no platform dependencies. All calculations use only `kotlin.math` and `kotlinx-datetime`.

---

## Overview

The service computes three things the Sky Planner needs:

| Function | What it answers |
|---|---|
| `getAltAzm` | Where in the sky is this object *right now*? |
| `getMeridianTransit` | When does it cross the meridian (its highest point)? |
| `getPlanetRaDec` | Where are the planets in the sky (RA/Dec)? |

Deep-sky objects (nebulae, star clusters, galaxies) have fixed RA/Dec stored in the database, so only `getAltAzm` is needed for those. Planets move, so `getPlanetRaDec` is called first to get their current position, and that result is then fed into `getAltAzm`.

---

## Coordinate Systems

| System | Description | Used for |
|---|---|---|
| **Equatorial** (RA/Dec) | RA = angle east along celestial equator from vernal equinox; Dec = angle north/south of equator. Fixed to the stars. | Catalog positions, planet output |
| **Ecliptic** (lon/lat) | Plane of Earth's orbit around the Sun. Planets move near this plane. | Internal planet calculation |
| **Horizontal** (Alt/Az) | Alt = degrees above horizon; Az = degrees clockwise from North. Observer-dependent, changes every second. | Final display |

---

## Public API

### `getAltAzm(ra, dec, lat, lon, altMeters) → AltAzmData`

Converts a catalog position (RA/Dec) to the observer's local sky (altitude and azimuth) at the current moment.

**How it works:**

1. **GMST → LST** — Greenwich Mean Sidereal Time is computed from the current Julian Date using the IAU polynomial formula. Adding the observer's longitude gives Local Sidereal Time (LST): the RA currently on the meridian.

2. **Hour Angle** — `HA = LST − RA`. This is how far the object has rotated west of the meridian. HA = 0 means the object is due south and at its highest.

3. **Spherical trig** — The standard altitude formula:
   ```
   sin(alt) = sin(dec)·sin(lat) + cos(dec)·cos(lat)·cos(HA)
   ```
   Azimuth is derived from a second formula and the sign of sin(HA) resolves the quadrant.

4. **Atmospheric refraction** — The atmosphere bends light toward the horizon, so objects appear slightly higher than they geometrically are. Bennett's formula applies a correction of up to ~0.5° near the horizon. This is only applied when altitude > −2°.

**Output:** `AltAzmData(altitude, azimuth)` where altitude is refraction-corrected.

---

### `getMeridianTransit(ra, lon, lat) → String`

Predicts the next time the object will cross the meridian (its highest point in the sky) and returns it as a 12-hour clock string like `"9:34 pm"`.

**How it works:**

1. Compute the current Hour Angle of the object. If HA is negative the object hasn't reached the meridian yet; if positive it already passed.
2. Express the remaining angular distance to HA = 0 in sidereal degrees.
3. Convert to wall-clock time. The sky rotates 360° in one **sidereal day** = 23 hours 56 minutes 4 seconds. So the wait in solar milliseconds is:
   ```
   wait = (degrees_remaining / 360) × 23.9344696 hours × 3 600 000 ms/hour
   ```
4. Add to the current time and format in the device's local timezone.

If the object just transited, the result will be approximately 23h 56m from now (tomorrow's transit).

---

### `getPlanetRaDec(planetObjectId) → Pair<Double, Double>`

Returns the current geocentric Right Ascension and Declination (both in degrees) for one of the seven planets: Mercury, Venus, Mars, Jupiter, Saturn, Uranus, Neptune.

The `planetObjectId` strings match the database: `"mercury"`, `"venus"`, `"mars"`, `"jupiter"`, `"saturn"`, `"uranus"`, `"neptune"`. Returns `(0.0, 0.0)` for unrecognised input.

**Accuracy:** ~1° for inner planets, ~0.5° for outer planets. Sufficient for a visual sky planner; not suitable for telescope pointing.

See the pipeline description in [Planet Position Pipeline](#planet-position-pipeline) below.

---

## Internal Architecture

### Time Representation

All planet calculations use **d = days from J2000.0** (2000-Jan-01 12:00 UTC). This is a small integer (about 9200 for early 2026) that makes the linear orbital element formulae easy to evaluate.

```
JD  = ms / 86 400 000 + 2 440 587.5     (Julian Date from Unix ms)
d   = JD − 2 451 545.0                  (days from J2000.0)
```

---

### GMST Formula

Greenwich Mean Sidereal Time is the foundation of the alt/az calculation. The IAU polynomial:

```
T    = d / 36525                         (Julian centuries)
GMST = 280.46061837
     + 360.98564736629 · d
     + 0.000387933 · T²
     − T³ / 38 710 000
```

The dominant term `360.986 × d` reflects that Earth rotates 360.986° per solar day (one extra degree per day relative to the stars due to Earth's orbit around the Sun). The cubic correction accounts for the very slow precession of Earth's axis (~26 000-year cycle).

---

### Planet Position Pipeline

`getPlanetRaDecForD(planetId, d)` runs a five-step chain:

```
Orbital elements (Schlyter)
        ↓
Kepler's equation → eccentric anomaly E
        ↓
Heliocentric ecliptic rectangular (xh, yh, zh)    [AU, origin = Sun]
        ↓
Perturbation corrections (Jupiter/Saturn/Uranus)
        ↓
Geocentric ecliptic rectangular                   [origin = Earth]
  xg = xh + xs   (add Sun's geocentric position)
        ↓
Rotate by obliquity ε → equatorial rectangular
  yeq = yg·cos(ε) − zg·sin(ε)
  zeq = yg·sin(ε) + zg·cos(ε)
        ↓
RA  = atan2(yeq, xg)
Dec = atan2(zeq, √(xg² + yeq²))
```

#### Step 1 — Orbital Elements (Schlyter)

Each planet's orbit is described by six Keplerian elements. They change slowly over time, so a simple linear formula `element = value_at_J2000 + drift_rate × d` is accurate enough for the required precision.

| Element | Symbol | Meaning |
|---|---|---|
| Longitude of ascending node | N | Where the orbit crosses the ecliptic plane heading north |
| Inclination | i | Tilt of the orbital plane relative to the ecliptic |
| Argument of perihelion | w | Angle from ascending node to the closest-approach point |
| Semi-major axis | a | Average planet-Sun distance in AU |
| Eccentricity | e | How elongated the ellipse is (0 = circle) |
| Mean anomaly | M | Uniform angular "clock hand" that ticks from 0° at perihelion |

#### Step 2 — Kepler's Equation

The mean anomaly M advances uniformly but doesn't tell you where on the ellipse the planet actually is. Kepler's equation relates M to the **eccentric anomaly** E (a geometric angle that does encode the true position):

```
E − e·sin(E) = M
```

This is solved numerically by Newton-Raphson iteration. Starting from a first-order approximation `E₀ ≈ M + e·sin(M)·(1 + e·cos(M))`, typically 2–4 iterations are sufficient.

#### Step 3 — Heliocentric Rectangular Coordinates

From E, the position in the orbital plane is:
```
xv = a·(cos E − e)
yv = a·√(1−e²)·sin E
```
These are then rotated from the orbital plane into the ecliptic plane using N, i, and the argument of latitude (v + w), yielding `(xh, yh, zh)` in AU relative to the Sun.

#### Step 4 — Perturbation Corrections

The Keplerian orbit ignores the gravitational pulls of other planets. The dominant interactions are:

| Planet | Perturber | Max error without correction |
|---|---|---|
| Jupiter | Saturn | ~1° |
| Saturn | Jupiter | ~3° |
| Uranus | Jupiter, Saturn | ~0.1° |

Each correction is a sum of sinusoidal terms whose arguments involve the mean anomalies of the perturbing planets. For example, Saturn's largest correction term is `+0.812° × sin(2·Mj − 5·Ms − 67.6°)`. The correction is applied to the ecliptic longitude and (for Saturn) the heliocentric distance.

Mercury, Venus, Mars, and Neptune have no corrections at this precision level.

#### Step 5 — Geocentric Conversion

The heliocentric position must be shifted from Sun-centred to Earth-centred coordinates.

`earthHeliocentric(d)` uses Schlyter's Sun orbital elements (N = 0, i = 0) to compute the Sun's geocentric position `(xs, ys)`. Because N = 0 and i = 0, the Sun moves in the ecliptic plane and the formula simplifies to `xs = r·cos(lon_sun)`, `ys = r·sin(lon_sun)`.

Note that `xs = −xe` (the Sun's geocentric position is the negative of Earth's heliocentric position). Therefore the geocentric planet position is:
```
xg = xh + xs     (= xh − Earth_heliocentric_x)
yg = yh + ys
zg = zh + zs
```

#### Step 6 — Ecliptic to Equatorial Rotation

The ecliptic plane is tilted ~23.4° relative to the celestial equator. Rotating the coordinates by the **obliquity of the ecliptic** ε converts from ecliptic rectangular to equatorial rectangular:
```
ε = 23.4393° − 3.563×10⁻⁷ · d
```
The rotation is around the x-axis (the direction of the vernal equinox, shared by both systems):
```
xeq = xg
yeq = yg·cos(ε) − zg·sin(ε)
zeq = yg·sin(ε) + zg·cos(ε)
```

---

## Accuracy Summary

| Object type | Altitude/Azimuth accuracy | Notes |
|---|---|---|
| Deep-sky objects | Exact (limited only by floating point) | RA/Dec from catalog, no approximation |
| Inner planets (Mercury, Venus, Mars) | ~1–2° | Schlyter algorithm limit |
| Outer planets (Jupiter–Neptune) | ~0.5° | Perturbations reduce outer-planet error significantly |

---

## Algorithm Sources

- **Orbital elements and perturbations:** Paul Schlyter, *"How to compute planetary positions"* — [stjarnhimlen.se/comp/ppcomp.html](https://stjarnhimlen.se/comp/ppcomp.html)
- **GMST formula:** IAU 1982 polynomial (see Meeus, *Astronomical Algorithms*, ch. 12)
- **Spherical trig:** Standard formulae for the astronomical triangle (Meeus, ch. 13)
- **Atmospheric refraction:** Bennett (1982) formula as cited in Meeus, ch. 16
- **Kepler solver:** Newton-Raphson, as described in Meeus, ch. 30
