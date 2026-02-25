package com.airsaid.logcat

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime

internal object TimestampFormatter {

  fun defaultFormatter(instant: Instant): String = instant.toString()

  @OptIn(FormatStringsInDatetimeFormats::class)
  fun patternFormatter(
    pattern: String,
    timeZone: TimeZone,
  ): (Instant) -> String {
    val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(pattern) }
    return { instant ->
      dateTimeFormat.format(instant.toLocalDateTime(timeZone))
    }
  }

  fun formatWithFallback(
    instant: Instant,
    formatter: (Instant) -> String,
  ): String = runCatching { formatter(instant) }.getOrElse { instant.toString() }
}
