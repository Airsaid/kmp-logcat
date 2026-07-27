package com.airsaid.logcat

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskLogStrategyOrderingTest {

  private val strategies = mutableListOf<DiskLogStrategy>()
  private val directories = mutableListOf<File>()

  @AfterTest
  fun tearDown() {
    strategies.forEach { it.close() }
    directories.forEach { it.deleteRecursively() }
  }

  @Test
  fun preservesLogOrderAndFlushesPreviouslyAcceptedLogs() {
    val directory = createTempDirectory("logcat-order")
    val logFile = File(directory, "test.log")
    val strategy = track(
      DiskLogStrategy.Builder()
        .logFileDirectory(directory.absolutePath)
        .logFileGenerator(FixedLogFileGenerator(logFile))
        .logBufferMaxSize(17)
        .build(),
    )
    val messages = List(500) { index -> "${index.toString().padStart(4, '0')}\n" }

    messages.forEach { message ->
      strategy.log(LogPriority.INFO, "Test", message)
    }
    strategy.flush()

    assertEquals(messages.joinToString(separator = ""), logFile.readText())
  }

  @Test
  fun concurrentCloseCallsWaitForTheSameFinalFlush() {
    val directory = createTempDirectory("logcat-close")
    val logFile = File(directory, "test.log")
    val generator = BlockingLogFileGenerator(logFile)
    val strategy = track(
      DiskLogStrategy.Builder()
        .logFileDirectory(directory.absolutePath)
        .logFileGenerator(generator)
        .logBufferMaxSize(1024)
        .build(),
    )
    val firstReturned = CountDownLatch(1)
    val secondReturned = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()

    strategy.log(LogPriority.INFO, "Test", "before-close\n")
    val firstThread = thread(name = "disk-log-close-first") {
      runCatching { strategy.close() }
        .onFailure { error -> failure.compareAndSet(null, error) }
      firstReturned.countDown()
    }

    val diskWorkStarted = generator.entered.await(2, TimeUnit.SECONDS)
    if (!diskWorkStarted) {
      generator.release.countDown()
    }
    assertTrue(diskWorkStarted)
    val secondThread = thread(name = "disk-log-close-second") {
      runCatching { strategy.close() }
        .onFailure { error -> failure.compareAndSet(null, error) }
      secondReturned.countDown()
    }

    assertFalse(secondReturned.await(150, TimeUnit.MILLISECONDS))
    generator.release.countDown()

    assertTrue(firstReturned.await(2, TimeUnit.SECONDS))
    assertTrue(secondReturned.await(2, TimeUnit.SECONDS))
    firstThread.join()
    secondThread.join()
    assertNull(failure.get())

    strategy.log(LogPriority.INFO, "Test", "after-close\n")
    strategy.flush()
    assertEquals("before-close\n", logFile.readText())
  }

  private fun createTempDirectory(prefix: String): File =
    Files.createTempDirectory(prefix).toFile().also(directories::add)

  private fun track(strategy: DiskLogStrategy): DiskLogStrategy =
    strategy.also(strategies::add)

  private open class FixedLogFileGenerator(
    private val logFile: File,
  ) : LogFileGenerator {
    override fun generateLogFile(
      priority: LogPriority,
      tag: String,
      message: String,
      logFolder: String,
      maxSize: Long,
    ): File {
      logFile.parentFile?.mkdirs()
      return logFile
    }
  }

  private class BlockingLogFileGenerator(
    logFile: File,
  ) : FixedLogFileGenerator(logFile) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun generateLogFile(
      priority: LogPriority,
      tag: String,
      message: String,
      logFolder: String,
      maxSize: Long,
    ): File {
      entered.countDown()
      release.await()
      return super.generateLogFile(priority, tag, message, logFolder, maxSize)
    }
  }
}
