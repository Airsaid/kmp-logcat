# kmp-logcat

[中文说明](README.zh.md)

[![Maven Central](https://img.shields.io/maven-central/v/com.airsaid/logcat.svg)](https://central.sonatype.com/artifact/com.airsaid/logcat)
[![CI](https://img.shields.io/github/actions/workflow/status/Airsaid/kmp-logcat/ci.yml?branch=main)](https://github.com/Airsaid/kmp-logcat/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg)

📝 A lightweight Kotlin Multiplatform (KMP) logging API with lazy evaluation, configurable
formatting, disk logging, and tag inference.

## Features

- `logcat { }` is lazy: message blocks run only when a logger is installed and loggable.
- Automatic tag from the call site class name (with an overload for explicit tags).
- Supports different formatted outputs:
  - `AndroidLogcatFormatStrategy` / `IosLogcatFormatStrategy`: platform-style console output with configurable fields.
  - `PrettyFormatStrategy`: bordered output with thread info and call stack.
  - `NonFormatStrategy`: no formatting; forwards raw messages to the underlying `LogStrategy`.
- Disk logging writes logs to disk with buffering, size rotation, time-based cleanup, and dynamic size limits.
- Multiple loggers can be installed at the same time.
- `Throwable.asLog()` for readable stack traces.

## Installation

Add the dependency in `commonMain`:

```kotlin
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation("com.airsaid:logcat:$version")
    }
  }
}
```

Make sure you have Maven Central:

```kotlin
repositories {
  mavenCentral()
}
```

## Quick start

### 1) Install a platform logger

Android (e.g., Application.onCreate):

```kotlin
val formatStrategy = AndroidLogcatFormatStrategy.Builder<AndroidLogcatLogStrategy>()
  .logStrategy(AndroidLogcatLogStrategy())
  .timeStampPattern(
    pattern = "uuuu-MM-dd HH:mm:ss.SSS",
    timeZone = TimeZone.currentSystemDefault(),
  )
  .build()

AndroidLogcatLogger.install(
  minPriority = LogPriority.DEBUG,
  formatStrategy = formatStrategy,
)
```

iOS (app startup):

```kotlin
val formatStrategy = IosLogcatFormatStrategy.Builder<IosLogcatLogStrategy>()
  .logStrategy(IosLogcatLogStrategy())
  .timeStampPattern(
    pattern = "uuuu-MM-dd HH:mm:ss.SSS",
    timeZone = TimeZone.currentSystemDefault(),
  )
  .build()

IosLogcatLogger.install(
  minPriority = LogPriority.DEBUG,
  formatStrategy = formatStrategy,
)
```

### 2) Log messages

```kotlin
class Foo {
  fun bar() {
    logcat { "Default log" }
    logcat(LogPriority.INFO) { "Info log" }
    logcat(tag = "CustomTag") { "Custom tag log" }
  }
}

logcat("StandaloneTag") { "Log in a top-level function" }

try {
  error("boom")
} catch (t: Throwable) {
  logcat { t.asLog() }
}
```

Note: `logcat { }` is an `Any` extension. For top-level functions (no `this`), use the
`logcat(tag) { }` overload.

## Format strategies

### AndroidLogcatFormatStrategy / IosLogcatFormatStrategy

Formats output similar to platform consoles. You can toggle fields:

- `showTimeStamp(Boolean)`: include/exclude the timestamp.
- `timeStampPattern(String, TimeZone)`: customize timestamp output with a Unicode pattern and timezone.
- `timeStampFormatter((Instant) -> String)`: fully custom timestamp formatter.
- `showProcessId(Boolean)`: include/exclude the process id.
- `showThreadInfo(Boolean)`: include/exclude thread name and id.
- `showTag(Boolean)`: include/exclude the log tag.
- `showLevel(Boolean)`: include/exclude the log priority.

By default, timestamp uses `Instant.toString()` (ISO-8601 UTC).

### PrettyFormatStrategy

Adds borders, thread info, and call stack lines:

- `showThreadInfo(Boolean)`: include/exclude thread info.
- `methodCount(Int)`: number of call stack lines to print.
- `methodOffset(Int)`: offset into the call stack.

### NonFormatStrategy

Bypasses formatting and delegates directly to the underlying `LogStrategy`.

## Disk logging

Disk logging is provided by `DiskLogStrategy` + `DiskLogger` and supports writing logs to disk:

```kotlin
val diskStrategy = DiskLogStrategy.Builder()
  .logFileDirectory(logDirectory)
  .logFileGenerator(DefaultLogFileGenerator())
  .logFileMaxSize(1024L * 1024L * 100L) // 100MB
  .logFileMaxTime(7L * 24L * 60L * 60L * 1000L) // 7 days
  .logFileMaxSizeResolver(AvailableSpaceLogFileMaxSizeResolver())
  .logBufferMaxSize(10 * 1024) // 10K chars
  .build()

val formatStrategy = NonFormatStrategy(diskStrategy)
DiskLogger.installOnApp(LogPriority.WARN, formatStrategy)
```

Builder options:

- `logFileDirectory(String)`
  - Base directory for log files. Provide a platform-specific path (e.g., Android app files dir,
    iOS Documents/Library dir).
- `logFileGenerator(LogFileGenerator)`
  - File naming/rotation strategy. Default: `DefaultLogFileGenerator` (daily folder + date files).
- `logFileMaxSize(Long)`
  - Max size per log file in bytes before rotation (default 20MB).
- `logFileMaxTime(Long)`
  - Max retention time in milliseconds before deletion (default 7 days).
- `logFileMaxSizeResolver(LogFileMaxSizeResolver)`
  - Dynamically adjusts max size based on available space.
- `logBufferMaxSize(Int)`
  - Buffer size (chars) before flushing to disk (default 10K).

Manual flush:

```kotlin
val diskLogger = DiskLogger(LogPriority.WARN, NonFormatStrategy(diskStrategy))
LogcatLogger.install(diskLogger)
// ...
diskLogger.flush()
```

## Multiple loggers

```kotlin
val androidLogger = AndroidLogcatLogger(
  minPriority = LogPriority.DEBUG,
  formatStrategy = AndroidLogcatFormatStrategy.Builder<AndroidLogcatLogStrategy>()
    .logStrategy(AndroidLogcatLogStrategy())
    .build()
)
val diskLogger = DiskLogger(
  minPriority = LogPriority.WARN,
  formatStrategy = NonFormatStrategy(diskStrategy)
)

LogcatLogger.install(androidLogger, diskLogger)
```

## Requirements

- Android
  - minSdk: 24
  - compileSdk / targetSdk: 35
  - JVM target: 17
- iOS targets
  - iosArm64 / iosX64 / iosSimulatorArm64
- Kotlin: 2.2.21

## Changelog & releases

- Changelog: [CHANGELOG.md](CHANGELOG.md)
- Release process: [docs/releasing.md](docs/releasing.md)

## Acknowledgements

- square/logcat
- orhanobut/logger

## License

Apache-2.0. See [LICENSE](LICENSE).
