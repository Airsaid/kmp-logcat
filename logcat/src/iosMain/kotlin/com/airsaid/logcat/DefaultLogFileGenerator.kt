@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import com.airsaid.logcat.internal.TAG
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

/**
 * The default log file generator for iOS.
 */
class DefaultLogFileGenerator : LogFileGenerator {

  override fun generateLogFile(
    priority: LogPriority,
    tag: String,
    message: String,
    logFolder: String,
    maxSize: Long,
  ): String {
    val logFolderOfToday = generateLogFolderOfToday(logFolder)
    return generateLogFile(logFolderOfToday, maxSize)
  }

  private fun generateLogFolderOfToday(logFolder: String): String {
    val currFolderName = formatFolderDate()
    val logFolderOfToday = "$logFolder/$currFolderName"
    val fileManager = NSFileManager.defaultManager
    val exists = fileManager.fileExistsAtPath(logFolderOfToday)
    if (!exists) {
      val created = fileManager.createDirectoryAtPath(
        path = logFolderOfToday,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
      if (!created) {
        IosUnifiedLog.logError(TAG, "Create log folder failed: $logFolderOfToday")
      }
    }
    return logFolderOfToday
  }

  private fun generateLogFile(logFolder: String, maxSize: Long): String {
    val fileManager = NSFileManager.defaultManager
    val logFileName = formatLogFileDate()
    var maxFileCount = 1
    val files = fileManager.contentsOfDirectoryAtPath(logFolder, error = null) ?: emptyList<Any>()
    for (item in files) {
      val name = item as? String ?: continue
      if (name.startsWith(logFileName)) {
        val count = name.substringAfterLast("_").substringBeforeLast(".").toIntOrNull()
        if (count != null) {
          maxFileCount = maxOf(maxFileCount, count)
        }
      }
    }

    val logFile = "$logFolder/${logFileName}_${maxFileCount}$LOG_FILE_EXTENSION"
    val fileSizeAttr = fileManager.attributesOfItemAtPath(logFile, error = null)?.get(NSFileSize)
    val fileSize = fileSizeAttr.toLongOrNull()
    if (fileSize != null && fileSize >= maxSize) {
      return "$logFolder/${logFileName}_${maxFileCount + 1}$LOG_FILE_EXTENSION"
    }
    return logFile
  }

  companion object {
    private const val LOG_FILE_EXTENSION = ".log"
  }

  private fun Any?.toLongOrNull(): Long? = when (this) {
    is NSNumber -> longLongValue
    is Long -> this
    is Int -> toLong()
    else -> null
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
