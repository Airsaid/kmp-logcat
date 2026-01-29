package com.airsaid.logcat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.VisibleForTesting
import java.io.File

/**
 * Resolves the max size of a log file before writing.
 *
 * @author airsaid
 */
fun interface LogFileMaxSizeResolver {

  /**
   * Returns the effective max size for a log file.
   *
   * @param logDirectory the base directory where logs are stored.
   * @param configuredMaxSize the configured max size.
   */
  fun resolveMaxSize(logDirectory: File, configuredMaxSize: Long): Long
}

/**
 * Uses the configured max size without changes.
 */
class FixedLogFileMaxSizeResolver : LogFileMaxSizeResolver {
  override fun resolveMaxSize(logDirectory: File, configuredMaxSize: Long): Long =
    configuredMaxSize
}

/**
 * Shrinks the max size based on available storage space.
 *
 * @param context the context used to query allocatable storage.
 * @param minSize the minimum allowed max size.
 * @param reserveBytes bytes reserved for other app usage.
 * @param usableSpaceFraction the fraction of available space allocated to a single log file.
 * @param usableSpaceProvider optional provider for usable space, mainly for testing.
 */
class AvailableSpaceLogFileMaxSizeResolver(
  private val context: Context,
  private val minSize: Long = 256L * 1024L, // 256KB
  private val reserveBytes: Long = 50L * 1024L * 1024L, // 50MB
  private val usableSpaceFraction: Float = 0.1f,
) : LogFileMaxSizeResolver {

  private var usableSpaceProvider: ((File) -> Long)? = null

  @VisibleForTesting
  internal constructor(
    context: Context,
    minSize: Long,
    reserveBytes: Long,
    usableSpaceFraction: Float,
    usableSpaceProvider: (File) -> Long,
  ) : this(
    context = context,
    minSize = minSize,
    reserveBytes = reserveBytes,
    usableSpaceFraction = usableSpaceFraction,
  ) {
    this.usableSpaceProvider = usableSpaceProvider
  }

  override fun resolveMaxSize(logDirectory: File, configuredMaxSize: Long): Long {
    val usableSpace = resolveUsableSpace(logDirectory)
    if (usableSpace <= 0L) return configuredMaxSize

    val safeUpper = minOf(configuredMaxSize, usableSpace)
    val availableForLogs = (usableSpace - reserveBytes).coerceAtLeast(0L)
    val dynamicLimit = minOf(safeUpper, (availableForLogs * usableSpaceFraction).toLong())
    val minAllowed = minOf(minSize, safeUpper)
    return maxOf(minAllowed, dynamicLimit)
  }

  @SuppressLint("UsableSpace")
  private fun resolveUsableSpace(logDirectory: File): Long {
    usableSpaceProvider?.let { return it(logDirectory) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return try {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val uuid = storageManager?.getUuidForPath(logDirectory) ?: return logDirectory.usableSpace
        storageManager.getAllocatableBytes(uuid)
      } catch (e: Exception) {
        logDirectory.usableSpace
      }
    }
    return logDirectory.usableSpace
  }
}
