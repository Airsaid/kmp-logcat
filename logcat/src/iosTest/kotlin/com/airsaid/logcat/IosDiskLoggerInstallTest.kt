package com.airsaid.logcat

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

}
