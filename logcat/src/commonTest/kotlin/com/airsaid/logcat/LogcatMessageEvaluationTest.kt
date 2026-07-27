package com.airsaid.logcat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogcatMessageEvaluationTest {

  @BeforeTest
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @AfterTest
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun tagOverloadEvaluatesMessageOnceForMultipleLoggers() {
    val first = RecordingLogger()
    val second = RecordingLogger()
    LogcatLogger.install(first, second)
    var evaluationCount = 0

    logcat("Test") { "message-${++evaluationCount}" }

    assertEquals(1, evaluationCount)
    assertEquals(listOf("message-1"), first.messages)
    assertEquals(listOf("message-1"), second.messages)
  }

  @Test
  fun receiverOverloadEvaluatesMessageOnceForMultipleLoggers() {
    val first = RecordingLogger()
    val second = RecordingLogger()
    LogcatLogger.install(first, second)
    var evaluationCount = 0

    this.logcat { "message-${++evaluationCount}" }

    assertEquals(1, evaluationCount)
    assertEquals(listOf("message-1"), first.messages)
    assertEquals(listOf("message-1"), second.messages)
  }

  @Test
  fun messageIsEvaluatedOnceWhenOnlySomeLoggersAreLoggable() {
    val rejected = RecordingLogger(isLoggable = false)
    val first = RecordingLogger()
    val second = RecordingLogger()
    LogcatLogger.install(rejected, first, second)
    var evaluationCount = 0

    logcat("Test") { "message-${++evaluationCount}" }

    assertEquals(1, evaluationCount)
    assertEquals(emptyList(), rejected.messages)
    assertEquals(listOf("message-1"), first.messages)
    assertEquals(listOf("message-1"), second.messages)
  }

  @Test
  fun messageIsNotEvaluatedWhenNoLoggerIsLoggable() {
    LogcatLogger.install(RecordingLogger(isLoggable = false))
    var evaluationCount = 0

    logcat("Test") { "message-${++evaluationCount}" }

    assertEquals(0, evaluationCount)
  }

  private class RecordingLogger(
    private val isLoggable: Boolean = true,
  ) : LogcatLogger {
    val messages = mutableListOf<String>()

    override fun isLoggable(priority: LogPriority) = isLoggable

    override fun log(priority: LogPriority, tag: String, message: String) {
      messages += message
    }
  }
}
