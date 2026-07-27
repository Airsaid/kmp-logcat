package com.airsaid.logcat

import com.airsaid.logcat.IosLogcatLogger.Companion.install
import com.airsaid.logcat.LogPriority.DEBUG

/**
 * A [logcat] logger that delegates to [IosLogcatLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 */
class IosLogcatLogger(
  minPriority: LogPriority = DEBUG,
  private val formatStrategy: FormatStrategy<IosLogcatLogStrategy>,
) : LogcatLogger {

  private val minPriorityInt: Int = minPriority.priorityInt

  override fun isLoggable(priority: LogPriority) = priority.priorityInt >= minPriorityInt

  override fun log(priority: LogPriority, tag: String, message: String) {
    formatStrategy.log(priority, tag, message)
  }

  companion object {
    fun install(
      minPriority: LogPriority = DEBUG,
      formatStrategy: FormatStrategy<IosLogcatLogStrategy>,
    ) {
      LogcatLogger.installIfAbsent(installationKey) {
        IosLogcatLogger(minPriority, formatStrategy)
      }
    }

    /**
     * Installs an [IosLogcatLogger], creating its format strategy only when no logger has already
     * been installed through this convenience API.
     *
     * The first successful installation remains active until it is uninstalled. Later calls return
     * that logger without evaluating [formatStrategyFactory].
     *
     * @param minPriority the minimum priority to log.
     * @param formatStrategyFactory creates the strategy for the first installation.
     * @return the newly installed logger, or the logger installed by an earlier convenience call.
     */
    fun install(
      minPriority: LogPriority = DEBUG,
      formatStrategyFactory: () -> FormatStrategy<IosLogcatLogStrategy>,
    ): IosLogcatLogger = LogcatLogger.installIfAbsent(installationKey) {
      IosLogcatLogger(minPriority, formatStrategyFactory())
    }

    private val installationKey = LoggerInstallationKey<IosLogcatLogger>()
  }
}
