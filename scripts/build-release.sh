#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew assembleRelease

release_apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"

if [[ -z "${release_apk:-}" ]]; then
  echo "Release APK not found in app/build/outputs/apk/release/" >&2
  exit 1
fi

release_size_bytes="$(stat -f%z "$release_apk")"
release_size_mb="$(awk "BEGIN { printf \"%.2f\", $release_size_bytes / 1024 / 1024 }")"

echo "Release APK: $release_apk"
echo "Release size: ${release_size_mb} MB (${release_size_bytes} bytes)"

debug_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' 2>/dev/null | sort | tail -n 1 || true)"

if [[ -n "${debug_apk:-}" ]]; then
  debug_size_bytes="$(stat -f%z "$debug_apk")"
  debug_size_mb="$(awk "BEGIN { printf \"%.2f\", $debug_size_bytes / 1024 / 1024 }")"
  size_delta_bytes=$((debug_size_bytes - release_size_bytes))
  size_delta_mb="$(awk "BEGIN { printf \"%.2f\", $size_delta_bytes / 1024 / 1024 }")"
  shrink_percent="$(awk "BEGIN { if ($debug_size_bytes > 0) printf \"%.1f\", ($size_delta_bytes / $debug_size_bytes) * 100; else print \"0.0\" }")"

  echo "Debug APK: $debug_apk"
  echo "Debug size: ${debug_size_mb} MB (${debug_size_bytes} bytes)"
  echo "Size delta: ${size_delta_mb} MB (${size_delta_bytes} bytes, ${shrink_percent}% smaller)"
fi
