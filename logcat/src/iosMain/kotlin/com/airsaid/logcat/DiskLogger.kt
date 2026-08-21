package com.airsaid.logcat

import platform.Foundation.NSCondition

/**
 * A [logcat] logger that delegates to [DiskLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 */
class DiskLogger(
  minPriority: LogPriority = LogPriority.WARN,
  private val formatStrategy: FormatStrategy<DiskLogStrategy>,
) : CloseableLogcatLogger() {

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

  override fun closeResources() {
    formatStrategy.logStrategy.close()
  }

  companion object {
    private val installationKey = LoggerInstallationKey<DiskLogger>()

    /**
     * Installs the application-wide disk logger if it has not already been installed.
     *
     * [formatStrategyFactory] is evaluated only for the first installation. Build the
     * [DiskLogStrategy] inside the factory so repeated calls do not allocate disk logging
     * resources.
     */
    fun installOnApp(
      minPriority: LogPriority = LogPriority.WARN,
      formatStrategyFactory: () -> FormatStrategy<DiskLogStrategy>,
    ): DiskLogger =
      LogcatLogger.installIfAbsent(installationKey) {
        DiskLogger(minPriority, formatStrategyFactory())
      }

  }
}

internal actual class CloseCompletion {
  private val condition = NSCondition()
  private var completed = false

  actual fun await() {
    condition.lock()
    try {
      while (!completed) {
        condition.wait()
      }
    } finally {
      condition.unlock()
    }
  }

  actual fun complete() {
    condition.lock()
    try {
      completed = true
      condition.broadcast()
    } finally {
      condition.unlock()
    }
  }
}
