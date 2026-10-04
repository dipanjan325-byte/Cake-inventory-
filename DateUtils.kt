package com.cakeshop.manager.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Today's date as yyyy-MM-dd, in the device's local time zone. */
fun todayStr(): String = LocalDate.now().format(ISO)

fun parseDate(s: String): LocalDate = LocalDate.parse(s, ISO)

fun addDaysStr(date: String, days: Long): String = parseDate(date).plusDays(days).format(ISO)

/** expiry - date, in whole days (positive = in the future, 0 = today, negative = overdue). */
fun daysBetweenStr(from: String, to: String): Long = ChronoUnit.DAYS.between(parseDate(from), parseDate(to))

fun formatDateShort(date: String): String {
    val d = parseDate(date)
    val month = d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    return "${d.dayOfMonth} $month"
}

fun formatDateWeekday(date: String): String {
    val d = parseDate(date)
    val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    return "$wd ${d.dayOfMonth}"
}

/** Parses "N day" / "N days" (case-insensitive) into N, else null (e.g. for
 *  month-based shelf life, which next-day alerts don't apply to). */
fun parseShelfLifeDays(raw: String): Int? {
    val m = Regex("^(\\d+)\\s*day(s)?$", RegexOption.IGNORE_CASE).find(raw.trim())
    return m?.groupValues?.get(1)?.toIntOrNull()
}
