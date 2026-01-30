# kmp-logcat

[English](README.md)

[![Maven Central](https://img.shields.io/maven-central/v/com.airsaid/logcat.svg)](https://central.sonatype.com/artifact/com.airsaid/logcat)
[![CI](https://img.shields.io/github/actions/workflow/status/Airsaid/kmp-logcat/ci.yml?branch=main)](https://github.com/Airsaid/kmp-logcat/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg)

📝 一个轻量的 Kotlin Multiplatform（KMP）日志 API，支持惰性计算、可配置格式与磁盘日志，并能自动推断 tag。

## 特性

- `logcat { }` 惰性执行：只有安装了 logger 且允许输出时才会计算消息。
- 自动 tag：默认使用调用处类名；也可手动传入 tag。
- 支持不同的格式化输出：
  - `AndroidLogcatFormatStrategy` / `IosLogcatFormatStrategy`：平台风格控制台输出，字段可配置。
  - `PrettyFormatStrategy`：带边框的输出，包含线程信息与调用栈。
  - `NonFormatStrategy`：不做格式化，直接透传原始日志到 `LogStrategy`。
- 磁盘日志支持写入日志到磁盘中，并提供缓冲写入、按大小滚动、按时间清理、动态最大文件大小。
- 支持同时安装多个 logger。
- `Throwable.asLog()` 便于输出堆栈信息。

## 安装

在 `commonMain` 中添加依赖：

```kotlin
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation("com.airsaid:logcat:0.1.0")
    }
  }
}
```

确保已添加 Maven Central：

```kotlin
repositories {
  mavenCentral()
}
```

## 快速开始

### 1) 安装平台 Logger

Android（如 Application.onCreate 中初始化）：

```kotlin
val formatStrategy = AndroidLogcatFormatStrategy.Builder<AndroidLogcatLogStrategy>()
  .logStrategy(AndroidLogcatLogStrategy())
  .build()

AndroidLogcatLogger.install(
  minPriority = LogPriority.DEBUG,
  formatStrategy = formatStrategy,
)
```

iOS（应用启动时初始化）：

```kotlin
val formatStrategy = IosLogcatFormatStrategy.Builder<IosLogcatLogStrategy>()
  .logStrategy(IosLogcatLogStrategy())
  .build()

IosLogcatLogger.install(
  minPriority = LogPriority.DEBUG,
  formatStrategy = formatStrategy,
)
```

### 2) 输出日志

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

注意：`logcat { }` 是 `Any` 的扩展函数。顶层函数或没有 `this` 的场景请使用
`logcat(tag) { }` 重载。

## 格式策略

### AndroidLogcatFormatStrategy / IosLogcatFormatStrategy

格式接近平台控制台输出，可切换字段：

- `showTimeStamp(Boolean)`：是否输出时间戳。
- `showProcessId(Boolean)`：是否输出进程 id。
- `showThreadInfo(Boolean)`：是否输出线程名称与 id。
- `showTag(Boolean)`：是否输出 tag。
- `showLevel(Boolean)`：是否输出日志级别。

### PrettyFormatStrategy

带边框、线程信息、调用栈：

- `showThreadInfo(Boolean)`：是否输出线程信息。
- `methodCount(Int)`：输出的调用栈行数。
- `methodOffset(Int)`：调用栈偏移量。

### NonFormatStrategy

跳过格式化，直接交给底层 `LogStrategy` 输出。

## 磁盘日志

磁盘日志由 `DiskLogStrategy` + `DiskLogger` 组成，支持写入日志到磁盘中：

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

Builder 参数说明：

- `logFileDirectory(String)`
  - 日志文件根目录。请传入平台相关路径（如 Android filesDir、iOS Documents/Library）。
- `logFileGenerator(LogFileGenerator)`
  - 文件命名/滚动策略。默认 `DefaultLogFileGenerator`（按日期分目录，按时间分文件）。
- `logFileMaxSize(Long)`
  - 单个日志文件最大大小（字节），超过则滚动（默认 20MB）。
- `logFileMaxTime(Long)`
  - 日志文件最大保留时间（毫秒），超过则删除（默认 7 天）。
- `logFileMaxSizeResolver(LogFileMaxSizeResolver)`
  - 根据可用空间动态调整最大文件大小。
- `logBufferMaxSize(Int)`
  - 缓冲区大小（字符），达到后写入磁盘（默认 10K）。

手动 flush：

```kotlin
val diskLogger = DiskLogger(LogPriority.WARN, NonFormatStrategy(diskStrategy))
LogcatLogger.install(diskLogger)
// ...
diskLogger.flush()
```

## 同时安装多个 Logger

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

## 平台与版本

- Android
  - minSdk: 24
  - compileSdk / targetSdk: 35
  - JVM target: 17
- iOS targets
  - iosArm64 / iosX64 / iosSimulatorArm64
- Kotlin: 2.2.21

## 致谢

- square/logcat
- orhanobut/logger

## License

Apache-2.0. See `LICENSE`.
