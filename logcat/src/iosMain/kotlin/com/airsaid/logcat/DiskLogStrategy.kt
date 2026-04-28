@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class,
)

package com.airsaid.logcat

import com.airsaid.logcat.internal.TAG
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSLock
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * The log strategy that writes the log to disk on iOS.
 */
class DiskLogStrategy private constructor(builder: Builder) : LogStrategy {

  private val directory = builder.directory
  private val fileGenerator = builder.fileGenerator
  private val maxSize = builder.maxSize
  private val maxSizeResolver = builder.maxSizeResolver
  private val maxTime = builder.maxTime
  private val bufferMaxSize = builder.bufferMaxSize

  private val lock = NSLock()
  private val bufferedLogs = ArrayList<BufferedLog>()
  private var bufferedSize = 0
  private var isClosed = false

  override fun log(priority: LogPriority, tag: String, message: String) {
    lock.lock()
    try {
      if (isClosed) return
      val size = messageSize(message)
      bufferedLogs.add(BufferedLog(priority, tag, message, size))
      bufferedSize += size
      if (bufferedSize >= bufferMaxSize) {
        flushBufferLocked()
      }
    } finally {
      lock.unlock()
    }
  }

  /**
   * Flush buffered logs to disk synchronously.
   */
  internal fun flush() {
    lock.lock()
    try {
      if (isClosed) return
      flushBufferLocked()
    } finally {
      lock.unlock()
    }
  }

  /**
   * Flush buffered logs and stop accepting new logs.
   */
  internal fun close() {
    lock.lock()
    try {
      if (isClosed) return
      flushBufferLocked()
      isClosed = true
    } finally {
      lock.unlock()
    }
  }

  private fun flushBufferLocked() {
    if (bufferedLogs.isEmpty()) return

    val fileManager = NSFileManager.defaultManager
    ensureDirectoryExists(fileManager, directory)
    deleteExpiredLogFiles(fileManager, directory)

    val resolvedMaxSize = maxSizeResolver.resolveMaxSize(directory, maxSize)
    val bufferMap = LinkedHashMap<String, StringBuilder>()
    for (log in bufferedLogs) {
      val logFile = fileGenerator.generateLogFile(
        log.priority,
        log.tag,
        log.message,
        directory,
        resolvedMaxSize,
      )
      val builder = bufferMap.getOrPut(logFile) { StringBuilder() }
      builder.append(log.message)
    }

    try {
      for ((logFile, builder) in bufferMap) {
        ensureLogFileExists(fileManager, logFile)
        appendToFile(fileManager, logFile, builder.toString())
      }
    } catch (e: Throwable) {
      IosUnifiedLog.logError(TAG, "Write log to disk failed. ${e}")
    } finally {
      bufferedLogs.clear()
      bufferedSize = 0
    }
  }

  private fun ensureDirectoryExists(fileManager: NSFileManager, path: String) {
    if (!fileManager.fileExistsAtPath(path)) {
      fileManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
    }
  }

  private fun ensureLogFileExists(fileManager: NSFileManager, path: String) {
    if (!fileManager.fileExistsAtPath(path)) {
      val created = fileManager.createFileAtPath(path, contents = null, attributes = null)
      if (!created) {
        IosUnifiedLog.logError(TAG, "Create log file failed: $path")
      }
    }
  }

  private fun appendToFile(fileManager: NSFileManager, path: String, content: String) {
    val file = fopen(path, "ab")
    if (file == null) {
      IosUnifiedLog.logError(TAG, "Open log file failed: $path")
      return
    }
    try {
      val bytes = content.encodeToByteArray()
      bytes.usePinned { pinned ->
        fwrite(pinned.addressOf(0), 1uL, bytes.size.toULong(), file)
      }
    } finally {
      fclose(file)
    }
  }

  private fun deleteExpiredLogFiles(fileManager: NSFileManager, folderPath: String) {
    val files = fileManager.contentsOfDirectoryAtPath(folderPath, error = null) ?: return
    if (files.isEmpty()) return
    val nowSeconds = NSDate().timeIntervalSinceReferenceDate
    val maxTimeSeconds = maxTime / 1000.0

    for (item in files) {
      val name = item as? String ?: continue
      val fullPath = "$folderPath/$name"
      val attrs = fileManager.attributesOfItemAtPath(fullPath, error = null) ?: continue
      val fileType = attrs[NSFileType] as? String
      val modDate = attrs[NSFileModificationDate] as? NSDate ?: continue
      val isExpired = (modDate.timeIntervalSinceReferenceDate + maxTimeSeconds) < nowSeconds
      if (isExpired) {
        fileManager.removeItemAtPath(fullPath, error = null)
      } else if (fileType == NSFileTypeDirectory) {
        deleteExpiredLogFiles(fileManager, fullPath)
      }
    }
  }

  private fun messageSize(message: String): Int = message.length

  class Builder {
    internal lateinit var directory: String
    internal var fileGenerator: LogFileGenerator = DefaultLogFileGenerator()
    internal var maxSize: Long = 1024L * 1024L * 20L // 20MB
    internal var maxTime: Long = 1000L * 60L * 60L * 24L * 7L // 7 days
    internal var maxSizeResolver: LogFileMaxSizeResolver = FixedLogFileMaxSizeResolver()
    internal var bufferMaxSize: Int = 10 * 1024

    /**
     * Set the directory where the log file is stored.
     *
     * @param directory the directory path where the log file is stored.
     */
    fun logFileDirectory(directory: String) = apply { this.directory = directory }

    /**
     * Sets the log file generator. Default use [DefaultLogFileGenerator].
     *
     * @param fileGenerator the log file generator.
     * @see DefaultLogFileGenerator
     */
    fun logFileGenerator(fileGenerator: LogFileGenerator) =
      apply { this.fileGenerator = fileGenerator }

    /**
     * Sets the max size of each log file. If it exceeds this size, a new log file
     * will be created and subsequent messages are written to the new log file.
     *
     * Default is 20MB.
     *
     * @param maxSize The max size of each log file(unit: byte).
     */
    fun logFileMaxSize(maxSize: Long) = apply { this.maxSize = maxSize }

    /**
     * Sets the max size resolver to adjust the max size dynamically.
     *
     * @param resolver The resolver that decides the effective max size.
     */
    fun logFileMaxSizeResolver(resolver: LogFileMaxSizeResolver) =
      apply { this.maxSizeResolver = resolver }

    /**
     * Sets the max time of each log file can keep, after that time it will be deleted.
     *
     * Default is 7 days.
     *
     * @param maxTime The max time of each log file can keep(unit: millisecond).
     */
    fun logFileMaxTime(maxTime: Long) = apply { this.maxTime = maxTime }

    /**
     * Sets the buffer max size before writing to disk.
     *
     * Default is 10K chars.
     *
     * @param bufferMaxSize The max buffer size(unit: char).
     */
    fun logBufferMaxSize(bufferMaxSize: Int) = apply { this.bufferMaxSize = bufferMaxSize }

    /**
     * Create the [DiskLogStrategy] instance.
     */
    fun build(): DiskLogStrategy {
      check(::directory.isInitialized) { "log directory must be set." }
      check(directory.isNotEmpty()) { "log directory can not be empty." }
      check(bufferMaxSize > 0) { "log buffer max size must be greater than 0." }
      return DiskLogStrategy(this)
    }
  }

  private data class BufferedLog(
    val priority: LogPriority,
    val tag: String,
    val message: String,
    val size: Int,
  )
}
