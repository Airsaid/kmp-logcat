@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber

/**
 * Resolves the max size of a log file before writing.
 */
fun interface LogFileMaxSizeResolver {

  /**
   * Returns the effective max size for a log file.
   *
   * @param logDirectory the base directory where logs are stored.
   * @param configuredMaxSize the configured max size.
   */
  fun resolveMaxSize(logDirectory: String, configuredMaxSize: Long): Long
}

/**
 * Uses the configured max size without changes.
 */
class FixedLogFileMaxSizeResolver : LogFileMaxSizeResolver {
  override fun resolveMaxSize(logDirectory: String, configuredMaxSize: Long): Long =
    configuredMaxSize
}

/**
 * Shrinks the max size based on available storage space.
 *
 * @param minSize the minimum allowed max size.
 * @param reserveBytes bytes reserved for other app usage.
 * @param usableSpaceFraction the fraction of available space allocated to a single log file.
 */
class AvailableSpaceLogFileMaxSizeResolver(
  private val minSize: Long = 256L * 1024L, // 256KB
  private val reserveBytes: Long = 50L * 1024L * 1024L, // 50MB
  private val usableSpaceFraction: Float = 0.1f,
) : LogFileMaxSizeResolver {

  override fun resolveMaxSize(logDirectory: String, configuredMaxSize: Long): Long {
    val usableSpace = resolveUsableSpace(logDirectory)
    if (usableSpace <= 0L) return configuredMaxSize

    val safeUpper = minOf(configuredMaxSize, usableSpace)
    val availableForLogs = (usableSpace - reserveBytes).coerceAtLeast(0L)
    val dynamicLimit = minOf(safeUpper, (availableForLogs * usableSpaceFraction).toLong())
    val minAllowed = minOf(minSize, safeUpper)
    return maxOf(minAllowed, dynamicLimit)
  }

  private fun resolveUsableSpace(logDirectory: String): Long {
    val fileManager = NSFileManager.defaultManager
    val attrs = fileManager.attributesOfFileSystemForPath(logDirectory, error = null) ?: return 0L
    val freeSizeAttr = attrs[NSFileSystemFreeSize]
    return freeSizeAttr.toLongOrNull() ?: 0L
  }

  private fun Any?.toLongOrNull(): Long? = when (this) {
    is NSNumber -> longLongValue
    is Long -> this
    is Int -> toLong()
    else -> null
  }
}
