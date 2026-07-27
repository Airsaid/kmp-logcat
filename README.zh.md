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
      implementation("com.airsaid:logcat:$version")
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
AndroidLogcatLogger.installOnDebuggableApp(
  application = this,
  minPriority = LogPriority.DEBUG,
) {
  AndroidLogcatFormatStrategy.Builder<AndroidLogcatLogStrategy>()
    .logStrategy(AndroidLogcatLogStrategy())
    .timeStampPattern(
      pattern = "uuuu-MM-dd HH:mm:ss.SSS",
      timeZone = TimeZone.currentSystemDefault(),
    )
    .build()
}
```

`installOnDebuggableApp` 会在应用不可调试时跳过安装。只有明确需要在不可调试构建中输出
Logcat 日志时，才使用 `AndroidLogcatLogger.install`。

iOS（应用启动时初始化）：

```kotlin
IosLogcatLogger.install(
  minPriority = LogPriority.DEBUG,
) {
  IosLogcatFormatStrategy.Builder<IosLogcatLogStrategy>()
    .logStrategy(IosLogcatLogStrategy())
    .timeStampPattern(
      pattern = "uuuu-MM-dd HH:mm:ss.SSS",
      timeZone = TimeZone.currentSystemDefault(),
    )
    .build()
}
```

便捷安装方法具有幂等性，并使用内部私有安装 key。第一次成功调用的配置生效；之后的调用会
复用已安装的 logger，不会执行新的工厂。需要修改配置时，应先卸载现有 logger（完整重置日志
系统时也可调用 `LogcatLogger.uninstallAll()`），再重新安装。需要安装多个独立配置的同类型
logger 时，请显式构造实例并使用 `LogcatLogger.install(...)`。

`IosLogcatLogStrategy()` 默认将动态统一日志内容标记为私密。仅当日志确定不包含敏感信息时，
才显式选择公开输出：

```kotlin
IosLogcatLogStrategy(IosLogcatPrivacy.PUBLIC)
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

## Android lint 检查

Android 产物会随包发布一个自定义 lint 检查，用来在应用代码直接调用
`android.util.Log` 时给出错误。建议统一通过 kmp-logcat 输出日志，以保留惰性计算、
统一格式化以及已安装 logger 的控制能力。

```kotlin
// Error: LogcatSystemLogUsage
Log.d(tag, msg)

// Quick fix
logcat(tag, LogPriority.DEBUG) { msg }
```

在可以安全转换的情况下，Throwable 重载也会提供 quick fix：

```kotlin
// Error: LogcatSystemLogUsage
Log.e(tag, msg, throwable)

// Quick fix
logcat(tag, LogPriority.ERROR) { msg + "\n" + throwable.asLog() }
```

该 lint 检查只在 Android lint 中生效，不影响 common 或 iOS 源集。库自身的 Android
实现会按设计使用 `android.util.Log`。

如果你的应用确实需要直接使用平台日志，可以通过 `@SuppressLint("LogcatSystemLogUsage")`
或 `lint.xml` 配置压制该错误。

## 格式策略

### AndroidLogcatFormatStrategy / IosLogcatFormatStrategy

格式接近平台控制台输出，可切换字段：

- `showTimeStamp(Boolean)`：是否输出时间戳。
- `timeStampPattern(String, TimeZone)`：通过 Unicode pattern + 时区自定义时间戳格式。
- `timeStampFormatter((Instant) -> String)`：完全自定义时间戳格式化逻辑。
- `showProcessId(Boolean)`：是否输出进程 id。
- `showThreadInfo(Boolean)`：是否输出线程名称与 id。
- `showTag(Boolean)`：是否输出 tag。
- `showLevel(Boolean)`：是否输出日志级别。

默认时间戳格式为 `Instant.toString()`（ISO-8601 UTC）。

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
val diskLogger = DiskLogger.installOnApp(minPriority = LogPriority.WARN) {
  NonFormatStrategy(
    DiskLogStrategy.Builder()
      .logFileDirectory(logDirectory)
      .logFileGenerator(DefaultLogFileGenerator())
      .logFileMaxSize(1024L * 1024L * 100L) // 100MB
      .logFileMaxTime(7L * 24L * 60L * 60L * 1000L) // 7 days
      .logFileMaxSizeResolver(AvailableSpaceLogFileMaxSizeResolver())
      .logBufferMaxSize(10 * 1024) // 10K chars
      .build()
  )
}
```

请确保 `DiskLogStrategy.Builder().build()` 位于工厂内部。旧的 eager
`installOnApp(minPriority, formatStrategy)` 重载已废弃，仅为二进制兼容而保留；传给该重载
的 strategy 会在方法判定重复安装前就完成资源分配，因此无法避免创建未使用的资源。

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

磁盘日志会进入私有的后台串行工作队列。达到缓冲阈值时，目录遍历和文件写入不会在调用线程
执行，已接收的日志会按提交顺序处理。`flush()` 与 `close()` 会同步等待此前接收的日志完成
写入，因此不应在主线程频繁调用。

手动 flush：

```kotlin
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
  formatStrategy = NonFormatStrategy(
    DiskLogStrategy.Builder()
      .logFileDirectory(logDirectory)
      .build()
  )
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

## 变更记录与发布

- 变更记录：[CHANGELOG.md](CHANGELOG.md)
- 发布流程：[docs/releasing.md](docs/releasing.md)

## 致谢

- square/logcat
- orhanobut/logger

## License

Apache-2.0. See [LICENSE](LICENSE).
