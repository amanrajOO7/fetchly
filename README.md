# Fetchly

A download manager for Android, built with Kotlin and Jetpack Compose using Material 3 Expressive.

## Status

Early development. The first milestone covers the download engine and a minimal UI.

## Planned features

- Segmented (multi-connection) downloads with resume after pause, network loss, or app restart
- Single-stream fallback when the server doesn't support range requests
- Retry with backoff, speed and ETA display
- Foreground service for active transfers
- Queue management with concurrency limits
- Share-intent and clipboard URL capture

## Requirements

- Android 11 (API 30) or newer
- Android Studio with a current Android Gradle Plugin

## Package

`com.fetchly.app`
