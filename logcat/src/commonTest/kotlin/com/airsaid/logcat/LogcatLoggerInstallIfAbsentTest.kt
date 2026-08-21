package com.airsaid.logcat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class LogcatLoggerInstallIfAbsentTest {

  @BeforeTest
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @AfterTest
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun sameKeyInvokesFactoryOnlyOnce() {
    val key = LoggerInstallationKey<CountingLogger>()
    var factoryCallCount = 0

    val first = LogcatLogger.installIfAbsent(key) {
      factoryCallCount++
      CountingLogger()
    }
    val second = LogcatLogger.installIfAbsent(key) {
      factoryCallCount++
      CountingLogger()
    }

    assertSame(first, second)
    assertEquals(1, factoryCallCount)
  }

  @Test
  fun differentKeysInstallDifferentInstancesOfSameClass() {
    val first = LogcatLogger.installIfAbsent(LoggerInstallationKey<CountingLogger>()) {
      CountingLogger()
    }
    val second = LogcatLogger.installIfAbsent(LoggerInstallationKey<CountingLogger>()) {
      CountingLogger()
    }

    logcat("Test") { "message" }

    assertNotSame(first, second)
    assertEquals(1, first.count)
    assertEquals(1, second.count)
  }

  @Test
  fun uninstallReleasesKeyForReuse() {
    val key = LoggerInstallationKey<CountingLogger>()
    val first = LogcatLogger.installIfAbsent(key) { CountingLogger() }

    LogcatLogger.uninstall(first)

    val second = LogcatLogger.installIfAbsent(key) { CountingLogger() }
    logcat("Test") { "message" }

    assertNotSame(first, second)
    assertEquals(0, first.count)
    assertEquals(1, second.count)
  }

  @Test
  fun uninstallAllReleasesKeyForReuse() {
    val key = LoggerInstallationKey<CountingLogger>()
    val first = LogcatLogger.installIfAbsent(key) { CountingLogger() }

    LogcatLogger.uninstallAll()

    val second = LogcatLogger.installIfAbsent(key) { CountingLogger() }

    assertNotSame(first, second)
  }

  @Test
  fun directCloseReleasesKeyForReuse() {
    val key = LoggerInstallationKey<CloseableCountingLogger>()
    val first = LogcatLogger.installIfAbsent(key) { CloseableCountingLogger() }

    first.close()

    val second = LogcatLogger.installIfAbsent(key) { CloseableCountingLogger() }

    assertNotSame(first, second)
    assertEquals(1, first.closeCount)
    assertEquals(0, LogcatLogger.loggerArray.count { it === first })
    assertEquals(1, LogcatLogger.loggerArray.count { it === second })
  }

  @Test
  fun repeatedCloseCallsReleaseResourcesOnlyOnce() {
    val logger = CloseableCountingLogger()

    logger.close()
    logger.close()

    assertEquals(1, logger.closeCount)
  }

  @Test
  fun closeFailureStillReleasesKeyForReuse() {
    val key = LoggerInstallationKey<FailingCloseableLogger>()
    val first = LogcatLogger.installIfAbsent(key) { FailingCloseableLogger(shouldFail = true) }

    assertFailsWith<IllegalStateException> { first.close() }

    val second = LogcatLogger.installIfAbsent(key) {
      FailingCloseableLogger(shouldFail = false)
    }
    assertNotSame(first, second)
    assertEquals(1, LogcatLogger.loggerArray.count { it === second })
  }

  @Test
  fun factoryFailureDoesNotReserveKey() {
    val key = LoggerInstallationKey<CountingLogger>()

    assertFailsWith<IllegalStateException> {
      LogcatLogger.installIfAbsent(key) {
        error("factory failed")
      }
    }

    val installed = LogcatLogger.installIfAbsent(key) { CountingLogger() }
    logcat("Test") { "message" }

    assertEquals(1, installed.count)
  }

  @Test
  fun factoryReturningInstalledInstanceDoesNotDuplicateLogger() {
    val key = LoggerInstallationKey<CountingLogger>()
    val logger = CountingLogger()
    LogcatLogger.install(logger)

    val installed = LogcatLogger.installIfAbsent(key) { logger }
    logcat("Test") { "message" }

    assertSame(logger, installed)
    assertEquals(1, logger.count)
  }

  private class CountingLogger : LogcatLogger {
    var count = 0

    override fun log(priority: LogPriority, tag: String, message: String) {
      count++
    }
  }

  private class CloseableCountingLogger : CloseableLogcatLogger() {
    var closeCount = 0

    override fun log(priority: LogPriority, tag: String, message: String) = Unit

    override fun closeResources() {
      closeCount++
    }
  }

  private class FailingCloseableLogger(
    private val shouldFail: Boolean,
  ) : CloseableLogcatLogger() {
    override fun log(priority: LogPriority, tag: String, message: String) = Unit

    override fun closeResources() {
      if (shouldFail) error("close failed")
    }
  }
}
