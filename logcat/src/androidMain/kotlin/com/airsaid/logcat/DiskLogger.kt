package com.airsaid.logcat

import java.util.concurrent.CountDownLatch

/**
 * A [logcat] logger that delegates to [DiskLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 *
 * @author airsaid
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
  private val completion = CountDownLatch(1)

  actual fun await() {
    completion.await()
  }

  actual fun complete() {
    completion.countDown()
  }
}
