#!/usr/bin/env bash
# READ-ONLY audit of the Mi Box S 2nd gen (MiTV-AFKR0) over network ADB.
#
# Usage:  scripts/audit/audit.sh <box-ip>[:port]
#
# Every command sent to the box goes through run_ro(), which refuses anything that is not on the
# read-only allowlist below. Nothing is enabled, disabled, installed, written or rebooted.
# Output goes to audits/<timestamp>/ (git-ignored: it holds serial numbers, MAC addresses, Wi-Fi and
# Bluetooth device names).
set -euo pipefail

HOST="${1:?usage: audit.sh <box-ip>[:port]}"
case "$HOST" in *:*) SERIAL="$HOST" ;; *) SERIAL="$HOST:5555" ;; esac

command -v adb >/dev/null || { echo "adb introuvable (brew install --cask android-platform-tools)" >&2; exit 1; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/audits/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/commands.log"
: > "$LOG"

# Read-only allowlist: a command must START with one of these prefixes.
ALLOW='^(getprop( |$)|uname |cat /proc/(cpuinfo|meminfo|version)$|df( |$)|wm (size|density)$|pm list (packages|features)|cmd package (resolve-activity|query-activities|query-services) |dumpsys [a-z_.]+( -[a-z]+)?$|settings list (global|secure|system)$|ls /vendor/usr/keylayout$|cmd wifi (status|list-networks)$)'
# Belt and braces: even an allowed prefix is refused if it contains a mutating word.
DENY='(disable|enable|install|uninstall|clear|put |set-|setprop|reboot|rm |kill|force-stop|grant|revoke|am start|input |svc |appops set|reset|forget|connect-network|>|;|&&|\|)'

run_ro() {   # run_ro <output file> <shell command>
  local file="$1" cmd="$2"
  if ! printf '%s' "$cmd" | grep -Eq "$ALLOW" || printf '%s' "$cmd" | grep -Eq "$DENY"; then
    echo "REFUSÉ (hors liste lecture seule) : $cmd" | tee -a "$LOG" >&2
    return 1
  fi
  printf '%s  %s\n' "$(date +%H:%M:%S)" "$cmd" >> "$LOG"
  adb -s "$SERIAL" shell "$cmd" > "$OUT/$file" 2>&1 || echo "  (code de sortie non nul, voir $file)" >> "$LOG"
}

echo "Connexion à $SERIAL… (acceptez « Autoriser le débogage ? » sur la TV si la question apparaît)"
adb connect "$SERIAL" >/dev/null 2>&1 || true
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  STATE="$(adb -s "$SERIAL" get-state 2>/dev/null || true)"
  [ "$STATE" = "device" ] && break
  sleep 5
done
if [ "$STATE" != "device" ]; then
  echo "Boîtier non joignable ou non autorisé (état : ${STATE:-aucun})." >&2
  adb devices -l >&2
  exit 2
fi
echo "Connecté. Audit en lecture seule vers $OUT"

# --- Identity, hardware, firmware
run_ro props.txt          'getprop'
run_ro uname.txt          'uname -a'
run_ro cpuinfo.txt        'cat /proc/cpuinfo'
run_ro meminfo.txt        'cat /proc/meminfo'
run_ro storage.txt        'df -h'
run_ro screen-size.txt    'wm size'
run_ro screen-density.txt 'wm density'
run_ro features.txt       'pm list features'
run_ro display.txt        'dumpsys display'

# --- Packages
run_ro pkg-all.txt        'pm list packages -f -U --show-versioncode'
run_ro pkg-system.txt     'pm list packages -s'
run_ro pkg-third.txt      'pm list packages -3'
run_ro pkg-disabled.txt   'pm list packages -d'
run_ro pkg-uninstalled-for-user.txt 'pm list packages -u'
run_ro dumpsys-package.txt 'dumpsys package'

# --- Home / launchers
run_ro home-owner.txt     'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
run_ro home-claimants.txt 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
run_ro leanback-apps.txt  'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER'
run_ro tv-inputs.txt      'cmd package query-services --brief -a android.media.tv.TvInputService'

# --- Bluetooth, audio, HDMI-CEC, input devices (remote)
run_ro bluetooth.txt      'dumpsys bluetooth_manager'
run_ro audio.txt          'dumpsys audio'
run_ro hdmi-cec.txt       'dumpsys hdmi_control'
run_ro input-devices.txt  'dumpsys input'
run_ro keylayouts.txt     'ls /vendor/usr/keylayout'

# --- Network
run_ro wifi.txt           'dumpsys wifi'
run_ro wifi-status.txt    'cmd wifi status'
run_ro wifi-networks.txt  'cmd wifi list-networks'
run_ro connectivity.txt   'dumpsys connectivity'

# --- Services, DRM, accessibility, memory
run_ro accessibility.txt  'dumpsys accessibility'
run_ro drm.txt            'dumpsys media.drm'
run_ro meminfo-apps.txt   'dumpsys meminfo'

# --- Settings snapshot (backup of what ADB can read)
run_ro settings-global.txt 'settings list global'
run_ro settings-secure.txt 'settings list secure'
run_ro settings-system.txt 'settings list system'

# --- Local summary (computed on the Mac, nothing sent to the box)
prop() { grep -F "[$1]" "$OUT/props.txt" | sed -E 's/.*\]: \[(.*)\]/\1/' | tr -d '\r' | head -1; }
{
  echo "# Audit $(basename "$OUT")"
  echo
  echo "| Propriété | Valeur |"
  echo "|---|---|"
  for p in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release \
           ro.build.version.sdk ro.build.display.id ro.build.fingerprint ro.build.version.security_patch \
           ro.product.cpu.abilist ro.board.platform ro.hardware ro.adb.secure ro.debuggable \
           persist.sys.locale ro.oem_unlock_supported ro.boot.verifiedbootstate ro.boot.flash.locked; do
    echo "| \`$p\` | \`$(prop "$p")\` |"
  done
  echo "| RAM totale | $(grep MemTotal "$OUT/meminfo.txt" | tr -s ' ' | cut -d' ' -f2-3) |"
  echo "| RAM disponible | $(grep MemAvailable "$OUT/meminfo.txt" | tr -s ' ' | cut -d' ' -f2-3) |"
  echo "| Écran | $(tr -d '\r' < "$OUT/screen-size.txt" | tr '\n' ' ') / $(tr -d '\r' < "$OUT/screen-density.txt" | tr '\n' ' ') |"
  echo "| Packages (total / système / tiers / désactivés) | $(grep -c '^package:' "$OUT/pkg-all.txt") / $(grep -c '^package:' "$OUT/pkg-system.txt") / $(grep -c '^package:' "$OUT/pkg-third.txt") / $(grep -c '^package:' "$OUT/pkg-disabled.txt") |"
  echo "| Propriétaire de HOME | \`$(tr -d '\r' < "$OUT/home-owner.txt" | tail -1)\` |"
  echo
  echo "## Stockage"
  echo '```'; tr -d '\r' < "$OUT/storage.txt"; echo '```'
  echo
  echo "## Candidats HOME"
  echo '```'; tr -d '\r' < "$OUT/home-claimants.txt"; echo '```'
} > "$OUT/summary.md"

# Package inventory as CSV: package,apk_path,uid,versionCode,origin,state
DISABLED="$(sed -n 's/^package://p' "$OUT/pkg-disabled.txt" | tr -d '\r')"
THIRD="$(sed -n 's/^package://p' "$OUT/pkg-third.txt" | tr -d '\r')"
{
  echo "package,apk_path,uid,version_code,origine,etat"
  tr -d '\r' < "$OUT/pkg-all.txt" | sed -n 's/^package://p' | while read -r line; do
    path_pkg="${line%% *}"; rest="${line#* }"
    pkg="${path_pkg##*=}"; path="${path_pkg%=*}"
    vc="$(printf '%s' "$rest" | sed -n 's/.*versionCode:\([0-9]*\).*/\1/p')"
    uid="$(printf '%s' "$rest" | sed -n 's/.*uid:\([0-9]*\).*/\1/p')"
    origin=systeme; printf '%s\n' "$THIRD" | grep -qxF "$pkg" && origin=tiers
    state=active; printf '%s\n' "$DISABLED" | grep -qxF "$pkg" && state=desactive
    echo "$pkg,$path,$uid,$vc,$origin,$state"
  done | sort
} > "$OUT/inventory.csv"

echo
cat "$OUT/summary.md"
echo
echo "Terminé. Commandes envoyées : $OUT/commands.log — inventaire : $OUT/inventory.csv"
