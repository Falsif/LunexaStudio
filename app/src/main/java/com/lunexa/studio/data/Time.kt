package com.lunexa.studio.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DEFAULT_ZONE = "Asia/Novosibirsk"

fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of(DEFAULT_ZONE))

fun localeOf(lang: String): Locale = if (lang == "en") Locale.ENGLISH else Locale.forLanguageTag("ru")

fun Long.toZdt(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(this).atZone(zone)
fun Long.toLocalDate(zone: ZoneId): LocalDate = toZdt(zone).toLocalDate()

fun LocalDate.millisAt(hour: Int, minute: Int, zone: ZoneId): Long =
    atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

fun LocalDate.startMillis(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun parseHm(s: String, defH: Int = 13, defM: Int = 25): Pair<Int, Int> {
    val parts = s.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it in 0..23 } ?: defH
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..59 } ?: defM
    return h to m
}

fun hm(h: Int, m: Int): String = "%02d:%02d".format(h, m)

object Fmt {
    fun date(ms: Long, zone: ZoneId, lang: String): String =
        DateTimeFormatter.ofPattern("d MMM yyyy", localeOf(lang)).format(ms.toZdt(zone))

    fun day(date: LocalDate, lang: String): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", localeOf(lang)).format(date)
            .replaceFirstChar { it.titlecase(localeOf(lang)) }

    fun shortDay(date: LocalDate, lang: String): String =
        DateTimeFormatter.ofPattern("EE, d MMM", localeOf(lang)).format(date)

    fun dateTime(ms: Long, zone: ZoneId, lang: String): String =
        DateTimeFormatter.ofPattern("d MMM, HH:mm", localeOf(lang)).format(ms.toZdt(zone))

    fun time(ms: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm").format(ms.toZdt(zone))

    fun monthYear(date: LocalDate, lang: String): String =
        DateTimeFormatter.ofPattern("LLLL yyyy", localeOf(lang)).format(date)
            .replaceFirstChar { it.titlecase(localeOf(lang)) }

    fun number(n: Long): String {
        val abs = kotlin.math.abs(n)
        return when {
            abs >= 1_000_000 -> "%.1fM".format(Locale.US, n / 1_000_000.0)
            abs >= 10_000 -> "%.1fK".format(Locale.US, n / 1_000.0)
            else -> "%,d".format(Locale.US, n).replace(',', ' ')
        }
    }

    fun signed(n: Long): String = (if (n > 0) "+" else "") + number(n)

    fun duration(sec: Int?): String = if (sec == null) "" else "%d:%02d".format(sec / 60, sec % 60)
}
