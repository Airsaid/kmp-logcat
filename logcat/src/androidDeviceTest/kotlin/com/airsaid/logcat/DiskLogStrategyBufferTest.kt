package com.airsaid.logcat

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskLogStrategyBufferTest {

  @Test
  fun doesNotWriteBeforeBufferThresholdReached() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-no-flush")
    logDir.deleteRecursively()

    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build()

    strategy.log(LogPriority.INFO, "Test", "12345")

    Thread.sleep(300)

    assertFalse(logDir.exists())
  }

  @Test
  fun flushesBufferedLogsOnFlushCall() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-flush-call")
    logDir.deleteRecursively()

    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build()
    val logger = DiskLogger(
      minPriority = LogPriority.INFO,
      formatStrategy = NonFormatStrategy(strategy),
    )

    val message = "flush-buffer"
    logger.log(LogPriority.INFO, "Test", message)
    logger.flush()

    val logFiles = awaitLogFiles(logDir)
    assertTrue(logFiles.isNotEmpty())

    val content = logFiles.joinToString(separator = "") { it.readText() }
    assertTrue(content.contains(message))
  }

  @Test
  fun flushesWhenBufferExceedsThreshold() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-flush")
    logDir.deleteRecursively()

    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(10)
      .build()

    strategy.log(LogPriority.INFO, "Test", "123456")
    strategy.log(LogPriority.INFO, "Test", "abcdef")

    val logFiles = awaitLogFiles(logDir)
    assertTrue(logFiles.isNotEmpty())

    val content = logFiles.joinToString(separator = "") { it.readText() }
    assertTrue(content.contains("123456"))
    assertTrue(content.contains("abcdef"))
  }

  @Test
  fun flushesLargeMessageImmediately() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-large")
    logDir.deleteRecursively()

    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(10)
      .build()

    val message = "1234567890abcdef"
    strategy.log(LogPriority.INFO, "Test", message)

    val logFiles = awaitLogFiles(logDir)
    assertTrue(logFiles.isNotEmpty())

    val content = logFiles.joinToString(separator = "") { it.readText() }
    assertTrue(content.contains(message))
  }

  @Test
  fun flushesBufferOnCrash() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-crash")
    logDir.deleteRecursively()

    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    val recordingHandler = RecordingHandler()
    Thread.setDefaultUncaughtExceptionHandler(recordingHandler)

    try {
      val strategy = DiskLogStrategy.Builder()
        .logFileDirectory(logDir.absolutePath)
        .logBufferMaxSize(1024)
        .build()

      strategy.log(LogPriority.INFO, "Test", "crash-buffer")
      Thread.sleep(300)

      val handler = Thread.getDefaultUncaughtExceptionHandler()
      handler?.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

      val logFiles = awaitLogFiles(logDir)
      assertTrue(logFiles.isNotEmpty())
      assertTrue(recordingHandler.called)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }
  }

  private fun awaitLogFiles(directory: File, timeoutMs: Long = 2000L): List<File> {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val files = directory.walkTopDown().filter { it.isFile }.toList()
      if (files.isNotEmpty()) return files
      Thread.sleep(50)
    }
    return emptyList()
  }

  private class RecordingHandler : Thread.UncaughtExceptionHandler {
    var called = false

    override fun uncaughtException(t: Thread, e: Throwable) {
      called = true
    }
  }
}
