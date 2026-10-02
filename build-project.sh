#!/usr/bin/env bash
set -euo pipefail
if [[ -x ./gradlew ]]; then
  ./gradlew assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle assembleDebug
else
  echo "Gradle is not installed. Open this project in Android Studio and run assembleDebug." >&2
  exit 1
fi
