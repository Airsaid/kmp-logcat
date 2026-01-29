package com.airsaid.logcat

/**
 * Utility to turn a [Throwable] into a loggable string.
 */
expect fun Throwable.asLog(): String
