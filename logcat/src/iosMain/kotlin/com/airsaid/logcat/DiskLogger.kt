package com.airsaid.logcat

/**
 * A [logcat] logger that delegates to [DiskLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 */
class DiskLogger(
  minPriority: LogPriority = LogPriority.WARN,
  private val formatStrategy: FormatStrategy<DiskLogStrategy>,
) : LogcatLogger {

  private val minPriorityInt: Int = minPriority.priorityInt

  override fun isLoggable(priority: LogPriority) = priority.priorityInt >= minPriorityInt

  override fun log(priority: LogPriority, tag: String, message: String) {
    formatStrategy.log(priority, tag, message)
  }

  /**
   * Flush buffered logs to disk synchronously.
   */
  fun flush() {
    formatStrategy.logStrategy.flush()
  }

  companion object {
    fun installOnApp(
      minPriority: LogPriority = LogPriority.WARN,
      formatStrategy: FormatStrategy<DiskLogStrategy>
    ) {
      val iosDiskLogger = DiskLogger(minPriority, formatStrategy)
      if (!LogcatLogger.isInstalled(iosDiskLogger)) {
        LogcatLogger.install(iosDiskLogger)
      }
    }
  }
}
