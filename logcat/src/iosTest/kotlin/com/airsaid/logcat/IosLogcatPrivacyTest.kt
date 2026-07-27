@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.airsaid.logcat

import kotlin.test.Test
import kotlin.test.assertEquals
import platform.darwin.OS_LOG_TYPE_DEBUG

class IosLogcatPrivacyTest {

  @Test
  fun noArgStrategy_defaultsToPrivatePrivacy() {
    val strategy = IosLogcatLogStrategy()

    assertEquals(IosLogcatPrivacy.PRIVATE, strategy.privacy)
  }

  @Test
  fun explicitPublicPrivacy_isForwardedToEveryLine() {
    val capturedPrivacy = mutableListOf<IosLogcatPrivacy>()
    val capturedMessages = mutableListOf<String>()
    val strategy = IosLogcatLogStrategy(IosLogcatPrivacy.PUBLIC) { _, _, message, privacy ->
      capturedMessages += message
      capturedPrivacy += privacy
    }

    strategy.log(LogPriority.INFO, "Tag", "first\nsecond")

    assertEquals(listOf("first", "second"), capturedMessages)
    assertEquals(
      listOf(IosLogcatPrivacy.PUBLIC, IosLogcatPrivacy.PUBLIC),
      capturedPrivacy,
    )
  }

  @Test
  fun utf8ByteSplitting_preservesPrivacyForEveryPart() {
    val capturedParts = mutableListOf<String>()
    val capturedPrivacy = mutableListOf<IosLogcatPrivacy>()
    val text = "你".repeat(342)

    IosUnifiedLog.splitAndWriteLog(
      type = OS_LOG_TYPE_DEBUG,
      text = text,
      privacy = IosLogcatPrivacy.PRIVATE,
    ) { _, part, privacy ->
      capturedParts += part
      capturedPrivacy += privacy
    }

    assertEquals(text, capturedParts.joinToString(separator = ""))
    assertEquals(listOf(1023, 3), capturedParts.map { it.encodeToByteArray().size })
    assertEquals(
      listOf(IosLogcatPrivacy.PRIVATE, IosLogcatPrivacy.PRIVATE),
      capturedPrivacy,
    )
  }

  @Test
  fun internalError_alwaysUsesPrivatePrivacy() {
    var actualPrivacy: IosLogcatPrivacy? = null

    IosUnifiedLog.logError("Tag", "message") { _, _, privacy ->
      actualPrivacy = privacy
    }

    assertEquals(IosLogcatPrivacy.PRIVATE, actualPrivacy)
  }
}
