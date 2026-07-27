@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import com.airsaid.logcat.darwin.logcat_darwin_log_create
import com.airsaid.logcat.darwin.logcat_darwin_log_private_with_type
import com.airsaid.logcat.darwin.logcat_darwin_log_public_with_type
import platform.darwin.OS_LOG_TYPE_DEBUG
import platform.darwin.OS_LOG_TYPE_DEFAULT
import platform.darwin.OS_LOG_TYPE_ERROR
import platform.darwin.OS_LOG_TYPE_FAULT
import platform.darwin.OS_LOG_TYPE_INFO
import platform.darwin.os_log_type_t

internal typealias IosUnifiedLogWriter = (
  type: os_log_type_t,
  text: String,
  privacy: IosLogcatPrivacy,
) -> Unit

/**
 * Shared iOS logger that routes through os_log and enforces byte-sized truncation.
 */
internal object IosUnifiedLog {

  private const val MAX_LOG_BYTES = 1024
  private const val SUBSYSTEM = "com.airsaid.logcat"
  private const val CATEGORY = "logcat"

  private val logger = logcat_darwin_log_create(SUBSYSTEM, CATEGORY)!!

  fun log(
    priority: LogPriority,
    tag: String,
    message: String,
    privacy: IosLogcatPrivacy,
  ) {
    val type = mapLogType(priority)
    val text = "${priority.priorityLetter}/$tag: $message"
    logText(type, text, privacy)
  }

  fun logError(
    tag: String,
    message: String,
    writer: IosUnifiedLogWriter = ::logText,
  ) {
    val text = "E/$tag: $message"
    writer(OS_LOG_TYPE_ERROR, text, IosLogcatPrivacy.PRIVATE)
  }

  private fun logText(
    type: os_log_type_t,
    text: String,
    privacy: IosLogcatPrivacy,
  ) {
    splitAndWriteLog(type, text, privacy, ::writeDarwinLog)
  }

  internal fun splitAndWriteLog(
    type: os_log_type_t,
    text: String,
    privacy: IosLogcatPrivacy,
    writer: IosUnifiedLogWriter,
  ) {
    val parts = splitByUtf8Bytes(text, MAX_LOG_BYTES)
    for (part in parts) {
      writer(type, part, privacy)
    }
  }

  private fun writeDarwinLog(
    type: os_log_type_t,
    text: String,
    privacy: IosLogcatPrivacy,
  ) {
    when (privacy) {
      IosLogcatPrivacy.PRIVATE ->
        logcat_darwin_log_private_with_type(logger, type, text)
      IosLogcatPrivacy.PUBLIC ->
        logcat_darwin_log_public_with_type(logger, type, text)
    }
  }

  private fun mapLogType(priority: LogPriority): os_log_type_t = when (priority) {
    LogPriority.VERBOSE -> OS_LOG_TYPE_DEBUG
    LogPriority.DEBUG -> OS_LOG_TYPE_DEBUG
    LogPriority.INFO -> OS_LOG_TYPE_INFO
    LogPriority.WARN -> OS_LOG_TYPE_DEFAULT
    LogPriority.ERROR -> OS_LOG_TYPE_ERROR
    LogPriority.ASSERT -> OS_LOG_TYPE_FAULT
  }

  private fun splitByUtf8Bytes(text: String, maxBytes: Int): List<String> {
    if (text.isEmpty()) return listOf("")

    val result = ArrayList<String>()
    val builder = StringBuilder()
    var currentBytes = 0
    var index = 0

    while (index < text.length) {
      val first = text[index]
      val isSurrogatePair = first.isHighSurrogate() &&
        index + 1 < text.length &&
        text[index + 1].isLowSurrogate()
      val codePoint = if (isSurrogatePair) {
        val high = first.code
        val low = text[index + 1].code
        ((high - 0xD800) shl 10) + (low - 0xDC00) + 0x10000
      } else {
        first.code
      }
      val charBytes = utf8LengthOfCodePoint(codePoint)

      if (currentBytes > 0 && currentBytes + charBytes > maxBytes) {
        result.add(builder.toString())
        builder.setLength(0)
        currentBytes = 0
      }

      if (isSurrogatePair) {
        builder.append(first)
        builder.append(text[index + 1])
        index += 2
      } else {
        builder.append(first)
        index += 1
      }
      currentBytes += charBytes
    }

    if (builder.isNotEmpty()) {
      result.add(builder.toString())
    }
    return result
  }

  private fun utf8LengthOfCodePoint(codePoint: Int): Int = when {
    codePoint <= 0x7F -> 1
    codePoint <= 0x7FF -> 2
    codePoint <= 0xFFFF -> 3
    else -> 4
  }
}
