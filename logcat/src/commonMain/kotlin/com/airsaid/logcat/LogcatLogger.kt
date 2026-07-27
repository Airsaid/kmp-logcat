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
    private val keyedLoggers: MutableMap<LoggerInstallationKey<*>, LogcatLogger> = mutableMapOf()

    @PublishedApi
    @kotlin.concurrent.Volatile
    internal var loggerArray = emptyArray<LogcatLogger>()

    /**
     * Installs one or more [LogcatLogger].
     *
     * It is an error to install the same logger instance more than once without calling [uninstall]
     * in between. Doing this won't throw; it logs an error to the duplicated logger instead.
     * Different instances of the same logger class may be installed together.
     *
     * @param loggers the logger list to install.
     */
    fun install(vararg loggers: LogcatLogger) {
      platformSynchronized(lock) {
        for (logger in loggers) {
          if (!isInstalledLocked(logger)) {
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
     * Installs the logger created by [loggerFactory] when no logger is associated with [key].
     *
     * The key lookup, logger creation, and installation are performed atomically. Repeated calls
     * with the same key return the first installed logger without invoking [loggerFactory] again.
     * Uninstalling that logger, or calling [uninstallAll], releases the key for reuse.
     *
     * [loggerFactory] must only create the logger and must not modify the logger registry.
     */
    internal fun <T : LogcatLogger> installIfAbsent(
      key: LoggerInstallationKey<T>,
      loggerFactory: () -> T,
    ): T = platformSynchronized(lock) {
      val installedLogger = keyedLoggers[key]
      if (installedLogger != null) {
        @Suppress("UNCHECKED_CAST")
        (installedLogger as T)
      } else {
        val logger = loggerFactory()
        if (!isInstalledLocked(logger)) {
          loggers.add(logger)
          loggerArray = loggers.toTypedArray()
        }
        keyedLoggers[key] = logger
        logger
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
        installed = isInstalledLocked(logger)
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
      var loggerToClose: LogcatLogger? = null
      var wasInstalled = false
      platformSynchronized(lock) {
        if (isInstalledLocked(logger)) {
          loggers.removeAll { it === logger }
          keyedLoggers.entries.removeAll { it.value === logger }
          loggerToClose = logger
          wasInstalled = true
        } else {
          wasInstalled = false
        }
        loggerArray = loggers.toTypedArray()
      }
      if (wasInstalled) {
        closeIfNeeded(loggerToClose)
      } else {
        logger.log(
          ERROR,
          TAG,
          "Logger $logger was not installed, ignoring this call."
        )
      }
    }

    /**
     * Uninstall all [LogcatLogger].
     */
    fun uninstallAll() {
      var loggersToClose: List<LogcatLogger> = emptyList()
      platformSynchronized(lock) {
        loggersToClose = loggers.toList()
        loggers.clear()
        keyedLoggers.clear()
        loggerArray = emptyArray()
      }
      loggersToClose.forEach(::closeIfNeeded)
    }

    private fun isInstalledLocked(logger: LogcatLogger): Boolean =
      loggers.any { it === logger }

    private fun closeIfNeeded(logger: LogcatLogger?) {
      if (logger is CloseableLogcatLogger) {
        runCatching { logger.close() }
      }
    }
  }
}

/**
 * Identity-based key for an internal logger installation slot.
 */
internal class LoggerInstallationKey<T : LogcatLogger>

/**
 * A [LogcatLogger] with resources that should be released when uninstalled.
 */
interface CloseableLogcatLogger : LogcatLogger {

  /**
   * Flushes pending work and releases logger resources.
   */
  fun close()
}
