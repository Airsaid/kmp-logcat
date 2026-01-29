package com.airsaid.logcat

import com.airsaid.logcat.LogPriority.ERROR
import com.airsaid.logcat.LogcatLogger.Companion.install
import com.airsaid.logcat.LogcatLogger.Companion.uninstall
import com.airsaid.logcat.internal.TAG

/**
 * Logger that [logcat] delegates to. Call [install] to install a new logger.
 * Calling [uninstall] will remove a logger.
 *
 * You should install [AndroidLogcatLogger] on Android or [PrintLogger] on a JVM
 * or [DiskLogger] for disk logging.
 *
 * @author airsaid
 * @see AndroidLogcatLogger
 * @see PrintLogger
 * @see DiskLogger
 */
interface LogcatLogger {

  /**
   * Whether a log with the provided priority should be logged and the corresponding message
   * providing lambda evaluated. Called by [logcat].
   */
  fun isLoggable(priority: LogPriority) = true

  /**
   * Write a log to its destination. Called by [logcat].
   *
   * @param priority the log priority. See [LogPriority].
   * @param tag the log tag.
   * @param message the log message.
   */
  fun log(priority: LogPriority, tag: String, message: String)

  companion object {
    private val lock = PlatformLock()
    private val loggers: MutableList<LogcatLogger> = mutableListOf()

    @PublishedApi
    @kotlin.concurrent.Volatile
    internal var loggerArray = emptyArray<LogcatLogger>()

    /**
     * Installs one or more [LogcatLogger].
     *
     * It is an error to call [install] more than once without calling [uninstall] in between,
     * however doing this won't throw, it'll log an error to the newly provided logger.
     *
     * @param loggers the logger list to install.
     */
    fun install(vararg loggers: LogcatLogger) {
      platformSynchronized(lock) {
        for (logger in loggers) {
          if (!isInstalled(logger)) {
            this.loggers.add(logger)
          } else {
            logger.log(
              ERROR,
              TAG,
              "Logger $logger is already installed, ignoring this call."
            )
          }
        }
        loggerArray = this.loggers.toTypedArray()
      }
    }

    /**
     * Whether a logger is installed.
     *
     * @return `true` if a logger is installed, `false` otherwise.
     */
    fun isInstalled(logger: LogcatLogger): Boolean {
      var installed = false
      platformSynchronized(lock) {
        val targetKey = identityKeyOf(logger)
        installed = loggers.any { identityKeyOf(it) == targetKey }
      }
      return installed
    }

    /**
     * Uninstall a [LogcatLogger].
     *
     * It is an error to call [uninstall] with a logger that was not installed, however doing this
     * won't throw, it'll log an error to the current logger.
     *
     * @param logger the logger to uninstall.
     */
    fun uninstall(logger: LogcatLogger) {
      platformSynchronized(lock) {
        if (isInstalled(logger)) {
          val targetKey = identityKeyOf(logger)
          loggers.removeAll { identityKeyOf(it) == targetKey }
        } else {
          logger.log(
            ERROR,
            TAG,
            "Logger $logger was not installed, ignoring this call."
          )
        }
        loggerArray = loggers.toTypedArray()
      }
    }

    /**
     * Uninstall all [LogcatLogger].
     */
    fun uninstallAll() {
      platformSynchronized(lock) {
        loggers.clear()
        loggerArray = emptyArray()
      }
    }

    private fun identityKeyOf(logger: LogcatLogger): String = loggerIdentityKeyOf(logger)
  }
}
