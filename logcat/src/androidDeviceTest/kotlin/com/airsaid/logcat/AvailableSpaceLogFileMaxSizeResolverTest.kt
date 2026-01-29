package com.airsaid.logcat

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class AvailableSpaceLogFileMaxSizeResolverTest {

  @Test
  fun returnsConfiguredMaxSizeWhenUsableSpaceIsNonPositive() {
    val resolver = resolverWithUsableSpace(0L)
    val result = resolver.resolveMaxSize(File("/tmp"), 123L)
    assertEquals(123L, result)
  }

  @Test
  fun clampsToMinSizeWhenUsableSpaceBelowReserve() {
    val resolver = AvailableSpaceLogFileMaxSizeResolver(
      context = ApplicationProvider.getApplicationContext(),
      minSize = 256L * 1024L, // 256KB
      reserveBytes = 50L * 1024L * 1024L, // 50MB
      usableSpaceFraction = 0.1f,
      usableSpaceProvider = { 1024L * 1024L }, // 1MB
    )
    val result = resolver.resolveMaxSize(File("/tmp"), 500L * 1024L * 1024L)
    assertEquals(256L * 1024L, result)
  }

  @Test
  fun appliesFractionAndUpperBound() {
    val usableSpace = 1024L * 1024L * 1024L // 1GB
    val resolver = AvailableSpaceLogFileMaxSizeResolver(
      context = ApplicationProvider.getApplicationContext(),
      minSize = 256L * 1024L, // 256KB
      reserveBytes = 50L * 1024L * 1024L, // 50MB
      usableSpaceFraction = 0.1f,
      usableSpaceProvider = { usableSpace },
    )
    val result = resolver.resolveMaxSize(File("/tmp"), 500L * 1024L * 1024L)
    val expected = ((usableSpace - 50L * 1024L * 1024L) * 0.1f).toLong()
    assertEquals(expected, result)
  }

  @Test
  fun respectsConfiguredMaxSizeWhenItIsSmallerThanMinSize() {
    val resolver = AvailableSpaceLogFileMaxSizeResolver(
      context = ApplicationProvider.getApplicationContext(),
      minSize = 256L * 1024L, // 256KB
      reserveBytes = 50L * 1024L * 1024L, // 50MB
      usableSpaceFraction = 0.1f,
      usableSpaceProvider = { 1024L * 1024L * 1024L }, // 1GB
    )
    val result = resolver.resolveMaxSize(File("/tmp"), 128L * 1024L)
    assertEquals(128L * 1024L, result)
  }

  private fun resolverWithUsableSpace(usableSpace: Long) =
    AvailableSpaceLogFileMaxSizeResolver(
      context = ApplicationProvider.getApplicationContext(),
      minSize = 256L * 1024L,
      reserveBytes = 50L * 1024L * 1024L,
      usableSpaceFraction = 0.1f,
      usableSpaceProvider = { usableSpace },
    )
}
