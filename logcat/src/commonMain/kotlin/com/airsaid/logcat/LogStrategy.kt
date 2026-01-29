package com.airsaid.logcat

/**
 * Determines destination target for the logs such as Disk, Logcat etc.
 *
 * @author airsaid
 * @see AndroidLogcatLogStrategy
 * @see DiskLogStrategy
 */
interface LogStrategy {

  /**
   * Write a log to its destination. Called by [logcat].
   *
   * @param priority the log priority. See [LogPriority].
   * @param tag the log tag.
   * @param message the log message.
   */
  fun log(priority: LogPriority, tag: String, message: String)
}