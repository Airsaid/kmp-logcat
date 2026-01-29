package com.airsaid.logcat

/**
 * Defines how log files are generated on iOS.
 */
interface LogFileGenerator {

  /**
   * Returns the log file path to write the log.
   *
   * @param priority the log priority. See [LogPriority].
   * @param tag the log tag.
   * @param message the log message.
   * @param logFolder the base directory path where logs are stored.
   * @param maxSize the max size of each log file.
   */
  fun generateLogFile(
    priority: LogPriority,
    tag: String,
    message: String,
    logFolder: String,
    maxSize: Long,
  ): String
}
