package com.airsaid.logcat

/**
 * A [LogcatLogger] that always logs and delegates to [println] concatenating
 * the tag and message, separated by a space. Alternative to [AndroidLogcatLogger]
 * when running on a JVM.
 *
 * @author airsaid
 * @see AndroidLogcatLogger
 * @see DiskLogger
 */
object PrintLogger : LogcatLogger {

  override fun log(priority: LogPriority, tag: String, message: String) {
    println("$tag $message")
  }
}
