package com.airsaid.logcat

internal actual fun fullClassNameOf(target: Any): String = target::class.java.name

/**
 * JVM lock wrapper used by [platformSynchronized].
 */
internal actual class PlatformLock {
  internal val lock = Any()
}

internal actual inline fun <T> platformSynchronized(lock: PlatformLock, block: () -> T): T {
  return synchronized(lock.lock) { block() }
}
