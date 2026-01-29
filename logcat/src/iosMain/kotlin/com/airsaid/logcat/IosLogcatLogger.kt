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
      val iosLogcatLogger = IosLogcatLogger(minPriority, formatStrategy)
      if (!LogcatLogger.isInstalled(iosLogcatLogger)) {
        LogcatLogger.install(iosLogcatLogger)
      }
    }
  }
}
