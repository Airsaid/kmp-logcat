package com.airsaid.logcat

/**
 * A [logcat] format strategy that does not format the log message anymore, just delegate to
 * the [LogStrategy] to handle the log message.
 *
 * @author airsaid
 */
class NonFormatStrategy<S : LogStrategy>(override val logStrategy: S) : FormatStrategy<S> {
  override fun log(priority: LogPriority, tag: String, message: String) {
    logStrategy.log(priority, tag, message)
  }
}