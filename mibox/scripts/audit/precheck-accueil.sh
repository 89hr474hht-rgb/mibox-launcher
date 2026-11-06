#!/usr/bin/env bash
# READ-ONLY check of the Mi Box before steps E4, E3 and E11 (mibox/docs/07-plan-accueil-et-voix.md).
#
# Usage:  mibox/scripts/audit/precheck-accueil.sh <box-ip>[:port]
#
# Prints a short summary and writes nothing to the box. Every shell command goes through run_ro(),
# which refuses anything outside the read-only allowlist. It never reads what is being watched (no
# activity stack, no media sessions, no screenshot) and never prints a Wi-Fi name, an address or a
# serial: the raw output is filtered on the Mac and only the counts and package states are shown.
set -euo pipefail

HOST="${1:?usage: precheck-accueil.sh <box-ip>[:port]}"
case "$HOST" in *:*) SERIAL="$HOST" ;; *) SERIAL="$HOST:5555" ;; esac
ADB="${TV_ADB_BINARY:-adb}"

ALLOW='^(getprop [a-z0-9_.]+$|pm list packages -[de]$|cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME$|cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME$|dumpsys role$|dumpsys package [a-z0-9_.]+$|dumpsys wifi$|dumpsys connectivity$|settings get (secure|global) [a-z_]+$)'
DENY='(disable|enable|install|uninstall|clear|put |set-|setprop|reboot|rm |kill|force-stop|grant|revoke|am |input |svc |appops|reset|forget|>|;|&&|\|)'

run_ro() {   # run_ro <shell command>: prints the box's answer
  if ! printf '%s' "$1" | grep -Eq "$ALLOW" || printf '%s' "$1" | grep -Eq "$DENY"; then
    echo "REFUSÉ (hors liste lecture seule) : $1" >&2
    return 1
  fi
  "$ADB" -s "$SERIAL" shell "$1" 2>/dev/null | tr -d '\r'
}

STATE="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
if [ "$STATE" != "device" ]; then
  echo "Box non connectée ($SERIAL, état : ${STATE:-aucun}). Lancez d'abord, depuis Terminal.app :" >&2
  echo "  adb connect $SERIAL" >&2
  exit 2
fi

ok=0; warn=0
pass() { echo "  OK      $1"; ok=$((ok + 1)); }
fail() { echo "  ATTENTION  $1"; warn=$((warn + 1)); }

echo "Box : $(run_ro 'getprop ro.product.model') · build $(run_ro 'getprop ro.build.version.incremental') · Android $(run_ro 'getprop ro.build.version.release')"
[ "$(run_ro 'getprop ro.build.version.incremental')" = "773" ] && pass "build 773, celui de l'audit" || fail "build différent de 773 : relire le plan avant d'appliquer"

echo; echo "Launcher"
V="$(run_ro 'dumpsys package com.gathin.tvhome' | sed -n 's/.*versionName=//p' | head -n 1)"
[ -n "$V" ] && pass "com.gathin.tvhome installé, version $V" || fail "com.gathin.tvhome absent : rien à faire avant E1/E2"
# Android 11 has no "cmd role get-role-holders": read the holder from dumpsys role.
ROLE="$(run_ro 'dumpsys role' | grep -A1 'name=android.app.role.HOME$' | sed -n 's/.*holders=//p' | head -n 1)"
[ "$ROLE" = "com.gathin.tvhome" ] && pass "rôle HOME : com.gathin.tvhome" || fail "rôle HOME : '${ROLE:-aucun}' (attendu com.gathin.tvhome)"
echo "  info    Home répond aujourd'hui par : $(run_ro 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1)"
echo "  info    applis qui répondent à Home :"
run_ro 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
  | grep -E '^ +[A-Za-z0-9_.]+/' | sed 's/^ */            /'

echo; echo "Paquets"
DISABLED="$(run_ro 'pm list packages -d' | sed 's/^package://')"
ENABLED="$(run_ro 'pm list packages -e' | sed 's/^package://')"
state() { if printf '%s\n' "$ENABLED" | grep -qx "$1"; then echo actif; elif printf '%s\n' "$DISABLED" | grep -qx "$1"; then echo désactivé; else echo absent; fi; }
for p in com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith com.google.android.katniss com.google.android.apps.mediashell com.xiaomi.android.tvsetup.partnercustomizer com.google.android.apps.tv.dreamx; do
  printf '  info    %-46s %s\n' "$p" "$(state "$p")"
done
for p in com.google.android.tv.remote.service com.mi.bluetooth com.sfr.androidtv.launcher com.sfr.androidtv.selfcare com.netflix.ninja com.google.android.youtube.tv com.google.android.gms com.android.vending; do
  [ "$(state "$p")" = actif ] && pass "liste rouge actif : $p" || fail "liste rouge NON actif : $p"
done
echo "  info    paquets déjà désactivés : $(printf '%s\n' "$DISABLED" | sed '/^$/d' | wc -l | tr -d ' ')"

echo; echo "Réseau"
CONN="$(run_ro 'dumpsys connectivity')"
NETID="$(printf '%s\n' "$CONN" | sed -nE 's/.*Active default network: ([0-9]+).*/\1/p' | head -n 1)"
TRANSPORT=""
[ -n "$NETID" ] && TRANSPORT="$(printf '%s\n' "$CONN" | grep 'NetworkAgentInfo{' | grep " network{$NETID} " | grep -oE 'type: [A-Za-z]+' | head -n 1 | sed 's/type: //')"
echo "  info    réseau par défaut : ${TRANSPORT:-inconnu}"
OWNERS="$(run_ro 'dumpsys wifi' | grep -o 'cname=[^ ]*' | sort | uniq -c | sed 's/^ */            /' || true)"
if [ -z "$OWNERS" ]; then
  echo "  info    aucun réseau Wi-Fi enregistré lisible"
elif printf '%s\n' "$OWNERS" | grep -q setupwraith; then
  fail "un réseau Wi-Fi appartient à setupwraith : il serait supprimé à E4"
  printf '%s\n' "$OWNERS"
else
  pass "aucun réseau Wi-Fi n'appartient à setupwraith"
fi

echo; echo "Voix"
echo "  info    assistant : $(run_ro 'settings get secure assistant')"
echo "  info    voice_interaction_service : $(run_ro 'settings get secure voice_interaction_service')"
echo "  info    voice_recognition_service : $(run_ro 'settings get secure voice_recognition_service')"

echo; echo "Résultat : $ok OK, $warn point(s) à regarder. Rien n'a été modifié sur la box."
[ "$warn" -eq 0 ]
