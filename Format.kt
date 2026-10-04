package com.cakeshop.manager.ui

import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToLong

private val moneyFmt = DecimalFormat("#,##0.##")

fun money(n: Double): String {
    val r = (n * 100).roundToLong() / 100.0
    val sign = if (r < 0) "-" else ""
    return "$sign\u20B9${moneyFmt.format(abs(r))}"
}

fun qty(n: Double): String {
    val r = (n * 100).roundToLong() / 100.0
    return moneyFmt.format(r)
}

fun compactMoney(n: Double): String {
    val a = abs(n)
    val s = when {
        a >= 10_000_000 -> "${trimZero(n / 10_000_000)}Cr"
        a >= 100_000 -> "${trimZero(n / 100_000)}L"
        a >= 1_000 -> "${trimZero(n / 1_000)}k"
        else -> n.roundToLong().toString()
    }
    return "\u20B9$s"
}

private fun trimZero(v: Double): String {
    val r = (v * 10).roundToLong() / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}
