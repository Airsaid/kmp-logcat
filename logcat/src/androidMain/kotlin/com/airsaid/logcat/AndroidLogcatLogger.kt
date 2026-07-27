package com.airsaid.logcat

import android.app.Application
import android.content.pm.ApplicationInfo
import com.airsaid.logcat.AndroidLogcatLogger.Companion.install
import com.airsaid.logcat.LogPriority.DEBUG
import com.airsaid.logcat.internal.isDebuggableApp

/**
 * A [logcat] logger that delegates to [AndroidLogcatLogStrategy] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 *
 * Handles special cases for [LogPriority.ASSERT] (which requires sending to Log.wtf) and
 * splitting logs to be at most 4000 characters per line (otherwise logcat just truncates).
 *
 * Call [installOnDebuggableApp] to install this logger only for debuggable applications. [install]
 * always installs the logger and should be used only when logging in non-debuggable builds is
 * intentional.
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
    /**
     * Installs an [AndroidLogcatLogger] only when [application] is debuggable.
     *
     * @param application the application used to check [ApplicationInfo.FLAG_DEBUGGABLE].
     * @param minPriority the minimum priority to log.
     * @param formatStrategy the strategy used to format and write log messages.
     */
    fun installOnDebuggableApp(
      application: Application,
      minPriority: LogPriority = DEBUG,
      formatStrategy: FormatStrategy<AndroidLogcatLogStrategy>,
    ) {
      if (!application.isDebuggableApp) return
      install(minPriority, formatStrategy)
    }

    /**
     * Installs an [AndroidLogcatLogger] in all build types.
     *
     * Prefer [installOnDebuggableApp] when logcat output should be disabled for non-debuggable
     * applications.
     *
     * @param minPriority the minimum priority to log.
     * @param formatStrategy the strategy used to format and write log messages.
     */
    fun install(
      minPriority: LogPriority = DEBUG,
      formatStrategy: FormatStrategy<AndroidLogcatLogStrategy>,
    ) {
      LogcatLogger.installIfAbsent(installationKey) {
        AndroidLogcatLogger(minPriority, formatStrategy)
      }
    }

    /**
     * Installs an [AndroidLogcatLogger] in all build types, creating its format strategy only when
     * no logger has already been installed through this convenience API.
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
      formatStrategyFactory: () -> FormatStrategy<AndroidLogcatLogStrategy>,
    ): AndroidLogcatLogger = LogcatLogger.installIfAbsent(installationKey) {
      AndroidLogcatLogger(minPriority, formatStrategyFactory())
    }

    /**
     * Installs an [AndroidLogcatLogger] only when [application] is debuggable, creating its format
     * strategy only for the first successful installation.
     *
     * @return the active convenience-installed logger, or `null` when the app is not debuggable.
     */
    fun installOnDebuggableApp(
      application: Application,
      minPriority: LogPriority = DEBUG,
      formatStrategyFactory: () -> FormatStrategy<AndroidLogcatLogStrategy>,
    ): AndroidLogcatLogger? {
      if (!application.isDebuggableApp) return null
      return install(minPriority, formatStrategyFactory)
    }

    private val installationKey = LoggerInstallationKey<AndroidLogcatLogger>()
  }
}
