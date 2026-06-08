#!/bin/bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
    echo "adb not found. Add Android SDK platform-tools to PATH." >&2
    exit 1
fi

serial="${1:-${ANDROID_SERIAL:-}}"

if [[ -z "$serial" ]]; then
    devices="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    device_count="$(printf '%s\n' "$devices" | awk 'NF { count++ } END { print count + 0 }')"
    physical_devices="$(printf '%s\n' "$devices" | awk '!/^emulator-/ && NF')"
    physical_device_count="$(printf '%s\n' "$physical_devices" | awk 'NF { count++ } END { print count + 0 }')"

    if [[ "$device_count" -eq 0 ]]; then
        echo "No Android device or emulator connected." >&2
        exit 1
    fi

    if [[ "$physical_device_count" -eq 1 ]]; then
        serial="$physical_devices"
    elif [[ "$device_count" -gt 1 ]]; then
        echo "Multiple Android targets connected:" >&2
        printf '  %s\n' $devices >&2
        echo "Run: ./run_release.sh <device-serial>" >&2
        exit 1
    else
        serial="$devices"
    fi
fi

if ! adb -s "$serial" get-state >/dev/null 2>&1; then
    echo "No Android device or emulator connected." >&2
    exit 1
fi

export ANDROID_SERIAL="$serial"

# Device selection lives here (bash is good at adb parsing); build/install/launch
# lives in the Gradle task (good at build logic). buildInternalRelease honors ANDROID_SERIAL.
echo "Building, installing, and launching signed release app on $serial..."
./gradlew buildInternalRelease
