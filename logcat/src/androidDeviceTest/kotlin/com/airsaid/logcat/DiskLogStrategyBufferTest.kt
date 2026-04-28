package com.airsaid.logcat

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskLogStrategyBufferTest {

  private val strategies = mutableListOf<DiskLogStrategy>()

  @After
  fun tearDown() {
    LogcatLogger.uninstallAll()
    strategies.forEach { it.close() }
    strategies.clear()
  }

  @Test
  fun doesNotWriteBeforeBufferThresholdReached() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-no-flush")
    logDir.deleteRecursively()

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build())

    strategy.log(LogPriority.INFO, "Test", "12345")

    Thread.sleep(300)

    assertFalse(logDir.exists())
  }

  @Test
  fun flushesBufferedLogsOnFlushCall() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-flush-call")
    logDir.deleteRecursively()

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build())
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

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(10)
      .build())

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

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(10)
      .build())

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
      val strategy = track(DiskLogStrategy.Builder()
        .logFileDirectory(logDir.absolutePath)
        .logBufferMaxSize(1024)
        .build())

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

  @Test
  fun uninstallFlushesDiskLoggerBuffer() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-uninstall")
    logDir.deleteRecursively()

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build())
    val logger = DiskLogger(
      minPriority = LogPriority.INFO,
      formatStrategy = NonFormatStrategy(strategy),
    )

    val message = "uninstall-flush-buffer"
    LogcatLogger.install(logger)
    logger.log(LogPriority.INFO, "Test", message)
    LogcatLogger.uninstall(logger)

    val content = awaitLogContent(logDir)
    assertTrue(content.contains(message))
  }

  @Test
  fun closeStopsWritingNewLogs() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-close")
    logDir.deleteRecursively()

    val strategy = track(DiskLogStrategy.Builder()
      .logFileDirectory(logDir.absolutePath)
      .logBufferMaxSize(1024)
      .build())
    val logger = DiskLogger(
      minPriority = LogPriority.INFO,
      formatStrategy = NonFormatStrategy(strategy),
    )

    logger.log(LogPriority.INFO, "Test", "before-close")
    logger.close()
    logger.log(LogPriority.INFO, "Test", "after-close")

    val content = awaitLogContent(logDir)
    assertTrue(content.contains("before-close"))
    assertFalse(content.contains("after-close"))
  }

  @Test
  fun closeRestoresCrashHandlerInstalledBeforeStrategy() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-restore-handler")
    logDir.deleteRecursively()

    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    val recordingHandler = RecordingHandler()
    Thread.setDefaultUncaughtExceptionHandler(recordingHandler)

    try {
      val strategy = track(DiskLogStrategy.Builder()
        .logFileDirectory(logDir.absolutePath)
        .logBufferMaxSize(1024)
        .build())

      strategy.close()

      assertSame(recordingHandler, Thread.getDefaultUncaughtExceptionHandler())
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }
  }

  @Test
  fun closeDoesNotOverwriteNewerCrashHandler() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val logDir = File(context.cacheDir, "logcat-buffer-keep-new-handler")
    logDir.deleteRecursively()

    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    val recordingHandler = RecordingHandler()
    val newerHandler = RecordingHandler()
    Thread.setDefaultUncaughtExceptionHandler(recordingHandler)

    try {
      val strategy = track(DiskLogStrategy.Builder()
        .logFileDirectory(logDir.absolutePath)
        .logBufferMaxSize(1024)
        .build())
      Thread.setDefaultUncaughtExceptionHandler(newerHandler)

      strategy.close()

      assertSame(newerHandler, Thread.getDefaultUncaughtExceptionHandler())
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }
  }

  @Test
  fun closeNewerCrashHandlerSkipsClosedOlderHandler() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val firstLogDir = File(context.cacheDir, "logcat-buffer-close-first-handler")
    val secondLogDir = File(context.cacheDir, "logcat-buffer-close-second-handler")
    firstLogDir.deleteRecursively()
    secondLogDir.deleteRecursively()

    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    val recordingHandler = RecordingHandler()
    Thread.setDefaultUncaughtExceptionHandler(recordingHandler)

    try {
      val firstStrategy = track(DiskLogStrategy.Builder()
        .logFileDirectory(firstLogDir.absolutePath)
        .logBufferMaxSize(1024)
        .build())
      val secondStrategy = track(DiskLogStrategy.Builder()
        .logFileDirectory(secondLogDir.absolutePath)
        .logBufferMaxSize(1024)
        .build())

      firstStrategy.close()
      secondStrategy.close()

      assertSame(recordingHandler, Thread.getDefaultUncaughtExceptionHandler())
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

  private fun awaitLogContent(directory: File): String {
    val logFiles = awaitLogFiles(directory)
    assertTrue(logFiles.isNotEmpty())
    return logFiles.joinToString(separator = "") { it.readText() }
  }

  private fun track(strategy: DiskLogStrategy): DiskLogStrategy {
    strategies += strategy
    return strategy
  }

  private class RecordingHandler : Thread.UncaughtExceptionHandler {
    var called = false

    override fun uncaughtException(t: Thread, e: Throwable) {
      called = true
    }
  }
}
