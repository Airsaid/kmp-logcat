package com.airsaid.logcat

/**
 * A log strategy that delegates to os_log for any log.
 *
 * Handles splitting logs by line, with byte-sized truncation handled internally.
 */
class IosLogcatLogStrategy : LogStrategy {

  override fun log(priority: LogPriority, tag: String, message: String) {
    var i = 0
    val length = message.length
    while (i < length) {
      var newline = message.indexOf('\n', i)
      newline = if (newline != -1) newline else length
      val part = message.substring(i, newline)
      logToConsole(priority, tag, part)
      i = newline + 1
    }
  }

  private fun logToConsole(priority: LogPriority, tag: String, part: String) {
    IosUnifiedLog.log(priority, tag, part)
  }
}
