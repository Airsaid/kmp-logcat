package com.airsaid.logcat

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class LogcatLoggerInstallIfAbsentConcurrencyTest {

  @BeforeTest
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @AfterTest
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun concurrentCallsWithSameKeyInvokeFactoryOnlyOnce() {
    val workerCount = 16
    val key = LoggerInstallationKey<CountingLogger>()
    val factoryCallCount = AtomicInteger()
    val ready = CountDownLatch(workerCount)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(workerCount)

    try {
      val results = List(workerCount) {
        executor.submit<CountingLogger> {
          ready.countDown()
          start.await()
          LogcatLogger.installIfAbsent(key) {
            factoryCallCount.incrementAndGet()
            CountingLogger()
          }
        }
      }

      assertTrue(ready.await(5, TimeUnit.SECONDS))
      start.countDown()

      val installedLoggers = results.map { it.get(5, TimeUnit.SECONDS) }
      val firstLogger = installedLoggers.first()

      assertEquals(1, factoryCallCount.get())
      assertTrue(installedLoggers.all { it === firstLogger })
      assertEquals(1, LogcatLogger.loggerArray.count { it === firstLogger })
    } finally {
      start.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun sameKeyInstallWaitsForConcurrentUninstallToFinishClosing() {
    assertSameKeyInstallWaitsForClose { logger -> LogcatLogger.uninstall(logger) }
  }

  @Test
  fun sameKeyInstallWaitsForConcurrentDirectCloseToFinishClosing() {
    assertSameKeyInstallWaitsForClose { logger -> logger.close() }
  }

  @Test
  fun sameKeyInstallWaitsForUninstallAllToFinishClosing() {
    assertSameKeyInstallWaitsForClose { LogcatLogger.uninstallAll() }
  }

  @Test
  fun concurrentDirectCloseCallsReleaseResourcesOnlyOnce() {
    val closeStarted = CountDownLatch(1)
    val allowCloseToFinish = CountDownLatch(1)
    val secondCloseStarted = CountDownLatch(1)
    val logger = BlockingCloseableLogger(closeStarted, allowCloseToFinish)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val firstClose = executor.submit { logger.close() }
      assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
      val secondClose = executor.submit {
        secondCloseStarted.countDown()
        logger.close()
      }
      assertTrue(secondCloseStarted.await(5, TimeUnit.SECONDS))

      allowCloseToFinish.countDown()

      firstClose.get(5, TimeUnit.SECONDS)
      secondClose.get(5, TimeUnit.SECONDS)
      assertEquals(1, logger.closeCount.get())
    } finally {
      allowCloseToFinish.countDown()
      executor.shutdownNow()
    }
  }

  private fun assertSameKeyInstallWaitsForClose(
    close: (BlockingCloseableLogger) -> Unit,
  ) {
    val key = LoggerInstallationKey<BlockingCloseableLogger>()
    val closeStarted = CountDownLatch(1)
    val allowCloseToFinish = CountDownLatch(1)
    val reinstallStarted = CountDownLatch(1)
    val factoryCalled = CountDownLatch(1)
    val first = LogcatLogger.installIfAbsent(key) {
      BlockingCloseableLogger(closeStarted, allowCloseToFinish)
    }
    val executor = Executors.newFixedThreadPool(2)

    try {
      val closeFuture = executor.submit { close(first) }
      assertTrue(closeStarted.await(5, TimeUnit.SECONDS))

      val reinstallFuture = executor.submit<BlockingCloseableLogger> {
        reinstallStarted.countDown()
        LogcatLogger.installIfAbsent(key) {
          factoryCalled.countDown()
          BlockingCloseableLogger(CountDownLatch(0), CountDownLatch(0))
        }
      }
      assertTrue(reinstallStarted.await(5, TimeUnit.SECONDS))
      assertFalse(factoryCalled.await(200, TimeUnit.MILLISECONDS))
      assertFalse(reinstallFuture.isDone)

      allowCloseToFinish.countDown()

      closeFuture.get(5, TimeUnit.SECONDS)
      val second = reinstallFuture.get(5, TimeUnit.SECONDS)
      assertNotSame(first, second)
      assertEquals(1, first.closeCount.get())
      assertEquals(1, LogcatLogger.loggerArray.count { it === second })
    } finally {
      allowCloseToFinish.countDown()
      executor.shutdownNow()
    }
  }

  private class CountingLogger : LogcatLogger {
    override fun log(priority: LogPriority, tag: String, message: String) = Unit
  }

  private class BlockingCloseableLogger(
    private val closeStarted: CountDownLatch,
    private val allowCloseToFinish: CountDownLatch,
  ) : CloseableLogcatLogger() {
    val closeCount = AtomicInteger()

    override fun log(priority: LogPriority, tag: String, message: String) = Unit

    override fun closeResources() {
      closeCount.incrementAndGet()
      closeStarted.countDown()
      assertTrue(allowCloseToFinish.await(5, TimeUnit.SECONDS))
    }
  }
}
