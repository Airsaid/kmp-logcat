package com.airsaid.logcat

import com.airsaid.logcat.AndroidLogcatLogger.Companion.install
import com.airsaid.logcat.LogPriority.DEBUG

/**
 * A [logcat] logger that delegates to [AndroidLogcatLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 *
 * Handles special cases for [LogPriority.ASSERT] (which requires sending to Log.wtf) and
 * splitting logs to be at most 4000 characters per line (otherwise logcat just truncates).
 *
 * Call [install] to make sure you never log in release builds.
 *
 * @author airsaid
 */
class AndroidLogcatLogger(
  minPriority: LogPriority = DEBUG,
  private val formatStrategy: FormatStrategy<AndroidLogcatLogStrategy>,
) : LogcatLogger {

  private val minPriorityInt: Int = minPriority.priorityInt

  override fun isLoggable(priority: LogPriority) = priority.priorityInt >= minPriorityInt

  override fun log(priority: LogPriority, tag: String, message: String) {
    formatStrategy.log(priority, tag, message)
  }

  companion object {
    fun install(
      minPriority: LogPriority = DEBUG,
      formatStrategy: FormatStrategy<AndroidLogcatLogStrategy>,
    ) {
      val androidLogcatLogger = AndroidLogcatLogger(minPriority, formatStrategy)
      if (!LogcatLogger.isInstalled(androidLogcatLogger)) {
        LogcatLogger.install(androidLogcatLogger)
      }
    }
  }
}
