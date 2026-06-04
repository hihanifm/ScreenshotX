#!/bin/bash
# Build and run the E2E screenshot capture test.
# Assumes the app is already installed on the device.
set -euo pipefail

echo "Building test APK..."
./gradlew assembleAndroidTest -q

echo "Installing test APK..."
adb install -r app/build/outputs/apk/androidTest/debug/*.apk

echo "Running test..."
adb shell am instrument -w \
  -e class com.tools.screenshot3.ScreenshotCaptureE2ETest \
  com.tools.screenshot3.test/androidx.test.runner.AndroidJUnitRunner
