package com.airsaid.logcat

/**
 * Draws borders around the given log message along with additional information such as:
 *
 * - Thread information
 * - Method stack trace
 *
 * ```
 *  ┌──────────────────────────
 *  │ Method stack history
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 *  │ Thread information
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 *  │ Log message
 *  └──────────────────────────
 * ```
 *
 * If the log message is very long, borders will not be drawn, making it easier to copy:
 * ```
 *  ┌──────────────────────────
 *  │ Method stack history
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 *  │ Thread information
 *  ├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
 * Log message of the 1 line length
 * Log message of the 2 line length
 * Log message of the 3 line length
 *  └──────────────────────────
 * ```
 *
 * Customize:
 * ```
 * val formatStrategy = PrettyFormatStrategy.Builder<LogStrategy>()
 *     .logStrategy(customLog) // Changes the log strategy to print out
 *     .showThreadInfo(false)  // (Optional) Whether to show thread info or not. Default true
 *     .methodCount(0)         // (Optional) How many method line to show. Default 2
 *     .methodOffset(7)        // (Optional) Hides internal method calls up to offset. Default 5
 *     .build()
 * ```
 *
 * @author airsaid
 */
class PrettyFormatStrategy<S : LogStrategy> private constructor(builder: Builder<S>) :
  FormatStrategy<S> {

  private val methodCount = builder.methodCount
  private val methodOffset = builder.methodOffset
  private val showThreadInfo = builder.showThreadInfo
  override val logStrategy: S = builder.logStrategyInstance

  override fun log(priority: LogPriority, tag: String, message: String) {
    logTopBorder(priority, tag)
    logHeaderContent(priority, tag, methodCount)

    val bytes = message.toByteArray()
    val length = bytes.size
    if (length <= CHUNK_SIZE) {
      if (methodCount > 0) {
        logDivider(priority, tag)
      }
      logContent(priority, tag, message)
      logBottomBorder(priority, tag)
      return
    }
    if (methodCount > 0) {
      logDivider(priority, tag)
    }
    var i = 0
    while (i < length) {
      val count = minOf(length - i, CHUNK_SIZE)
      logChunk(priority, tag, String(bytes, i, count))
      i += CHUNK_SIZE
    }
    logBottomBorder(priority, tag)
  }

  private fun logTopBorder(priority: LogPriority, tag: String) {
    logChunk(priority, tag, TOP_BORDER)
  }

  private fun logHeaderContent(priority: LogPriority, tag: String, methodCount: Int) {
    val trace = Thread.currentThread().stackTrace
    if (showThreadInfo) {
      logChunk(priority, tag, "$HORIZONTAL_LINE Thread: ${Thread.currentThread().name}")
      logDivider(priority, tag)
    }
    var localMethodCount = methodCount
    val stackOffset = getStackOffset(trace) + methodOffset

    if (localMethodCount + stackOffset > trace.size) {
      localMethodCount = trace.size - stackOffset - 1
    }

    var level = ""
    for (i in localMethodCount downTo 1) {
      val stackIndex = i + stackOffset
      if (stackIndex >= trace.size) {
        continue
      }
      val builder = StringBuilder()
      builder.append(HORIZONTAL_LINE)
        .append(' ')
        .append(level)
        .append(getSimpleClassName(trace[stackIndex].className))
        .append(".")
        .append(trace[stackIndex].methodName)
        .append(" ")
        .append(" (")
        .append(trace[stackIndex].fileName)
        .append(":")
        .append(trace[stackIndex].lineNumber)
        .append(")")
      level += SEPARATOR
      logChunk(priority, tag, builder.toString())
    }
  }

  private fun logBottomBorder(priority: LogPriority, tag: String) {
    logChunk(priority, tag, BOTTOM_BORDER)
  }

  private fun logDivider(priority: LogPriority, tag: String) {
    logChunk(priority, tag, MIDDLE_BORDER)
  }

  private fun logContent(priority: LogPriority, tag: String, chunk: String) {
    logChunk(priority, tag, "$HORIZONTAL_LINE $chunk")
  }

  private fun logChunk(priority: LogPriority, tag: String, chunk: String) {
    logStrategy.log(priority, tag, chunk)
  }

  private fun getSimpleClassName(name: String): String {
    val lastIndex = name.lastIndexOf(".")
    return name.substring(lastIndex + 1)
  }

  private fun getStackOffset(trace: Array<StackTraceElement>): Int {
    for (i in MIN_STACK_OFFSET until trace.size) {
      val e = trace[i]
      val name = e.className
      if (name != LogcatLogger::class.java.name) {
        return i - 1
      }
    }
    return -1
  }

  companion object {
    private const val CHUNK_SIZE = 4000
    private const val MIN_STACK_OFFSET = 5
    private const val SEPARATOR = "\t"
    private const val TOP_LEFT_CORNER = '┌'
    private const val BOTTOM_LEFT_CORNER = '└'
    private const val MIDDLE_CORNER = '├'
    private const val HORIZONTAL_LINE = '│'
    private const val DOUBLE_DIVIDER = "────────────────────────────────────────────────────────"
    private const val SINGLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄"
    private const val TOP_BORDER = "$TOP_LEFT_CORNER$DOUBLE_DIVIDER$DOUBLE_DIVIDER"
    private const val BOTTOM_BORDER = "$BOTTOM_LEFT_CORNER$DOUBLE_DIVIDER$DOUBLE_DIVIDER"
    private const val MIDDLE_BORDER = "$MIDDLE_CORNER$SINGLE_DIVIDER$SINGLE_DIVIDER"
  }

  class Builder<S : LogStrategy> {
    internal lateinit var logStrategyInstance: S
    internal var methodCount = 2
    internal var methodOffset = 0
    internal var showThreadInfo = true

    /**
     * Sets the log strategy to use to determine how to handler the formatted log message.
     *
     * @param logStrategy the log strategy to use.
     */
    fun logStrategy(logStrategy: S) = apply { this.logStrategyInstance = logStrategy }

    /**
     * Sets the number of method line to show. Default value is 2.
     *
     * @param methodCount the number of method line to show.
     */
    fun methodCount(methodCount: Int) = apply { this.methodCount = methodCount }

    /**
     * Sets the number of method offset. Default value is 0.
     *
     * @param methodOffset the number of method offset.
     */
    fun methodOffset(methodOffset: Int) = apply { this.methodOffset = methodOffset }

    /**
     * Sets whether to show the thread info in the log message.
     *
     * @param showThreadInfo `true` to show the thread info, `false` otherwise.
     */
    fun showThreadInfo(showThreadInfo: Boolean) = apply { this.showThreadInfo = showThreadInfo }

    /**
     * Create the [PrettyFormatStrategy] instance.
     */
    fun build(): PrettyFormatStrategy<S> {
      check(::logStrategyInstance.isInitialized) { "log strategy must be set." }
      return PrettyFormatStrategy(this)
    }
  }
}
