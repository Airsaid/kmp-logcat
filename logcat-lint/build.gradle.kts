plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.androidLint)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  compileOnly(libs.android.lint.api)
  testImplementation(libs.android.lint.api)
  testImplementation(libs.android.lint.tests)
  testImplementation(libs.junit)
}

tasks.jar {
  manifest {
    attributes("Lint-Registry-v2" to "com.airsaid.logcat.lint.LogcatIssueRegistry")
  }
}

tasks.withType<Test>().configureEach {
  useJUnit()
}
