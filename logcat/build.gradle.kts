import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.JavadocJar

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.androidLint)
  alias(libs.plugins.vanniktechMavenPublish)
  alias(libs.plugins.dokka)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }

  androidLibrary {
    namespace = "com.airsaid.logcat"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }

    withHostTestBuilder {
    }

    withDeviceTestBuilder {
      sourceSetTreeName = "test"
    }.configure {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  iosX64()
  iosArm64()
  iosSimulatorArm64()

  targets.withType(KotlinNativeTarget::class.java).configureEach {
    if (konanTarget.family == Family.IOS) {
      compilations.getByName("main").cinterops.create("osLog") {
        defFile(project.file("src/nativeInterop/cInterop/os_log.def"))
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlin.stdlib)
        api(libs.kotlinx.datetime)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.androidx.annotation)
      }
    }

    iosMain {
      dependencies {
      }
    }

    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }

    getByName("androidDeviceTest") {
      dependencies {
        implementation(libs.androidx.runner)
        implementation(libs.androidx.core)
        implementation(libs.androidx.testExt.junit)
      }
    }
  }
}

mavenPublishing {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka("dokkaGenerateHtml")
    )
  )
}

dependencies {
  lintPublish(project(":logcat-lint")) {
    isTransitive = false
  }
}
