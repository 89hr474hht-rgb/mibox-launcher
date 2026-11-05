#!/usr/bin/env bash
# Mi Box edition of the launcher: build it signed with the owner's release key, check the APK, then install it
# with CleanYourTV's own launcher/build-and-install.sh. Google TV stays enabled (STOCK_LAUNCHER=none: step E1+E2
# of mibox/docs/04-architecture.md), and the launcher is allowed to install its own updates.
#
# Usage: TV_HOST=<box ip>:5555 mibox/scripts/install/install-launcher.sh            build and check only
#        TV_HOST=<box ip>:5555 mibox/scripts/install/install-launcher.sh --execute  also install on the box
#
# --execute changes the box and shows on its screen: only in a time slot agreed with the owner (mibox/AGENTS.md).
# It reuses the adb server the owner started from Terminal.app (TV_ADB_BINARY); never run adb kill-server.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
EXPECTED_CERT=ea3231bad936833e9f2b7d81e64b946b2c290a7e568d996391af57706fcb50e7
: "${TV_HOST:?set TV_HOST to the box address, e.g. TV_HOST=192.168.1.50:5555 $0}"
export TV_ADB_BINARY="${TV_ADB_BINARY:-/opt/homebrew/bin/adb}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BT="$ANDROID_HOME/build-tools/34.0.0"

case "${1:-}" in
  "" | --execute) ;;
  *) echo "usage: TV_HOST=<box ip>:5555 $0 [--execute]" >&2; exit 2 ;;
esac

cd "$ROOT/launcher"
version="$(tr -d '[:space:]' < mibox/VERSION)"
echo "Building the Mi Box edition $version (tests, lint, signed release)..."
./gradlew --no-daemon -q --console=plain -Pmibox testReleaseUnitTest lintRelease assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
IFS=. read -r major minor patch <<< "$version"
code=$((major * 10000 + minor * 100 + patch))
# Captured first: with pipefail, grep -q closing the pipe early would fail aapt2 with SIGPIPE.
badging="$("$BT/aapt2" dump badging "$APK")"
printf '%s\n' "$badging" | sed -n 1p \
  | grep -q "name='com.gathin.tvhome' versionCode='$code' versionName='$version'" \
  || { echo "ERROR: the APK is not com.gathin.tvhome $version ($code)." >&2; exit 1; }
cert="$("$BT/apksigner" verify --print-certs "$APK" | awk '/certificate SHA-256 digest/ {print $NF}' | sort -u)"
[ "$cert" = "$EXPECTED_CERT" ] \
  || { echo "ERROR: the APK is not signed with the release key only (got: $cert)." >&2; exit 1; }
printf '%s\n' "$badging" | grep -q "mibox.UpdatesActivity" \
  || { echo "ERROR: the APK has no updater: it was built without -Pmibox." >&2; exit 1; }
echo "APK OK: $version, $(stat -f %z "$APK" 2>/dev/null || stat -c %s "$APK") bytes, release key, updater included."
shasum -a 256 "$APK"

if [ "${1:-}" != --execute ]; then
  echo "Dry run: nothing was sent to the box. Run again with --execute in the agreed time slot."
  exit 0
fi

# On Google TV, launcherx (priority 2) keeps Home while it is enabled, so build-and-install.sh ends with
# "Home resolves to ...launcherx, not to the new launcher" and, with STOCK_LAUNCHER=none, has nothing to roll
# back. That is the expected state for E1+E2: check the install here instead of trusting its exit status.
SKIP_BUILD=1 STOCK_LAUNCHER=none ./build-and-install.sh \
  || echo "build-and-install.sh stopped (expected while Google TV keeps Home); checking the install..."
TV() { "$ROOT/tools/venv/bin/python" -I "$ROOT/tools/tv.py" "$1"; }
TV "pm list packages --show-versioncode com.gathin.tvhome" | grep -q "package:com.gathin.tvhome versionCode:$code" \
  || { echo "ERROR: com.gathin.tvhome $version is not installed on the box." >&2; exit 1; }
echo "Installed on the box: com.gathin.tvhome $version ($code)."
# Android 8+ asks each installer app for "install unknown apps". Google TV has no screen for it, so it is granted
# here once; the update itself still needs the user's confirmation on the box every time.
TV "appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow; appops get com.gathin.tvhome REQUEST_INSTALL_PACKAGES"
echo "Installed. Next: the E1+E2 tests in mibox/docs/02-plan-de-tests.md."
