package com.airsaid.logcat

import android.util.Log
import com.airsaid.logcat.internal.TAG
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

/**
 * The default log file generator.
 *
 * All log files are stored in the folder named by the current date, and the log file name is
 * generated based on the current time.
 *
 * For example:
 * - /sdcard/logcat/20210801
 *      2021-08-01_1.log
 *      2021-08-01_2.log
 * - /sdcard/logcat/20210802
 *      2021-08-02_1.log
 *      2021-08-02_2.log
 *
 * @author airsaid
 */
class DefaultLogFileGenerator : LogFileGenerator {

  override fun generateLogFile(
    priority: LogPriority,
    tag: String,
    message: String,
    logFolder: String,
    maxSize: Long,
  ): File {
    val logFolderOfToday = generateLogFolderOfToday(logFolder)
    return generateLogFile(logFolderOfToday, maxSize)
  }

  private fun generateLogFolderOfToday(logFolder: String): File {
    val currFolderName = formatFolderDate()
    val logFolderOfToday = File(logFolder, currFolderName)
    if (!logFolderOfToday.exists()) {
      if (!logFolderOfToday.mkdirs()) {
        Log.e(TAG, "Create log folder failed: ${logFolderOfToday.absolutePath}")
      }
    }
    return logFolderOfToday
  }

  private fun generateLogFile(
    logFolder: File,
    maxSize: Long
  ): File {
    // If already exists log files, get the last log file count number
    val logFileName = formatLogFileDate()
    var maxFileCount = 1
    logFolder.list()?.forEach {
      if (it.startsWith(logFileName)) {
        val count = it.substringAfterLast("_").substringBeforeLast(".").toIntOrNull()
        if (count != null) {
          maxFileCount = maxOf(maxFileCount, count)
        }
      }
    }

    val logFile = File(
      logFolder,
      String.format("%s_%s%s", logFileName, maxFileCount, LOG_FILE_EXTENSION)
    )
    // If the log file size exceeds the maximum size, create a new log file
    if (logFile.exists() && logFile.length() >= maxSize) {
      return File(
        logFolder,
        String.format("%s_%s%s", logFileName, maxFileCount + 1, LOG_FILE_EXTENSION)
      )
    }
    return logFile
  }

  companion object {
    private const val LOG_FILE_EXTENSION = ".log"
  }

  private fun formatFolderDate(): String {
    val date = Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault())
      .date
    return buildString {
      append(date.year.toString().padStart(4, '0'))
      append(date.monthNumber.toString().padStart(2, '0'))
      append(date.dayOfMonth.toString().padStart(2, '0'))
    }
  }

  private fun formatLogFileDate(): String {
    val date = Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault())
      .date
    return buildString {
      append(date.year.toString().padStart(4, '0'))
      append('-')
      append(date.monthNumber.toString().padStart(2, '0'))
      append('-')
      append(date.dayOfMonth.toString().padStart(2, '0'))
    }
  }
}
