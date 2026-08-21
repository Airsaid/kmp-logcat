package com.airsaid.logcat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogcatLoggerInstallTest {

  @Before
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @After
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun installsSameLoggerInstanceOnlyOnce() {
    val logger = CountingLogger()

    LogcatLogger.install(logger, logger)

    val countAfterInstall = logger.count
    logcat("Test") { "message" }

    assertEquals(countAfterInstall + 1, logger.count)
  }

  @Test
  fun installsSameLoggerClassInstancesTogether() {
    val first = CountingLogger()
    val second = CountingLogger()

    LogcatLogger.install(first, second)

    logcat("Test") { "message" }

    assertEquals(1, first.count)
    assertEquals(1, second.count)
  }

  @Test
  fun installsDifferentLoggerClassesTogether() {
    val first = CountingLogger()
    val second = AnotherCountingLogger()

    LogcatLogger.install(first, second)

    logcat("Test") { "message" }

    assertEquals(1, first.count)
    assertEquals(1, second.count)
  }

  @Test
  fun uninstallDoesNotRemoveSameClassDifferentInstance() {
    val installed = CountingLogger()
    val sameClass = CountingLogger()

    LogcatLogger.install(installed)
    LogcatLogger.uninstall(sameClass)

    logcat("Test") { "message" }

    assertEquals(1, installed.count)
    assertEquals(1, sameClass.count)
  }

  @Test
  fun uninstallRemovesLoggerInstance() {
    val installed = CountingLogger()

    LogcatLogger.install(installed)
    LogcatLogger.uninstall(installed)

    logcat("Test") { "message" }

    assertEquals(0, installed.count)
  }

  @Test
  fun reinstallAfterUninstallWorks() {
    val first = CountingLogger()
    LogcatLogger.install(first)
    LogcatLogger.uninstall(first)

    val second = CountingLogger()
    LogcatLogger.install(second)

    logcat("Test") { "message" }

    assertEquals(0, first.count)
    assertEquals(1, second.count)
  }

  @Test
  fun uninstallAllClearsAllLoggers() {
    val first = CountingLogger()
    val second = AnotherCountingLogger()

    LogcatLogger.install(first, second)
    LogcatLogger.uninstallAll()

    logcat("Test") { "message" }

    assertEquals(0, first.count)
    assertEquals(0, second.count)
  }

  @Test
  fun uninstallClosesCloseableLogger() {
    val logger = CloseableCountingLogger()

    LogcatLogger.install(logger)
    LogcatLogger.uninstall(logger)

    assertEquals(1, logger.closeCount)
  }

  @Test
  fun uninstallAllClosesCloseableLoggersAndClearsAllLoggers() {
    val closeable = CloseableCountingLogger()
    val regular = CountingLogger()

    LogcatLogger.install(closeable, regular)
    LogcatLogger.uninstallAll()

    logcat("Test") { "message" }

    assertEquals(1, closeable.closeCount)
    assertEquals(0, closeable.count)
    assertEquals(0, regular.count)
  }

  private class CountingLogger : LogcatLogger {
    var count = 0

    override fun log(priority: LogPriority, tag: String, message: String) {
      count++
    }
  }

  private class AnotherCountingLogger : LogcatLogger {
    var count = 0

    override fun log(priority: LogPriority, tag: String, message: String) {
      count++
    }
  }

  private class CloseableCountingLogger : CloseableLogcatLogger() {
    var count = 0
    var closeCount = 0

    override fun log(priority: LogPriority, tag: String, message: String) {
      count++
    }

    override fun closeResources() {
      closeCount++
    }
  }
}
