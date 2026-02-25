package com.airsaid.logcat

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * A [logcat] format strategy that handle log message as Android logcat format.
 *
 * @author airsaid
 */
class AndroidLogcatFormatStrategy<S : LogStrategy> private constructor(builder: Builder<S>) :
  FormatStrategy<S> {

  private val isShowTimeStamp = builder.isShowTimeStamp
  private val isShowProcessId = builder.isShowProcessId
  private val isShowThreadInfo = builder.isShowThreadInfo
  private val isShowTag = builder.isShowTag
  private val isShowLevel = builder.isShowLevel
  private val timeStampFormatter = builder.timeStampFormatterBlock
  override val logStrategy: S = builder.logStrategyInstance

  override fun log(priority: LogPriority, tag: String, message: String) {
    val builder = StringBuilder().apply {
      if (isShowTimeStamp) {
        val now = Clock.System.now()
        append(TimestampFormatter.formatWithFallback(now, timeStampFormatter))
        append(SEPARATOR)
      }
      if (isShowProcessId) {
        append(android.os.Process.myPid())
        append(SEPARATOR)
      }
      if (isShowThreadInfo) {
        append(Thread.currentThread().name)
        append(SEPARATOR)
      }
      if (isShowTag) {
        append(tag)
        append(SEPARATOR)
      }
      if (isShowLevel) {
        append(priority.priorityLetter)
        append(SEPARATOR)
      }
      append(message)
      append(NEW_LINE)
    }
    logStrategy.log(priority, tag, builder.toString())
  }

  companion object {
    private const val SEPARATOR = "\t"
    private val NEW_LINE = System.lineSeparator()
  }

  class Builder<S : LogStrategy> {
    internal lateinit var logStrategyInstance: S
    internal var isShowTimeStamp = true
    internal var isShowProcessId = true
    internal var isShowThreadInfo = true
    internal var isShowTag = true
    internal var isShowLevel = true
    internal var timeStampFormatterBlock: (Instant) -> String =
      TimestampFormatter::defaultFormatter

    /**
     * Sets the log strategy to use to determine how to handler the formatted log message.
     *
     * @param logStrategy the log strategy to use.
     */
    fun logStrategy(logStrategy: S) = apply { this.logStrategyInstance = logStrategy }

    /**
     * Sets whether to show the time stamp in the log message.
     *
     * Default is `true`.
     *
     * @param isShowTimeStamp `true` to show the time stamp, `false` otherwise.
     */
    fun showTimeStamp(isShowTimeStamp: Boolean) = apply { this.isShowTimeStamp = isShowTimeStamp }

    /**
     * Sets the timestamp format pattern.
     *
     * The pattern follows Unicode date pattern format.
     * Example: `uuuu-MM-dd HH:mm:ss.SSS`.
     *
     * Default timezone is [TimeZone.currentSystemDefault].
     */
    fun timeStampPattern(
      pattern: String,
      timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ) = apply {
      this.timeStampFormatterBlock = TimestampFormatter.patternFormatter(pattern, timeZone)
    }

    /**
     * Sets a custom timestamp formatter.
     *
     * Default is [Instant.toString].
     */
    fun timeStampFormatter(timeStampFormatter: (Instant) -> String) = apply {
      this.timeStampFormatterBlock = timeStampFormatter
    }

    /**
     * Sets whether to show the process id in the log message.
     *
     * Default is `true`.
     *
     * @param isShowProcessId `true` to show the process id, `false` otherwise.
     */
    fun showProcessId(isShowProcessId: Boolean) = apply { this.isShowProcessId = isShowProcessId }

    /**
     * Sets whether to show the thread info in the log message.
     *
     * Default is `true`.
     *
     * @param isShowThreadInfo `true` to show the thread info, `false` otherwise.
     */
    fun showThreadInfo(isShowThreadInfo: Boolean) =
      apply { this.isShowThreadInfo = isShowThreadInfo }

    /**
     * Sets whether to show the tag in the log message.
     *
     * Default is `true`.
     *
     * @param isShowTag `true` to show the tag, `false` otherwise.
     */
    fun showTag(isShowTag: Boolean) = apply { this.isShowTag = isShowTag }

    /**
     * Sets whether to show the log level in the log message.
     *
     * Default is `true`.
     *
     * @param isShowLevel `true` to show the log level, `false` otherwise.
     */
    fun showLevel(isShowLevel: Boolean) = apply { this.isShowLevel = isShowLevel }

    /**
     * Create the [AndroidLogcatFormatStrategy] instance.
     */
    fun build(): AndroidLogcatFormatStrategy<S> {
      check(::logStrategyInstance.isInitialized) { "log strategy must be set." }
      return AndroidLogcatFormatStrategy(this)
    }

  }
}
