package com.airsaid.logcat

import java.io.File

/**
 * The interface defines how to generate a log file.
 *
 * @author airsaid
 * @see DefaultLogFileGenerator
 */
interface LogFileGenerator {

  /**
   * Generate a log file.
   *
   * @param priority the priority of the log.
   * @param tag the tag of the log.
   * @param message the write message of the log.
   * @param logFolder the folder where the log file is stored.
   * @param maxSize the maximum size of the log file.
   * @return the generated file name.
   */
  fun generateLogFile(
    priority: LogPriority,
    tag: String,
    message: String,
    logFolder: String,
    maxSize: Long
  ): File
}