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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosDiskLoggerInstallTest {

  @BeforeTest
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @AfterTest
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun lazyInstallCreatesDiskResourcesOnlyOnce() {
    val directory = "${NSTemporaryDirectory()}/kmp-logcat-${NSUUID.UUID().UUIDString}"
    var factoryCalls = 0

    val first = DiskLogger.installOnApp(LogPriority.INFO) {
      factoryCalls++
      NonFormatStrategy(
        DiskLogStrategy.Builder()
          .logFileDirectory(directory)
          .build(),
      )
    }
    val second = DiskLogger.installOnApp(LogPriority.ERROR) {
      factoryCalls++
      error("The duplicate installation factory must not be evaluated.")
    }

    assertEquals(1, factoryCalls)
    assertSame(first, second)
    assertEquals(1, LogcatLogger.loggerArray.count { it is DiskLogger })
  }

  @Suppress("DEPRECATION")
  @Test
  fun eagerDuplicateDoesNotCloseCallerOwnedStrategy() {
    val firstDirectory =
      "${NSTemporaryDirectory()}/kmp-logcat-${NSUUID.UUID().UUIDString}-first"
    val rejectedDirectory =
      "${NSTemporaryDirectory()}/kmp-logcat-${NSUUID.UUID().UUIDString}-rejected"
    val rejectedFile = "$rejectedDirectory/test.log"
    val firstStrategy = DiskLogStrategy.Builder()
      .logFileDirectory(firstDirectory)
      .build()
    val rejectedStrategy = DiskLogStrategy.Builder()
      .logFileDirectory(rejectedDirectory)
      .logFileGenerator(FixedLogFileGenerator())
      .logBufferMaxSize(1024)
      .build()

    try {
      DiskLogger.installOnApp(LogPriority.INFO, NonFormatStrategy(firstStrategy))
      DiskLogger.installOnApp(LogPriority.INFO, NonFormatStrategy(rejectedStrategy))

      rejectedStrategy.log(LogPriority.INFO, "Test", "caller-owned-strategy")
      rejectedStrategy.flush()

      assertTrue(readFile(rejectedFile).contains("caller-owned-strategy"))
      assertEquals(1, LogcatLogger.loggerArray.count { it is DiskLogger })
    } finally {
      rejectedStrategy.close()
    }
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
