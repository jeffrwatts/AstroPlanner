package com.islandskiesastro.astroplanner

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator

actual fun moonRaDec(): Pair<Double, Double> {
    val time = Time.fromMillisecondsSince1970(System.currentTimeMillis())
    val observer = Observer(0.0, 0.0, 0.0)
    val eq = equator(Body.Moon, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
    return Pair(eq.ra * 15.0, eq.dec)
}
