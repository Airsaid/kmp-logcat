# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1](https://github.com/Airsaid/kmp-logcat/compare/v0.2.0...v0.2.1) (2026-08-21)


### Bug Fixes

* add release automation ([7a72f0f](https://github.com/Airsaid/kmp-logcat/commit/7a72f0f72b5d71c879cfee3a8ddecc75d09b4e81))
* avoid duplicate log message evaluation across loggers ([931f36b](https://github.com/Airsaid/kmp-logcat/commit/931f36b8388bad09930313a1c7ac6448426fb34a))
* prevent disk logging thread blocking and preserve ordering ([9850d91](https://github.com/Airsaid/kmp-logcat/commit/9850d91caf9fc5b5b6520d07902fa5e89cde4410))
* prevent duplicate logger installation ([dd5dbf6](https://github.com/Airsaid/kmp-logcat/commit/dd5dbf6b3191dfd8a770e4eed22a3f6ba480e150))
* secure platform logging defaults ([8d18c5b](https://github.com/Airsaid/kmp-logcat/commit/8d18c5bf715b6f9970ed4dcc8a5bc9c290c15bcb))
* synchronize disk logger shutdown and reinstall ([8d3690f](https://github.com/Airsaid/kmp-logcat/commit/8d3690fc831b8d99a0fce4fdc184c7d6054ed8dc))

## [Unreleased]

- Make convenience logger installation idempotent through keyed lazy factories, preventing duplicate
  output and avoiding creation of unused disk logging resources.
- Add `AndroidLogcatLogger.installOnDebuggableApp` to skip logcat installation for non-debuggable apps.
- Make iOS unified log content private by default, with explicit `IosLogcatPrivacy.PUBLIC` opt-in.

## [0.2.0]
- Support multiple logger instances, allowing independent configuration and lifecycle management.
- Add disk logger lifecycle management: properly close disk loggers when uninstalled.
- Add Android logcat lint checks that report direct `android.util.Log` usage as errors.
- Support customizable timestamp formatting on Android and iOS platforms.
- Fix iOS bitmap decoding for logo rendering.
- Improve README documentation with clarity and formatting updates.
- Add release documentation and CI sync scripts for better maintainability.
- Update Android Kotlin Multiplatform library plugin for improved compatibility.

## [0.1.0]
- Initial release.
