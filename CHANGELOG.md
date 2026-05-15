# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
