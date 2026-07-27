@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class,
)

package com.airsaid.logcat

import com.airsaid.logcat.internal.TAG
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
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

  private val ioQueue = dispatch_queue_create(IO_QUEUE_LABEL, null)
  private val lifecycleCondition = NSCondition()
  private val bufferedLogs = ArrayList<BufferedLog>()
  private var bufferedSize = 0
  private var state = State.OPEN

  override fun log(priority: LogPriority, tag: String, message: String) {
    lifecycleCondition.lock()
    try {
      if (state != State.OPEN) return
      val size = messageSize(message)
      dispatch_async(ioQueue) {
        runCatching {
          bufferAndMaybeWrite(BufferedLog(priority, tag, message, size))
        }.onFailure { error ->
          IosUnifiedLog.logError(TAG, "Write log to disk failed. $error")
        }
      }
    } finally {
      lifecycleCondition.unlock()
    }
  }

  /**
   * Flush buffered logs to disk synchronously.
   */
  internal fun flush() {
    val completion = dispatch_semaphore_create(0)
    var shouldWaitForFlush = false

    lifecycleCondition.lock()
    try {
      when (state) {
        State.OPEN -> {
          shouldWaitForFlush = true
          dispatch_async(ioQueue) {
            try {
              flushBuffer()
            } catch (error: Throwable) {
              IosUnifiedLog.logError(TAG, "Write log to disk failed. $error")
            } finally {
              dispatch_semaphore_signal(completion)
            }
          }
        }
        State.CLOSING -> {
          while (state == State.CLOSING) {
            lifecycleCondition.wait()
          }
        }
        State.CLOSED -> Unit
      }
    } finally {
      lifecycleCondition.unlock()
    }

    if (shouldWaitForFlush) {
      dispatch_semaphore_wait(completion, DISPATCH_TIME_FOREVER)
    }
  }

  /**
   * Flush buffered logs and stop accepting new logs.
   */
  internal fun close() {
    lifecycleCondition.lock()
    try {
      when (state) {
        State.OPEN -> {
          state = State.CLOSING
          dispatch_async(ioQueue) {
            try {
              flushBuffer()
            } catch (error: Throwable) {
              IosUnifiedLog.logError(TAG, "Write log to disk failed. $error")
            } finally {
              lifecycleCondition.lock()
              try {
                state = State.CLOSED
                lifecycleCondition.broadcast()
              } finally {
                lifecycleCondition.unlock()
              }
            }
          }
        }
        State.CLOSING -> Unit
        State.CLOSED -> return
      }

      while (state != State.CLOSED) {
        lifecycleCondition.wait()
      }
    } finally {
      lifecycleCondition.unlock()
    }
  }

  private fun bufferAndMaybeWrite(log: BufferedLog) {
    bufferedLogs.add(log)
    bufferedSize += log.size
    if (bufferedSize >= bufferMaxSize) {
      flushBuffer()
    }
  }

  private fun flushBuffer() {
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

  private enum class State {
    OPEN,
    CLOSING,
    CLOSED,
  }

  private companion object {
    private const val IO_QUEUE_LABEL = "com.airsaid.logcat.disk"
  }
}
