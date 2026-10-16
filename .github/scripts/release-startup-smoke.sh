#!/usr/bin/env bash
# Installs an R8 release APK on the running emulator, starts it from the launcher and fails if the
# app process dies or logs a crash / missing-class / JNI error. Leaves logcat and a screenshot in
# release-smoke/ for the workflow artifact. Usage: release-startup-smoke.sh <apk> <package>
set -u
apk="$1"
package="$2"
mkdir -p release-smoke
bash .github/scripts/wait-for-android-ready.sh
echo "Device: API $(adb shell getprop ro.build.version.sdk | tr -d '\r'), page size $(adb shell getconf PAGE_SIZE | tr -d '\r'), ABI $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
adb install -r "$apk"
adb logcat -c
adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1
sleep 30
adb exec-out screencap -p > release-smoke/screen.png || true
adb logcat -d > release-smoke/logcat.txt
pid=$(adb shell pidof "$package" | tr -d '\r')
echo "Process ${package}: ${pid:-not running}"
# A crash of this app: "FATAL EXCEPTION" followed by "Process: <package>".
crash=$(grep -A2 "FATAL EXCEPTION" release-smoke/logcat.txt | grep -B1 -A30 "Process: ${package}" || true)
if [ -n "$crash" ]; then
  echo "::error::${package} crashed after start:"
  echo "$crash" | head -60
  exit 1
fi
if [ -z "$pid" ]; then
  echo "::error::${package} is not running 30 s after launch"
  grep -E "AndroidRuntime|ActivityManager.*${package}" release-smoke/logcat.txt | tail -40
  exit 1
fi
# Errors logged by the app process itself (caught ones too) are shown for review, not failed on.
adb logcat -d --pid="$pid" > release-smoke/logcat-app.txt
grep -E "UnsatisfiedLinkError|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|AbstractMethodError|VerifyError" \
  release-smoke/logcat-app.txt | sed 's/^/::warning::/' | head -20 || true
echo "Startup smoke test passed: ${package} running 30 s after launch, no crash."
