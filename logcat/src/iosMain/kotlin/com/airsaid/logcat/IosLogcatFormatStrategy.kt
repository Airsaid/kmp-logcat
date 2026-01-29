package com.airsaid.logcat

import kotlinx.datetime.Clock
import platform.Foundation.NSThread
import platform.posix.getpid

/**
 * A [logcat] format strategy that handles log message as a console format on iOS.
 */
class IosLogcatFormatStrategy<S : LogStrategy> private constructor(builder: Builder<S>) :
  FormatStrategy<S> {

  private val isShowTimeStamp = builder.isShowTimeStamp
  private val isShowProcessId = builder.isShowProcessId
  private val isShowThreadInfo = builder.isShowThreadInfo
  private val isShowTag = builder.isShowTag
  private val isShowLevel = builder.isShowLevel
  override val logStrategy: S = builder.logStrategyInstance

  override fun log(priority: LogPriority, tag: String, message: String) {
    val builder = StringBuilder().apply {
      if (isShowTimeStamp) {
        append(Clock.System.now().toString())
        append(SEPARATOR)
      }
      if (isShowProcessId) {
        append(getpid())
        append(SEPARATOR)
      }
      if (isShowThreadInfo) {
        append(currentThreadName())
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

  private fun currentThreadName(): String {
    val thread = NSThread.currentThread
    val name = thread.name
    if (name != null && name.isNotEmpty()) return name
    return if (thread.isMainThread) "main" else "background"
  }

  companion object {
    private const val SEPARATOR = "\t"
    private const val NEW_LINE = "\n"
  }

  class Builder<S : LogStrategy> {
    internal lateinit var logStrategyInstance: S
    internal var isShowTimeStamp = true
    internal var isShowProcessId = true
    internal var isShowThreadInfo = true
    internal var isShowTag = true
    internal var isShowLevel = true

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
     * Create the [IosLogcatFormatStrategy] instance.
     */
    fun build(): IosLogcatFormatStrategy<S> {
      check(::logStrategyInstance.isInitialized) { "log strategy must be set." }
      return IosLogcatFormatStrategy(this)
    }

  }
}
