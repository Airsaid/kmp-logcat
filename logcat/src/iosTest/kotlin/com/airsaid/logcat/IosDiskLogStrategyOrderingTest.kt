@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_t
import platform.darwin.dispatch_semaphore_wait
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import platform.posix.usleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosDiskLogStrategyOrderingTest {

  @Test
  fun preservesLogOrderAndFlushesPreviouslyAcceptedLogs() {
    val directory = temporaryDirectory("order")
    val logFilePath = "$directory/test.log"
    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(directory)
      .logFileGenerator(FixedLogFileGenerator(logFilePath))
      .logBufferMaxSize(17)
      .build()
    val messages = List(300) { index -> "${index.toString().padStart(4, '0')}\n" }

    try {
      messages.forEach { message ->
        strategy.log(LogPriority.INFO, "Test", message)
      }
      strategy.flush()

      assertEquals(messages.joinToString(separator = ""), readFile(logFilePath))
    } finally {
      strategy.close()
    }
  }

  @Test
  fun logReturnsWhileThresholdDiskWorkIsBlocked() {
    val directory = temporaryDirectory("async")
    val logFilePath = "$directory/test.log"
    val generator = BlockingLogFileGenerator(logFilePath)
    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(directory)
      .logFileGenerator(generator)
      .logBufferMaxSize(1)
      .build()
    val callerQueue = dispatch_queue_create("com.airsaid.logcat.test.caller", null)
    val logReturned = dispatch_semaphore_create(0)

    dispatch_async(callerQueue) {
      strategy.log(LogPriority.INFO, "Test", "async-write\n")
      dispatch_semaphore_signal(logReturned)
    }

    val diskWorkStarted = waitForSemaphore(generator.entered)
    val returnedBeforeRelease = waitForSemaphore(logReturned, timeoutMs = 1_000)
    dispatch_semaphore_signal(generator.release)
    if (!returnedBeforeRelease) {
      assertTrue(waitForSemaphore(logReturned))
    }

    try {
      assertTrue(diskWorkStarted)
      assertTrue(returnedBeforeRelease)
      strategy.flush()
      assertEquals("async-write\n", readFile(logFilePath))
    } finally {
      dispatch_semaphore_signal(generator.release)
      strategy.close()
    }
  }

  @Test
  fun concurrentCloseCallsWaitForTheSameFinalFlush() {
    val directory = temporaryDirectory("close")
    val logFilePath = "$directory/test.log"
    val generator = BlockingLogFileGenerator(logFilePath)
    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(directory)
      .logFileGenerator(generator)
      .logBufferMaxSize(1024)
      .build()
    val firstQueue = dispatch_queue_create("com.airsaid.logcat.test.close.first", null)
    val secondQueue = dispatch_queue_create("com.airsaid.logcat.test.close.second", null)
    val firstReturned = dispatch_semaphore_create(0)
    val secondReturned = dispatch_semaphore_create(0)

    strategy.log(LogPriority.INFO, "Test", "before-close\n")
    dispatch_async(firstQueue) {
      strategy.close()
      dispatch_semaphore_signal(firstReturned)
    }

    val diskWorkStarted = waitForSemaphore(generator.entered)
    if (!diskWorkStarted) {
      dispatch_semaphore_signal(generator.release)
    }
    assertTrue(diskWorkStarted)
    dispatch_async(secondQueue) {
      strategy.close()
      dispatch_semaphore_signal(secondReturned)
    }

    val secondWasWaiting = !waitForSemaphore(secondReturned, timeoutMs = 250)
    dispatch_semaphore_signal(generator.release)

    assertTrue(waitForSemaphore(firstReturned))
    if (secondWasWaiting) {
      assertTrue(waitForSemaphore(secondReturned))
    }
    assertTrue(secondWasWaiting)

    strategy.log(LogPriority.INFO, "Test", "after-close\n")
    strategy.flush()
    assertEquals("before-close\n", readFile(logFilePath))
  }

  private fun waitForSemaphore(
    semaphore: dispatch_semaphore_t,
    timeoutMs: Int = 2_000,
  ): Boolean {
    repeat(timeoutMs / POLL_INTERVAL_MS) {
      if (dispatch_semaphore_wait(semaphore, DISPATCH_TIME_NOW) == 0L) return true
      usleep((POLL_INTERVAL_MS * 1_000).toUInt())
    }
    return false
  }

  private fun temporaryDirectory(suffix: String): String =
    "${NSTemporaryDirectory()}/kmp-logcat-${NSUUID.UUID().UUIDString}-$suffix"

  private fun readFile(path: String): String {
    val file = fopen(path, "rb") ?: return ""
    return try {
      fseek(file, 0, SEEK_END)
      val size = ftell(file).toInt()
      rewind(file)

      val bytes = ByteArray(size)
      bytes.usePinned { pinned ->
        fread(pinned.addressOf(0), 1uL, size.toULong(), file)
      }
      bytes.decodeToString()
    } finally {
      fclose(file)
    }
  }

  private open class FixedLogFileGenerator(
    private val logFilePath: String,
  ) : LogFileGenerator {
    override fun generateLogFile(
      priority: LogPriority,
      tag: String,
      message: String,
      logFolder: String,
      maxSize: Long,
    ): String = logFilePath
  }

  private class BlockingLogFileGenerator(
    logFilePath: String,
  ) : FixedLogFileGenerator(logFilePath) {
    val entered = dispatch_semaphore_create(0)
    val release = dispatch_semaphore_create(0)

    override fun generateLogFile(
      priority: LogPriority,
      tag: String,
      message: String,
      logFolder: String,
      maxSize: Long,
    ): String {
      dispatch_semaphore_signal(entered)
      dispatch_semaphore_wait(release, platform.darwin.DISPATCH_TIME_FOREVER)
      return super.generateLogFile(priority, tag, message, logFolder, maxSize)
    }
  }

  private companion object {
    private const val POLL_INTERVAL_MS = 10
  }
}
