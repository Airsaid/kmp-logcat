package com.airsaid.logcat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IosLogcatLoggerInstallTest {

  @BeforeTest
  fun setUp() {
    LogcatLogger.uninstallAll()
  }

  @AfterTest
  fun tearDown() {
    LogcatLogger.uninstallAll()
  }

  @Test
  fun repeatedInstallRunsFactoryOnlyOnce() {
    var factoryCalls = 0

    val first = IosLogcatLogger.install {
      factoryCalls++
      NonFormatStrategy(IosLogcatLogStrategy())
    }
    val second = IosLogcatLogger.install {
      factoryCalls++
      NonFormatStrategy(IosLogcatLogStrategy())
    }

    assertEquals(1, factoryCalls)
    assertSame(first, second)
    assertEquals(1, LogcatLogger.loggerArray.count { it is IosLogcatLogger })
  }

  @Test
  fun uninstallAllAllowsFactoryToRunAgain() {
    IosLogcatLogger.install { NonFormatStrategy(IosLogcatLogStrategy()) }
    LogcatLogger.uninstallAll()

    var factoryCalls = 0
    IosLogcatLogger.install {
      factoryCalls++
      NonFormatStrategy(IosLogcatLogStrategy())
    }

    assertEquals(1, factoryCalls)
  }
}
