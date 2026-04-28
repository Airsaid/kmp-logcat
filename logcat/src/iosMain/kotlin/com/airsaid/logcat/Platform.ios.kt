package com.airsaid.logcat

internal actual fun fullClassNameOf(target: Any): String =
  target::class.qualifiedName ?: target::class.simpleName ?: "Unknown"

/**
 * NSRecursiveLock wrapper used by [platformSynchronized].
 */
internal actual class PlatformLock {
  internal val lock = platform.Foundation.NSRecursiveLock()
}

internal actual inline fun <T> platformSynchronized(lock: PlatformLock, block: () -> T): T {
  lock.lock.lock()
  try {
    return block()
  } finally {
    lock.lock.unlock()
  }
}
