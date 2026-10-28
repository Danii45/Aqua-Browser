#!/bin/bash
# Installs the Android build on the emulator, walks through a few screens and screenshots each, and keeps
# the app's log. Run inside reactivecircus/android-emulator-runner.
#   bash test/android-shots.sh path/to/app-x86_64-release.apk out-dir
set -u
APK="$1"; OUT="$2"
PKG=app.aquabrowser.android
mkdir -p "$OUT"
adb install -r "$APK" > "$OUT/install.txt" 2>&1 || { cat "$OUT/install.txt"; exit 1; }
adb logcat -c
start() { adb shell am start -W -n "$PKG/.MainActivity" "$@" > /dev/null 2>&1; }
shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
start; shot 01-welcome 16
start --ez aqua_welcomed true; shot 02-home 5
start -a android.intent.action.VIEW -d https://www.wikipedia.org; shot 03-page 22
start --es aqua_screen tabs; shot 04-tabs 4
start --es aqua_screen settings; shot 05-settings 4
start --es aqua_screen none --es aqua_color orange; shot 06-orange 6
start --es aqua_profile work; shot 07-work-profile 6
start --es aqua_screen vault --ez aqua_unlock true; shot 08-vault 4
adb shell am force-stop "$PKG"
start; shot 09-relaunch 14
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -iE "aquabrowser|AndroidRuntime|FATAL|GeckoRuntime|GeckoSession|Gecko[A-Z]" | tail -400 > "$OUT/logcat.txt"
echo "done: $(ls "$OUT"/*.png | wc -l) screenshots"
