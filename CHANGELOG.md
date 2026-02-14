# Changelog

All notable changes to this project will be documented in this file.

## [1.2] - 2026-02-14

### New Features

- **Pull-to-Refresh:** You can now pull down from the top of the log screen to trigger a manual refresh and check for new step data.
- **Infinite Scrolling Log Viewer:** The log screen is now much more performant. It uses "infinite scrolling" to only load log entries as you scroll, which makes the app much faster and more responsive, especially for users with a large log history.
- **File-Based Logging:** To improve performance and reliability, the app now writes logs to a dedicated file instead of storing it in the app's preferences.

## [1.1] - 2026-01-17

### New Features

- **Polar Flow Quirk Fix:** Added an experimental feature to handle a quirk in how the Polar Flow app reports steps, which can cause issues with Walkscape when it rewrites its own records.
- **Unit Tests:** Added a suite of unit tests to validate the Polar Flow fix and improve code quality.

### Bug Fixes

- Fixed a bug where the app would try to insert a new record with `0` steps, which is unnecessary and could cause errors.
- Fixed a potential race condition that could occur with the Polar Flow fix during a day rollover.
- Corrected the client ID used for rewritten Polar Flow records.

## [1.0] - 2026-01-07

This was the initial public release of the OHealth Steps Fix app.

### Features

- **Core Functionality:** Reads step data from the OHealth app and rewrites it to Health Connect with modified metadata.
- **Manual & Periodic Polling:** Allows for both manual and periodic background checks for new step data.
- **Customizable Metadata:** Provides a settings screen to configure the device manufacturer, model, and type for the rewritten records.
- **In-App Log Viewer:** Includes a simple in-app log viewer for monitoring the app's activity.
- **CI/CD:** Implemented GitHub Actions workflows to automatically build and release new versions of the app.

