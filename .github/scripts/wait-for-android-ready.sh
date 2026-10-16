#!/usr/bin/env bash
# Waits until a freshly booted emulator can install and run tests: boot completed, boot
# animation stopped, package manager and storage manager answering. Without this the API 35
# 16 KB-page image sometimes fails the first install (DELETE_FAILED_INTERNAL_ERROR) and Gradle
# runs 0 tests.
set -u
deadline=$((SECONDS + 300))
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] \
  && [ "$(adb shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')" = "stopped" ] \
  && adb shell pm path android >/dev/null 2>&1 \
  && adb shell cmd package list packages >/dev/null 2>&1 \
  && adb shell service check mount 2>/dev/null | grep -q "found" \
  && adb shell ls /data/local/tmp >/dev/null 2>&1; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "Emulator not ready after 300 s" >&2
    exit 1
  fi
  sleep 5
done
# Give the package manager a moment to finish post-boot work (dexopt, package scans).
sleep 20
echo "Emulator ready: $(adb shell getprop ro.build.version.sdk | tr -d '\r') page=$(adb shell getconf PAGE_SIZE | tr -d '\r')"
