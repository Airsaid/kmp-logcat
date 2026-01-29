package com.airsaid.logcat

actual fun Throwable.asLog(): String {
  return runCatching { stackTraceToString() }.getOrElse { toString() }
}
