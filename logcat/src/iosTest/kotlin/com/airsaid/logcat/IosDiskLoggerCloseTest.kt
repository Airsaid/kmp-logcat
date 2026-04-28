@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDiskLoggerCloseTest {

  @Test
  fun closeFlushesBufferAndStopsWritingNewLogs() {
    val directory = "${NSTemporaryDirectory()}/kmp-logcat-${NSUUID.UUID().UUIDString}"
    val logFilePath = "$directory/test.log"
    val strategy = DiskLogStrategy.Builder()
      .logFileDirectory(directory)
      .logFileGenerator(FixedLogFileGenerator())
      .logBufferMaxSize(1024)
      .build()
    val logger = DiskLogger(
      minPriority = LogPriority.INFO,
      formatStrategy = NonFormatStrategy(strategy),
    )

    logger.log(LogPriority.INFO, "Test", "before-close")
    logger.close()
    logger.log(LogPriority.INFO, "Test", "after-close")

    val content = readFile(logFilePath)
    assertTrue(content.contains("before-close"))
    assertFalse(content.contains("after-close"))
  }

  private fun readFile(path: String): String {
    val file = fopen(path, "rb") ?: return ""
    return try {
      fseek(file, 0, SEEK_END)
      val size = ftell(file).toInt()
      rewind(file)

      val bytes = ByteArray(size)
      bytes.usePinned { pinned ->
        fread(pinned.addressOf(0), 1uL, size.toULong(), file)
      }
      bytes.decodeToString()
    } finally {
      fclose(file)
    }
  }

  private class FixedLogFileGenerator : LogFileGenerator {
    override fun generateLogFile(
      priority: LogPriority,
      tag: String,
      message: String,
      logFolder: String,
      maxSize: Long,
    ): String = "$logFolder/test.log"
  }
}
