package com.airsaid.logcat

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

  private class CountingLogger : LogcatLogger {
    override fun log(priority: LogPriority, tag: String, message: String) = Unit
  }
}
