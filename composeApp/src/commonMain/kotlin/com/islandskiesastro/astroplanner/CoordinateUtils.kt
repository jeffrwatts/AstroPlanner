package com.islandskiesastro.astroplanner

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs

private fun fmtSec(s: Double): String {
    val w = s.toInt()
    val f = ((s - w) * 100 + 0.5).toInt().coerceIn(0, 99)
    return "${w.toString().padStart(2, '0')}.${f.toString().padStart(2, '0')}"
}

// Degrees → HH:MM:SS.ss
internal fun Double.toRaString(): String {
    val hours = this / 15.0
    val h = hours.toInt()
    val rem1 = (hours - h) * 60.0
    val m = rem1.toInt()
    val s = (rem1 - m) * 60.0
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${fmtSec(s)}"
}

// Absolute degrees → DD:MM:SS.ss (no sign prefix)
internal fun Double.toDecBodyString(): String {
    val a = abs(this)
    val d = a.toInt()
    val rem1 = (a - d) * 60.0
    val m = rem1.toInt()
    val s = (rem1 - m) * 60.0
    return "${d.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${fmtSec(s)}"
}

// HH:MM:SS[.ss] → degrees, null if invalid. A plain decimal value (no ':') is
// interpreted as decimal degrees directly, matching the format the database stores.
internal fun parseRa(input: String): Double? {
    val trimmed = input.trim()
    val parts = trimmed.split(":")
    if (parts.size == 1) {
        val deg = trimmed.toDoubleOrNull() ?: return null
        return if (deg in 0.0..360.0) deg else null
    }
    if (parts.size != 3) return null
    val h = parts[0].toDoubleOrNull() ?: return null
    val m = parts[1].toDoubleOrNull() ?: return null
    val s = parts[2].toDoubleOrNull() ?: return null
    if (h < 0 || h >= 24 || m < 0 || m >= 60 || s < 0 || s >= 60) return null
    return (h + m / 60.0 + s / 3600.0) * 15.0
}

// DD:MM:SS[.ss] (no sign) → absolute degrees, null if invalid. A plain decimal value
// (no ':') is interpreted as decimal degrees directly, matching the format the database stores.
internal fun parseDecBody(input: String): Double? {
    val trimmed = input.trim()
    val parts = trimmed.split(":")
    if (parts.size == 1) {
        val deg = trimmed.toDoubleOrNull() ?: return null
        return if (deg in 0.0..90.0) deg else null
    }
    if (parts.size != 3) return null
    val d = parts[0].toDoubleOrNull() ?: return null
    val m = parts[1].toDoubleOrNull() ?: return null
    val s = parts[2].toDoubleOrNull() ?: return null
    if (d < 0 || d > 90 || m < 0 || m >= 60 || s < 0 || s >= 60) return null
    val result = d + m / 60.0 + s / 3600.0
    return if (result > 90.0) null else result
}

// Auto-insert ':' after the 2nd and 4th digit typed; cursor placed after the colon.
internal fun autoInsertColon(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.text.length <= old.text.length) return new
    if (new.text.isEmpty() || !new.text.last().isDigit()) return new
    val digitCount = new.text.count { it.isDigit() }
    return if (digitCount == 2 || digitCount == 4) {
        val withColon = "${new.text}:"
        new.copy(text = withColon, selection = TextRange(withColon.length))
    } else new
}
