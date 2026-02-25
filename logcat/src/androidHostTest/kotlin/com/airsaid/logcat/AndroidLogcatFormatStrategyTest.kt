package com.airsaid.logcat

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLogcatFormatStrategyTest {

  @Test
  fun customTimestampFormatter_isUsedInOutput() {
    val logStrategy = CaptureLogStrategy()
    val formatStrategy = AndroidLogcatFormatStrategy.Builder<CaptureLogStrategy>()
      .logStrategy(logStrategy)
      .showProcessId(false)
      .showThreadInfo(false)
      .showTag(false)
      .showLevel(false)
      .timeStampFormatter { "CUSTOM_TS" }
      .build()

    formatStrategy.log(LogPriority.DEBUG, "Tag", "Hello")

    assertEquals("CUSTOM_TS\tHello${System.lineSeparator()}", logStrategy.message)
  }

  private class CaptureLogStrategy : LogStrategy {
    var message: String? = null

    override fun log(priority: LogPriority, tag: String, message: String) {
      this.message = message
    }
  }
}
