package com.airsaid.logcat

internal typealias IosUnifiedLogSink = (
  priority: LogPriority,
  tag: String,
  message: String,
  privacy: IosLogcatPrivacy,
) -> Unit

/**
 * A log strategy that delegates to os_log for any log.
 *
 * Handles splitting logs by line, with byte-sized truncation handled internally.
 * Dynamic log content is [IosLogcatPrivacy.PRIVATE] by default. Pass
 * [IosLogcatPrivacy.PUBLIC] to the constructor only when messages are known not to contain
 * sensitive data.
 */
class IosLogcatLogStrategy internal constructor(
  internal val privacy: IosLogcatPrivacy,
  private val logSink: IosUnifiedLogSink,
) : LogStrategy {

  constructor() : this(IosLogcatPrivacy.PRIVATE, IosUnifiedLog::log)

  constructor(privacy: IosLogcatPrivacy) : this(privacy, IosUnifiedLog::log)

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
    logSink(priority, tag, part, privacy)
  }
}
