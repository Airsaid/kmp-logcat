package com.airsaid.logcat.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class LogcatIssueRegistry : IssueRegistry() {

  override val issues = listOf(SystemLogUsageDetector.ISSUE)

  override val api = CURRENT_API

  override val vendor = Vendor(
    vendorName = "kmp-logcat",
    identifier = "com.airsaid.logcat",
    feedbackUrl = "https://github.com/Airsaid/kmp-logcat/issues"
  )
}
