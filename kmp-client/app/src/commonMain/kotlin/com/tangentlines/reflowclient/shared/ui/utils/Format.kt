package com.tangentlines.reflowclient.shared.ui.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun durationMs(ms: Long?): String = duration(ms?.let { it / 1000L })

fun duration(seconds: Long?): String =
    seconds?.let {
        val m = (it / 60)
        val s = it - (m * 60)
        if(m == 0L){
            return@let "$s sec"
        } else {

            if(s == 0L){
                "$m min"
            } else {
                "$m min, $s sec"
            }

        }
    } ?: "-"

@OptIn(ExperimentalTime::class)
fun unixMsToHHmm(ms: Long, tz: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    return "${pad2(local.hour)}:${pad2(local.minute)}:${pad2(local.second)}"
}

private fun pad2(n: Int) = if (n < 10) "0$n" else n.toString()

/**
 * Convert milliseconds (duration) to a clock string.
 * Examples:  75_000 -> "01:15",  3_600_500 -> "01:00:00" (hours shown when needed)
 */
fun Long.toClockString(includeSeconds: Boolean = true): String {
    val totalSec = (this / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    fun pad(n: Long) = if (n < 10) "0$n" else n.toString()
    val showHours = h > 0
    return if (includeSeconds) {
        if (showHours) "${pad(h)}:${pad(m)}:${pad(s)}" else "${pad(m)}:${pad(s)}"
    } else {
        if (showHours) "${pad(h)}:${pad(m)}" else pad(m)
    }
}

fun Float.toFixed(decimals: Int): String {
    if (decimals <= 0) return round(this).toInt().toString()
    val factor = 10.0.pow(decimals).toLong()
    val scaled = round(this.toDouble() * factor).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = abs(scaled.toDouble()).toLong()
    val intPart = absScaled / factor
    val frac = (absScaled % factor).toString().padStart(decimals, '0')
    return "$sign$intPart.$frac"
}

fun Float?.f1(): String = this?.toFixed(1) ?: "-"
fun Float?.f2(): String = this?.toFixed(2) ?: "-"
fun Float?.percent0(): String = this?.let { (it * 100f).toFixed(0) + " %" } ?: "- %"
