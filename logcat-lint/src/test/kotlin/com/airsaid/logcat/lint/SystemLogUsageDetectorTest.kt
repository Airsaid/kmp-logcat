package com.airsaid.logcat.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class SystemLogUsageDetectorTest : LintDetectorTest() {

  override fun getDetector(): Detector = SystemLogUsageDetector()

  override fun getIssues(): List<Issue> = listOf(SystemLogUsageDetector.ISSUE)

  fun testDetectsKotlinSystemLogCallsAndOffersQuickFixes() {
    lint()
      .testModes(TestMode.DEFAULT)
      .files(
        androidLogStub,
        kotlin(
          """
          package test.pkg

          import android.util.Log

          fun test(tag: String, msg: String, throwable: Throwable) {
            Log.d(tag, msg)
            android.util.Log.e(tag, msg, throwable)
            Log.wtf(tag, msg)
            Log.println(Log.INFO, tag, msg)
          }
          """
        ).indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.d(tag, msg)
              ~
        src/test/pkg/test.kt:7: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          android.util.Log.e(tag, msg, throwable)
                           ~
        src/test/pkg/test.kt:8: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.wtf(tag, msg)
              ~~~
        src/test/pkg/test.kt:9: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.println(Log.INFO, tag, msg)
              ~~~~~~~
        4 errors, 0 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/test/pkg/test.kt line 6: Replace with logcat:
        @@ -3,0 +4,2 @@
        +import com.airsaid.logcat.LogPriority
        +import com.airsaid.logcat.logcat
        @@ -6 +8 @@
        -  Log.d(tag, msg)
        +  logcat(tag, LogPriority.DEBUG) { msg }
        Fix for src/test/pkg/test.kt line 7: Replace with logcat:
        @@ -3,0 +4,3 @@
        +import com.airsaid.logcat.LogPriority
        +import com.airsaid.logcat.asLog
        +import com.airsaid.logcat.logcat
        @@ -7 +10 @@
        -  android.util.Log.e(tag, msg, throwable)
        +  logcat(tag, LogPriority.ERROR) { msg + "\n" + throwable.asLog() }
        Fix for src/test/pkg/test.kt line 8: Replace with logcat:
        @@ -3,0 +4,2 @@
        +import com.airsaid.logcat.LogPriority
        +import com.airsaid.logcat.logcat
        @@ -8 +10 @@
        -  Log.wtf(tag, msg)
        +  logcat(tag, LogPriority.ASSERT) { msg }
        Fix for src/test/pkg/test.kt line 9: Replace with logcat:
        @@ -3,0 +4,2 @@
        +import com.airsaid.logcat.LogPriority
        +import com.airsaid.logcat.logcat
        @@ -9 +11 @@
        -  Log.println(Log.INFO, tag, msg)
        +  logcat(tag, LogPriority.INFO) { msg }
        """
      )
  }

  fun testDetectsJavaSystemLogCallsWithoutQuickFixes() {
    lint()
      .files(
        androidLogStub,
        java(
          """
          package test.pkg;

          import android.util.Log;

          class JavaSample {
            void test(String tag, String msg) {
              Log.d(tag, msg);
            }
          }
          """
        ).indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/JavaSample.java:7: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
            Log.d(tag, msg);
                ~
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  fun testIgnoresCustomLogClassesAndLogcatApi() {
    lint()
      .files(
        logcatStub,
        kotlin(
          """
          package test.pkg

          import com.airsaid.logcat.LogPriority
          import com.airsaid.logcat.logcat

          object Log {
            fun d(tag: String, msg: String): Int = 0
          }

          fun test(tag: String, msg: String) {
            Log.d(tag, msg)
            logcat(tag, LogPriority.DEBUG) { msg }
          }
          """
        ).indented()
      )
      .run()
      .expectClean()
  }

  fun testSuppressedCallsAreIgnored() {
    lint()
      .files(
        androidLogStub,
        suppressLintStub,
        kotlin(
          """
          package test.pkg

          import android.annotation.SuppressLint
          import android.util.Log

          @SuppressLint("LogcatSystemLogUsage")
          fun test(tag: String, msg: String) {
            Log.d(tag, msg)
          }
          """
        ).indented()
      )
      .run()
      .expectClean()
  }

  fun testComplexPrintlnPriorityReportsWithoutQuickFix() {
    lint()
      .files(
        androidLogStub,
        kotlin(
          """
          package test.pkg

          import android.util.Log

          fun test(priority: Int, tag: String, msg: String) {
            Log.println(priority, tag, msg)
          }
          """
        ).indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.println(priority, tag, msg)
              ~~~~~~~
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  fun testThrowableOnlyOverloadReportsWithoutQuickFix() {
    lint()
      .files(
        androidLogStub,
        kotlin(
          """
          package test.pkg

          import android.util.Log

          fun test(tag: String, throwable: Throwable) {
            Log.w(tag, throwable)
          }
          """
        ).indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.w(tag, throwable)
              ~
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  fun testUnknownThrowablePositionReportsWithoutQuickFix() {
    lint()
      .files(
        androidLogStub,
        kotlin(
          """
          package test.pkg

          import android.util.Log

          fun test(tag: String, msg: String, extra: String) {
            Log.d(tag, msg, extra)
          }
          """
        ).indented()
      )
      .run()
      .expect(
        """
        src/test/pkg/test.kt:6: Error: Use kmp-logcat's logcat API instead of android.util.Log. [LogcatSystemLogUsage]
          Log.d(tag, msg, extra)
              ~
        1 errors, 0 warnings
        """
      )
      .expectFixDiffs("")
  }

  private val androidLogStub = java(
    """
    package android.util;

    public class Log {
      public static final int VERBOSE = 2;
      public static final int DEBUG = 3;
      public static final int INFO = 4;
      public static final int WARN = 5;
      public static final int ERROR = 6;
      public static final int ASSERT = 7;

      public static int v(String tag, String msg) { return 0; }
      public static int d(String tag, String msg) { return 0; }
      public static int i(String tag, String msg) { return 0; }
      public static int w(String tag, String msg) { return 0; }
      public static int e(String tag, String msg) { return 0; }
      public static int wtf(String tag, String msg) { return 0; }
      public static int e(String tag, String msg, Throwable tr) { return 0; }
      public static int w(String tag, Throwable tr) { return 0; }
      public static int d(String tag, String msg, String extra) { return 0; }
      public static int println(int priority, String tag, String msg) { return 0; }
    }
    """
  ).indented()

  private val suppressLintStub = java(
    """
    package android.annotation;

    public @interface SuppressLint {
      String[] value();
    }
    """
  ).indented()

  private val logcatStub = kotlin(
    """
    package com.airsaid.logcat

    enum class LogPriority {
      VERBOSE,
      DEBUG,
      INFO,
      WARN,
      ERROR,
      ASSERT
    }

    fun logcat(
      tag: String,
      priority: LogPriority = LogPriority.DEBUG,
      message: () -> String
    ) {
    }

    fun Throwable.asLog(): String = ""
    """
  ).indented()
}
