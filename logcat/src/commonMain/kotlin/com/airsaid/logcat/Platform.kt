package com.airsaid.logcat

/**
 * Returns the fully qualified class name for log tag derivation.
 */
internal expect fun fullClassNameOf(target: Any): String

/**
 * Returns a stable identity key for logger de-duplication across platforms.
 */
internal expect fun loggerIdentityKeyOf(logger: LogcatLogger): String

/**
 * Platform-specific lock used by [platformSynchronized].
 */
internal expect class PlatformLock()

/**
 * Executes [block] with a platform-specific synchronization primitive.
 */
internal expect inline fun <T> platformSynchronized(lock: PlatformLock, block: () -> T): T
