package com.airsaid.logcat.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class SystemLogUsageDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> = LOG_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, ANDROID_LOG_CLASS)) {
      return
    }

    val quickFix = createQuickFix(context, node, method)
    context.report(
      ISSUE,
      node,
      context.getNameLocation(node),
      "Use kmp-logcat's `logcat` API instead of `android.util.Log`.",
      quickFix
    )
  }

  private fun createQuickFix(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod
  ): LintFix? {
    if (context.file.extension != "kt") {
      return null
    }

    val arguments = node.valueArguments
    val replacement = when (node.methodName) {
      "println" -> createPrintlnReplacement(arguments)
      else -> createLogMethodReplacement(node.methodName, arguments, method)
    } ?: return null

    val imports = mutableListOf(
      "com.airsaid.logcat.LogPriority",
      "com.airsaid.logcat.logcat"
    )
    if (replacement.usesAsLog) {
      imports += "com.airsaid.logcat.asLog"
    }

    return fix()
      .name("Replace with logcat")
      .replace()
      .range(context.getLocation(node))
      .with(replacement.source)
      .imports(*imports.toTypedArray())
      .shortenNames()
      .reformat(true)
      .build()
  }

  private fun createLogMethodReplacement(
    methodName: String?,
    arguments: List<UExpression>,
    method: PsiMethod
  ): Replacement? {
    val priority = priorityForMethod(methodName) ?: return null
    if (arguments.size < 2) {
      return null
    }

    if (isThrowableParameter(method, index = 1)) {
      return null
    }

    val tag = arguments[0].asSourceString()
    val message = arguments[1].asSourceString()
    if (arguments.size >= 3) {
      if (!isThrowableParameter(method, index = 2)) {
        return null
      }

      val throwable = arguments[2].asSourceString()
      return Replacement(
        source = "logcat($tag, LogPriority.$priority) { $message + \"\\n\" + $throwable.asLog() }",
        usesAsLog = true
      )
    }

    return Replacement("logcat($tag, LogPriority.$priority) { $message }")
  }

  private fun isThrowableParameter(method: PsiMethod, index: Int): Boolean {
    val typeName = method.parameterList.parameters.getOrNull(index)?.type?.canonicalText
    return typeName == "java.lang.Throwable"
  }

  private fun createPrintlnReplacement(arguments: List<UExpression>): Replacement? {
    if (arguments.size != 3) {
      return null
    }

    val priority = priorityForExpression(arguments[0]) ?: return null
    val tag = arguments[1].asSourceString()
    val message = arguments[2].asSourceString()
    return Replacement("logcat($tag, LogPriority.$priority) { $message }")
  }

  private fun priorityForMethod(methodName: String?): String? {
    return when (methodName) {
      "v" -> "VERBOSE"
      "d" -> "DEBUG"
      "i" -> "INFO"
      "w" -> "WARN"
      "e" -> "ERROR"
      "wtf" -> "ASSERT"
      else -> null
    }
  }

  private fun priorityForExpression(expression: UExpression): String? {
    return when (expression.asSourceString().substringAfterLast('.')) {
      "VERBOSE", "2" -> "VERBOSE"
      "DEBUG", "3" -> "DEBUG"
      "INFO", "4" -> "INFO"
      "WARN", "5" -> "WARN"
      "ERROR", "6" -> "ERROR"
      "ASSERT", "7" -> "ASSERT"
      else -> null
    }
  }

  private data class Replacement(
    val source: String,
    val usesAsLog: Boolean = false
  )

  companion object {
    private const val ANDROID_LOG_CLASS = "android.util.Log"

    private val LOG_METHOD_NAMES = listOf("v", "d", "i", "w", "e", "wtf", "println")

    val ISSUE: Issue = Issue.create(
      id = "LogcatSystemLogUsage",
      briefDescription = "Uses android.util.Log directly",
      explanation = "kmp-logcat projects should route Android logs through the library `logcat` API so logging stays lazy, consistently formatted, and controlled by the installed loggers.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        SystemLogUsageDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      )
    ).setAndroidSpecific(true)
  }
}
