package com.islandskiesastro.astroplanner

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.siderealTime

actual object AstronomyService {

    private fun now(): Time = Time.fromMillisecondsSince1970(System.currentTimeMillis())

    actual fun getAltAzm(ra: Double, dec: Double, lat: Double, lon: Double, altMeters: Double): AltAzmData {
        val time = now()
        val observer = Observer(lat, lon, altMeters)
        val raHours = ra / 15.0
        val hor = horizon(time, observer, raHours, dec, Refraction.Normal)
        return AltAzmData(hor.altitude, hor.azimuth)
    }

    actual fun getPlanetRaDec(planetObjectId: String): Pair<Double, Double> {
        val body = when (planetObjectId.lowercase()) {
            "mercury" -> Body.Mercury
            "venus"   -> Body.Venus
            "mars"    -> Body.Mars
            "jupiter" -> Body.Jupiter
            "saturn"  -> Body.Saturn
            "uranus"  -> Body.Uranus
            "neptune" -> Body.Neptune
            else      -> return Pair(0.0, 0.0)
        }
        val time = now()
        val observer = Observer(0.0, 0.0, 0.0)
        val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        return Pair(eq.ra * 15.0, eq.dec)
    }

    actual fun getMeridianTransit(ra: Double, lon: Double, lat: Double): String {
        val raHours = ra / 15.0
        val time = now()
        val gmst = siderealTime(time)
        val lst = (gmst + lon / 15.0 + 48.0) % 24.0

        var haHours = lst - raHours
        while (haHours > 12.0) haHours -= 24.0
        while (haHours < -12.0) haHours += 24.0

        // Sidereal hours until next transit (HA = 0)
        val siderealHoursUntilTransit = if (haHours <= 0.0) -haHours else 24.0 - haHours
        // Convert to solar milliseconds: 1 sidereal day = 23.9344696 solar hours
        val solarMsUntilTransit = (siderealHoursUntilTransit * 3600.0 * 1000.0 * 23.9344696 / 24.0).toLong()

        val nowMs = System.currentTimeMillis()
        var transitMs = nowMs + solarMsUntilTransit

        val msPerHour = 3_600_000L
        val msPerDay = 86_400_000L
        val tz = java.util.TimeZone.getDefault()
        val offsetMs = tz.getOffset(nowMs).toLong()
        val localTransitMs = transitMs + offsetMs
        val minuteOfDay = ((localTransitMs % msPerDay) / 60_000L).toInt()
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val ampm = if (hour < 12) "am" else "pm"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "${hour12}:${minute.toString().padStart(2, '0')} $ampm"
    }
}
