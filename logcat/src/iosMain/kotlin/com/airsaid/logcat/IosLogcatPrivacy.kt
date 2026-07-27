package com.airsaid.logcat

/**
 * Controls whether dynamic iOS unified log content is redacted.
 */
enum class IosLogcatPrivacy {
  /** Redacts dynamic log content unless the system is configured to reveal private data. */
  PRIVATE,

  /** Emits dynamic log content as public, readable text. */
  PUBLIC,
}
