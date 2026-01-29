package com.airsaid.logcat

import android.util.Log
import com.airsaid.logcat.internal.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The log strategy that writes the log to the disk.
 *
 * You can use [Builder] to create an instance of [DiskLogStrategy].
 *
 * For example:
 * ```
 * DiskLogStrategy.Builder()
 *  .logFileDirectory(filesDir.absolutePath + "/logcat")
 *  .logFileGenerator(DefaultLogFileGenerator())
 *  .logFileMaxSize(1024 * 1024 * 100L) // 100MB
 *  .logFileMaxTime(7 * 24 * 60 * 60 * 1000L) // 7 days
 *  .logFileMaxSizeResolver(AvailableSpaceLogFileMaxSizeResolver(context))
 *  .logBufferMaxSize(10 * 1024) // 10K chars
 *  .build()
 * ```
 *
 * @author airsaid
 */
class DiskLogStrategy private constructor(builder: Builder) : LogStrategy {

  private val singleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  private val ioScope = CoroutineScope(SupervisorJob() + singleDispatcher)

  private val directory = builder.directory
  private val fileGenerator = builder.fileGenerator
  private val maxSize = builder.maxSize
  private val maxSizeResolver = builder.maxSizeResolver
  private val maxTime = builder.maxTime
  private val bufferMaxSize = builder.bufferMaxSize

  private val writeMutex = Mutex()
  private val bufferedLogs = ArrayList<BufferedLog>()
  private var bufferedSize = 0

  init {
    installCrashHandler()
  }

  override fun log(priority: LogPriority, tag: String, message: String) {
    ioScope.launch {
      bufferAndMaybeWrite(priority, tag, message)
    }
  }

  /**
   * Flush buffered logs to disk synchronously.
   */
  internal fun flush() {
    runBlocking {
      val parentJob = ioScope.coroutineContext[Job]
      parentJob?.children?.toList()?.joinAll()
      withContext(singleDispatcher) {
        writeMutex.withLock {
          flushBufferLocked()
        }
      }
    }
  }

  private suspend fun bufferAndMaybeWrite(priority: LogPriority, tag: String, message: String) =
    withContext(Dispatchers.IO) {
      writeMutex.withLock {
        val size = messageSize(message)
        bufferedLogs.add(BufferedLog(priority, tag, message, size))
        bufferedSize += size

        if (bufferedSize >= bufferMaxSize) {
          flushBufferLocked()
        }
      }
    }

  private fun flushBufferLocked() {
    if (bufferedLogs.isEmpty()) return

    val logFolder = File(directory)
    deleteExpiredLogFiles(logFolder)

    val resolvedMaxSize = maxSizeResolver.resolveMaxSize(logFolder, maxSize)
    val bufferMap = LinkedHashMap<File, StringBuilder>()
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
        ensureLogFileExists(logFile)
        FileWriter(logFile, true).use { writer ->
          writer.append(builder)
          writer.flush()
        }
      }
    } catch (e: IOException) {
      Log.e(TAG, "Write log to disk failed.", e)
    } finally {
      bufferedLogs.clear()
      bufferedSize = 0
    }
  }

  private fun ensureLogFileExists(logFile: File) {
    if (!logFile.exists()) {
      if (!logFile.createNewFile()) {
        Log.e(TAG, "Create log file failed: ${logFile.absolutePath}")
      }
    }
  }

  private fun messageSize(message: String): Int = message.length

  private fun flushBufferOnCrash() {
    if (!writeMutex.tryLock()) return
    try {
      flushBufferLocked()
    } catch (ignored: Throwable) {
      // Ignore crash flush failures.
    } finally {
      writeMutex.unlock()
    }
  }

  private fun installCrashHandler() {
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (currentHandler is CrashFlushHandler && currentHandler.strategy === this) return
    Thread.setDefaultUncaughtExceptionHandler(CrashFlushHandler(this, currentHandler))
  }

  private fun deleteExpiredLogFiles(logFolder: File) {
    val logFiles = logFolder.listFiles()
    if (logFiles == null || logFiles.isEmpty()) return

    for (logFile in logFiles) {
      if (!logFile.exists()) continue

      val isExpired = logFile.lastModified() + maxTime < System.currentTimeMillis()
      if (isExpired) {
        if (logFile.isFile) {
          if (!logFile.delete()) {
            Log.e(TAG, "Delete expired log file failed: ${logFile.absolutePath}")
          }
        } else if (logFile.isDirectory) {
          if (!logFile.deleteRecursively()) {
            Log.e(TAG, "Delete expired log folder failed: ${logFile.absolutePath}")
          }
        }
      } else if (logFile.isDirectory) {
        deleteExpiredLogFiles(logFile)
      }
    }
  }

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

  private class CrashFlushHandler(
    val strategy: DiskLogStrategy,
    private val delegate: Thread.UncaughtExceptionHandler?
  ) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
      try {
        strategy.flushBufferOnCrash()
      } catch (ignored: Throwable) {
        // Ignore crash flush failures.
      }
      delegate?.uncaughtException(t, e)
    }
  }
}
