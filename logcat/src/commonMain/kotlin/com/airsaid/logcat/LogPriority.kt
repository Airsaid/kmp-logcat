package com.airsaid.logcat

/**
 * An enum for log priorities that map to [android.util.Log] priority constants
 * without a direct import.
 *
 * @author airsaid
 */
enum class LogPriority(
  val priorityInt: Int,
  val priorityLetter: Char,
) {
  VERBOSE(2, 'V'),
  DEBUG(3, 'D'),
  INFO(4, 'I'),
  WARN(5, 'W'),
  ERROR(6, 'E'),
  ASSERT(7, 'A');

  companion object {

    /**
     * Returns the [LogPriority] for the given priority integer.
     *
     * If the priority integer is not recognized, [DEBUG] is returned.
     */
    fun of(priority: Int): LogPriority {
      return values().firstOrNull { it.priorityInt == priority } ?: DEBUG
    }
  }
}