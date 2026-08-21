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
    private val keyedLoggers: MutableMap<LoggerInstallationKey<*>, KeyedLoggerState> = mutableMapOf()

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
     * Closing or uninstalling that logger, or calling [uninstallAll], releases the key for reuse
     * after any closeable resources have finished shutting down.
     *
     * [loggerFactory] must only create the logger and must not modify the logger registry.
     */
    internal fun <T : LogcatLogger> installIfAbsent(
      key: LoggerInstallationKey<T>,
      loggerFactory: () -> T,
    ): T {
      while (true) {
        var installedLogger: T? = null
        var closingCompletion: CloseCompletion? = null
        platformSynchronized(lock) {
          when (val state = keyedLoggers[key]) {
            is KeyedLoggerState.Active -> {
              @Suppress("UNCHECKED_CAST")
              installedLogger = state.logger as T
            }
            is KeyedLoggerState.Closing -> closingCompletion = state.completion
            null -> {
              val logger = loggerFactory()
              if (!isInstalledLocked(logger)) {
                loggers.add(logger)
                loggerArray = loggers.toTypedArray()
              }
              keyedLoggers[key] = KeyedLoggerState.Active(logger)
              installedLogger = logger
            }
          }
        }
        installedLogger?.let { return it }
        checkNotNull(closingCompletion).await()
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
      if (logger is CloseableLogcatLogger) {
        if (!closeLogger(logger, requireInstalled = true, suppressCloseFailure = true)) {
          logNotInstalled(logger)
        }
        return
      }

      var wasInstalled = false
      platformSynchronized(lock) {
        if (isInstalledLocked(logger)) {
          loggers.removeAll { it === logger }
          keyedLoggers.entries.removeAll {
            val state = it.value
            state is KeyedLoggerState.Active && state.logger === logger
          }
          wasInstalled = true
        } else {
          wasInstalled = false
        }
        loggerArray = loggers.toTypedArray()
      }
      if (!wasInstalled) {
        logNotInstalled(logger)
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
        loggerArray = emptyArray()
        val closingStates = mutableListOf<KeyedLoggerState.Closing>()
        keyedLoggers.keys.toList().forEach { key ->
          when (val state = keyedLoggers[key]) {
            is KeyedLoggerState.Active -> {
              val logger = state.logger
              if (logger is CloseableLogcatLogger) {
                keyedLoggers[key] = closingStates.firstOrNull { it.logger === logger }
                  ?: KeyedLoggerState.Closing(logger).also(closingStates::add)
              } else {
                keyedLoggers.remove(key)
              }
            }
            is KeyedLoggerState.Closing, null -> Unit
          }
        }
      }
      loggersToClose.forEach { logger ->
        if (logger is CloseableLogcatLogger) {
          closeLogger(logger, requireInstalled = false, suppressCloseFailure = true)
        }
      }
    }

    private fun isInstalledLocked(logger: LogcatLogger): Boolean =
      loggers.any { it === logger }

    internal fun close(logger: CloseableLogcatLogger) {
      closeLogger(logger, requireInstalled = false)
    }

    private fun closeLogger(
      logger: CloseableLogcatLogger,
      requireInstalled: Boolean,
      suppressCloseFailure: Boolean = false,
    ): Boolean {
      var wasInstalled = false
      var closingState: KeyedLoggerState.Closing? = null
      platformSynchronized(lock) {
        wasInstalled = isInstalledLocked(logger)
        if (wasInstalled || !requireInstalled) {
          loggers.removeAll { it === logger }
          closingState = keyedLoggers.values
            .filterIsInstance<KeyedLoggerState.Closing>()
            .firstOrNull { it.logger === logger }
            ?: KeyedLoggerState.Closing(logger)
          keyedLoggers.keys.toList().forEach { key ->
            val state = keyedLoggers[key]
            if (state is KeyedLoggerState.Active && state.logger === logger) {
              keyedLoggers[key] = checkNotNull(closingState)
            }
          }
          loggerArray = loggers.toTypedArray()
        }
      }
      if (!wasInstalled && requireInstalled) return false

      var closeFailure: Throwable? = null
      try {
        logger.closeResourcesOnce()
      } catch (error: Throwable) {
        closeFailure = error
      } finally {
        val completions = mutableSetOf<CloseCompletion>()
        platformSynchronized(lock) {
          keyedLoggers.entries.removeAll { entry ->
            val state = entry.value
            if (state is KeyedLoggerState.Closing && state.logger === logger) {
              completions += state.completion
              true
            } else {
              false
            }
          }
        }
        completions.forEach(CloseCompletion::complete)
      }
      if (!suppressCloseFailure) {
        closeFailure?.let { throw it }
      }
      return true
    }

    private fun logNotInstalled(logger: LogcatLogger) {
      logger.log(
        ERROR,
        TAG,
        "Logger $logger was not installed, ignoring this call."
      )
    }
  }
}

/**
 * Identity-based key for an internal logger installation slot.
 */
internal class LoggerInstallationKey<T : LogcatLogger>

private sealed interface KeyedLoggerState {
  class Active(val logger: LogcatLogger) : KeyedLoggerState

  class Closing(
    val logger: CloseableLogcatLogger,
    val completion: CloseCompletion = CloseCompletion(),
  ) : KeyedLoggerState
}

/**
 * A [LogcatLogger] with resources that should be released when uninstalled.
 */
abstract class CloseableLogcatLogger : LogcatLogger {
  private val resourceCloseLock = PlatformLock()
  private var resourceCloseCompletion: CloseCompletion? = null

  /**
   * Flushes pending work and releases logger resources.
   */
  fun close() {
    LogcatLogger.close(this)
  }

  /**
   * Flushes pending work and releases resources for the concrete logger implementation.
   */
  protected abstract fun closeResources()

  internal fun closeResourcesOnce() {
    var closeResources = false
    var completion: CloseCompletion? = null
    platformSynchronized(resourceCloseLock) {
      completion = resourceCloseCompletion
      if (completion == null) {
        completion = CloseCompletion()
        resourceCloseCompletion = completion
        closeResources = true
      }
    }
    if (!closeResources) {
      checkNotNull(completion).await()
      return
    }

    try {
      closeResources()
    } finally {
      checkNotNull(completion).complete()
    }
  }
}

internal expect class CloseCompletion() {
  fun await()
  fun complete()
}
