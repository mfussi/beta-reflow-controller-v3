package com.tangentlines.reflowcontroller.ui

import java.util.Locale

object UiFormat {
    private const val DASH = "–"

    // ---------- Temperature ----------
    fun temp(value: Float?, decimals: Int = 1, unit: String = "°C", locale: Locale = Locale.US): String =
        value?.let { "${num(it, decimals, locale)} $unit" } ?: DASH

    fun tempPair(actual: Float?, target: Float?, decimals: Int = 1, unit: String = "°C", locale: Locale = Locale.US): String {
        val a = actual?.let { if(it > -1.0f) "<b>${num(it, decimals, locale)}</b>" else "<b>$DASH</b>" } ?: DASH
        val t = target?.let {if(it > -1.0f)  num(it, decimals, locale) else DASH } ?: DASH
        return "<html>$a / $t $unit</html>"
    }

    // ---------- Duration (seconds -> "mm min / ss sec") ----------
    fun duration(seconds: Long?): String =
        seconds?.let {
            val m = (it / 60)
            val s = it - (m * 60)
            if(m == 0L){
                return@let "$s sec"
            } else {
                "$m min, $s sec"
            }
        } ?: DASH

    fun durationPair(actualSec: Long?, targetSec: Long?, postFix : String? = null): String {
        val a = actualSec?.let { "<b>${duration(it)}</b>" } ?: "<b>$DASH</b>"
        val t = targetSec?.let { duration(it) } ?: DASH
        return "<html>$a / $t${postFix?.let { " $it" } ?: ""}</html>"
    }

    // ---------- Percentage ----------
    /**
     * percentage(90f) -> "90 %"
     * Set fraction=true if you pass 0.0..1.0 and want 0..100 (%).
     */
    fun percentage(value: Float?, decimals: Int = 0, fraction: Boolean = false, locale: Locale = Locale.US): String {
        val v = value ?: return DASH
        val pct = if (fraction) v * 100f else v
        return "${num(pct, decimals, locale)} %"
    }

    fun percentagePair(actual: Float?, target: Float?, decimals: Int = 0, fraction: Boolean = false, locale: Locale = Locale.US): String {
        val a = actual?.let {
            val pct = if (fraction) it * 100f else it
            "<b>${num(pct, decimals, locale)}</b>"
        } ?: DASH
        val t = target?.let {
            val pct = if (fraction) it * 100f else it
            num(pct, decimals, locale)
        } ?: DASH
        return "<html>$a / $t %</html>"
    }

    // ---------- Helpers ----------
    fun num(v: Float, decimals: Int, locale: Locale): String =
        String.format(locale, "%.${decimals}f", v)
}
