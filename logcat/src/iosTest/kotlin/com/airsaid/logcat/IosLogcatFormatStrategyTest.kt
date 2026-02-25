package com.airsaid.logcat

import kotlin.test.Test
import kotlin.test.assertEquals

class IosLogcatFormatStrategyTest {

  @Test
  fun customTimestampFormatter_isUsedInOutput() {
    val logStrategy = CaptureLogStrategy()
    val formatStrategy = IosLogcatFormatStrategy.Builder<CaptureLogStrategy>()
      .logStrategy(logStrategy)
      .showProcessId(false)
      .showThreadInfo(false)
      .showTag(false)
      .showLevel(false)
      .timeStampFormatter { "CUSTOM_TS" }
      .build()

    formatStrategy.log(LogPriority.DEBUG, "Tag", "Hello")

    assertEquals("CUSTOM_TS\tHello\n", logStrategy.message)
  }

  private class CaptureLogStrategy : LogStrategy {
    var message: String? = null

    override fun log(priority: LogPriority, tag: String, message: String) {
      this.message = message
    }
  }
}
