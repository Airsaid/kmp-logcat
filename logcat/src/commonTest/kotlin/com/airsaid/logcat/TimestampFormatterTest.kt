package com.airsaid.logcat

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampFormatterTest {

  @Test
  fun defaultFormatter_usesIsoInstantString() {
    val instant = Instant.parse("2026-02-25T12:34:56.789Z")

    val actual = TimestampFormatter.defaultFormatter(instant)

    assertEquals("2026-02-25T12:34:56.789Z", actual)
  }

  @Test
  fun patternFormatter_formatsWithGivenTimeZone() {
    val formatter = TimestampFormatter.patternFormatter(
      pattern = "uuuu-MM-dd HH:mm:ss.SSS",
      timeZone = TimeZone.of("Asia/Shanghai"),
    )
    val instant = Instant.parse("2026-02-25T12:34:56.789Z")

    val actual = formatter(instant)

    assertEquals("2026-02-25 20:34:56.789", actual)
  }

  @Test
  fun formatWithFallback_returnsIsoWhenFormatterThrows() {
    val instant = Instant.parse("2026-02-25T12:34:56.789Z")

    val actual = TimestampFormatter.formatWithFallback(instant) {
      error("boom")
    }

    assertEquals("2026-02-25T12:34:56.789Z", actual)
  }
}
