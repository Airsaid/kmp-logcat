package com.airsaid.logcat

import platform.Foundation.NSThread

/**
 * Draws borders around the given log message along with additional information such as:
 *
 * - Thread information
 * - Call stack symbols
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

    val bytes = message.encodeToByteArray()
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
      logChunk(priority, tag, bytes.decodeToString(i, i + count))
      i += CHUNK_SIZE
    }
    logBottomBorder(priority, tag)
  }

  private fun logTopBorder(priority: LogPriority, tag: String) {
    logChunk(priority, tag, TOP_BORDER)
  }

  private fun logHeaderContent(priority: LogPriority, tag: String, methodCount: Int) {
    if (showThreadInfo) {
      logChunk(priority, tag, "$HORIZONTAL_LINE Thread: ${currentThreadName()}")
      logDivider(priority, tag)
    }

    val trace = NSThread.callStackSymbols
    if (trace.isEmpty()) return
    val stackOffset = methodOffset.coerceAtLeast(0)
    val maxIndex = (stackOffset + methodCount).coerceAtMost(trace.size)

    var level = ""
    for (i in (maxIndex - 1) downTo stackOffset) {
      val line = trace[i] as? String ?: continue
      logChunk(priority, tag, "$HORIZONTAL_LINE $level$line")
      level += SEPARATOR
    }
  }

  private fun currentThreadName(): String {
    val thread = NSThread.currentThread
    val name = thread.name
    if (name != null && name.isNotEmpty()) return name
    return if (thread.isMainThread) "main" else "background"
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

  companion object {
    private const val CHUNK_SIZE = 4000
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
