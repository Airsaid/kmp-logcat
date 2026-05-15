# Repository Guidelines

## Project Structure & Module Organization
- `logcat/`: KMP library module (core logging APIs + Android/iOS implementations). Source in `logcat/src/*Main` and tests in `logcat/src/commonTest` and `logcat/src/androidDeviceTest`.
- `composeApp/`: Demo KMP app using the library. Android entry points in `composeApp/src/androidMain`, shared UI in `composeApp/src/commonMain`, iOS entry in `composeApp/src/iosMain`.
- `iosApp/`: Xcode project wrapper for running the iOS app.
- `gradle/` and `gradle/libs.versions.toml`: build logic and dependency versions.

## Build, Test, and Development Commands
- `./gradlew build`: Builds all modules and runs unit tests where applicable.
- `./gradlew :composeApp:assembleDebug`: Builds the Android demo APK.
- `./gradlew :logcat:test`: Runs Kotlin/JVM unit tests (common tests where supported).
- `./gradlew :logcat:connectedAndroidTest`: Runs Android device tests in `logcat/src/androidDeviceTest` (requires emulator/device).
- iOS app: open `iosApp/iosApp.xcodeproj` in Xcode and run the scheme.

## Coding Style & Naming Conventions
- Kotlin style is standard/official (`kotlin.code.style=official`). Use 2-space indentation in Gradle Kotlin scripts and 2–4 spaces in Kotlin per IDE defaults.
- Package naming follows reverse-DNS, e.g., `com.airsaid.logcat` and `com.airsaid.logcat.demo`.
- Prefer clear, descriptive file/class names that mirror responsibilities (e.g., `DiskLogStrategy`, `PrettyFormatStrategy`).

## Testing Guidelines
- Common tests use `kotlin-test` in `commonTest` source sets.
- Android instrumentation tests live under `logcat/src/androidDeviceTest` and use AndroidX test + JUnit.
- Name tests with `*Test` suffix (e.g., `DiskLogStrategyBufferTest`).

## Commit & Pull Request Guidelines
- Use Conventional Commits so Release Please can determine release versions.
- `fix:` triggers a patch release, `feat:` triggers a minor release, and `!` or a `BREAKING CHANGE:` footer triggers a breaking release.
- `docs:`, `test:`, `ci:`, and `chore:` do not trigger a release by default.
- Use a `Release-As: x.y.z` footer when a PR must force a specific release version.
- Keep each PR focused and ensure the squash merge title keeps the Conventional Commits prefix.
- Keep commits focused by module and behavior.
- PRs should include: a short description, affected modules, and test results (commands + outcomes). For UI changes, add screenshots from Android and iOS.

## Documentation Guidelines
- When updating `README.md`, sync the same changes in other language variants (e.g., `README.zh.md`) to keep content aligned.

## Security & Configuration Tips
- `local.properties` is intentionally ignored; it contains machine-specific paths like the Android SDK. Do not commit secrets, tokens, or signing configs.
