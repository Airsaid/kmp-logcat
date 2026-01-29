package com.airsaid.logcat

/**
 * Used to determine how messages should be printed or saved.
 *
 * @author airsaid
 * @see NonFormatStrategy
 * @see PrettyFormatStrategy
 * @see AndroidLogcatFormatStrategy
 */
interface FormatStrategy<S : LogStrategy> {

  /**
   * The log strategy to use to determine how to handler the formatted log message.
   */
  val logStrategy: S

  /**
   * Write a log to its destination. Called by [logcat].
   *
   * @param priority the log priority. See [LogPriority].
   * @param tag the log tag.
   * @param message the log message.
   */
  fun log(priority: LogPriority, tag: String, message: String)
}