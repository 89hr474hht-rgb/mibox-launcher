#!/usr/bin/env bash
# Uninstall the abandoned third-party launcher com.razorbill.launcher from the Mi Box.
#
#   scripts/maintenance/uninstall-razorbill.sh <box-ip>[:port]             dry run: checks + backup, changes nothing
#   scripts/maintenance/uninstall-razorbill.sh <box-ip>[:port] --execute   same checks, then asks you to type
#                                                                          DESINSTALLER before uninstalling
#
# Only ONE command modifies the box: `pm uninstall com.razorbill.launcher` (a user-installed app; no system
# package is touched). Everything else is read-only. The APK is pulled to audits/ first, so the app can be
# reinstalled with `adb install` (its settings and data are not kept).
set -euo pipefail

HOST="${1:?usage: uninstall-razorbill.sh <box-ip>[:port] [--execute]}"
MODE="${2:-dry-run}"
case "$HOST" in *:*) SERIAL="$HOST" ;; *) SERIAL="$HOST:5555" ;; esac
PKG=com.razorbill.launcher

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
OUT="$ROOT/audits/$(date +%Y%m%d-%H%M%S)-razorbill"
mkdir -p "$OUT"
LOG="$OUT/log.txt"
log() { printf '%s  %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$LOG"; }
# </dev/null: adb shell would otherwise swallow the script's stdin, including the typed confirmation.
sh_ro() { adb -s "$SERIAL" shell "$@" < /dev/null | tr -d '\r'; }
fail() { log "ARRÊT : $*"; log "Aucune modification n'a été faite."; exit 1; }

adb connect "$SERIAL" </dev/null >/dev/null 2>&1 || true
[ "$(adb -s "$SERIAL" get-state 2>/dev/null || true)" = device ] || fail "box injoignable sur $SERIAL"
[ "$(sh_ro getprop ro.product.model)" = "MiTV-AFKR0" ] || fail "ce n'est pas la Mi Box MiTV-AFKR0"

# ---- 1. Pre-checks (read-only) -------------------------------------------------------------
log "1. Vérifications préalables (lecture seule)"
sh_ro pm list packages "$PKG" | grep -qx "package:$PKG" || fail "$PKG n'est pas installé (déjà désinstallé ?)"
sh_ro pm list packages -s | grep -qx "package:$PKG" && fail "$PKG est un package système : ce script ne le touche pas"
sh_ro pm list packages -3 | grep -qx "package:$PKG" || fail "$PKG n'apparaît pas comme application tierce"

HOME_NOW="$(sh_ro cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
log "   HOME affiché actuellement : $HOME_NOW"
case "$HOME_NOW" in "$PKG"/*) fail "Razorbill affiche actuellement l'accueil : remettez d'abord un autre accueil" ;; esac

for key in enabled_accessibility_services enabled_input_methods default_input_method \
           enabled_notification_listeners screensaver_components screensaver_default_component; do
  v="$(sh_ro settings get secure "$key")"
  case "$v" in *"$PKG"*) fail "Razorbill est utilisé par le réglage $key=$v" ;; esac
done
log "   Aucun service d'accessibilité, clavier, écouteur de notifications ni écran de veille Razorbill"

sh_ro dumpsys device_policy | grep -q "$PKG" && fail "Razorbill est administrateur de l'appareil"
log "   Razorbill n'est pas administrateur de l'appareil"

ROLE_BEFORE="$(sh_ro dumpsys role | grep -A1 'name=android.app.role.HOME' | sed -n 's/.*holders=//p' | head -1)"
log "   Rôle HOME (préférence Android) avant : ${ROLE_BEFORE:-aucun}"
sh_ro dumpsys package preferred > "$OUT/preferred-before.txt"

# ---- 2. Backup of the APK (read-only on the box) ------------------------------------------
log "2. Sauvegarde de l'APK"
APK="$(sh_ro pm path "$PKG" | sed -n 's/^package://p' | head -1)"
[ -n "$APK" ] || fail "chemin de l'APK introuvable"
adb -s "$SERIAL" pull "$APK" "$OUT/$PKG.apk" </dev/null >/dev/null || fail "copie de l'APK impossible"
( cd "$OUT" && shasum -a 256 "$PKG.apk" > "$PKG.apk.sha256" )
log "   APK sauvegardé : $OUT/$PKG.apk ($(cut -d' ' -f1 "$OUT/$PKG.apk.sha256"))"

if [ "$MODE" != "--execute" ]; then
  log "Mode simulation : toutes les vérifications sont passées. La seule commande qui serait envoyée est :"
  log "   pm uninstall $PKG"
  log "Relancez avec --execute pour désinstaller."
  exit 0
fi

# ---- 3. Uninstall (the only modifying command) ---------------------------------------------
printf 'Tapez DESINSTALLER pour supprimer %s de la Mi Box : ' "$PKG"
answer=""
read -r answer || true
echo
[ "$answer" = "DESINSTALLER" ] || fail "confirmation non donnée"
log "3. Envoi de : pm uninstall $PKG"
RESULT="$(sh_ro pm uninstall "$PKG")"
log "   Réponse de la box : $RESULT"
[ "$RESULT" = "Success" ] || { log "La désinstallation a échoué ; rien d'autre n'a été modifié."; exit 1; }

# ---- 4. Post-checks (read-only) -------------------------------------------------------------
log "4. Vérifications après désinstallation"
sh_ro pm list packages "$PKG" | grep -q "$PKG" && log "   ATTENTION : le package est toujours listé" || log "   Package absent : OK"
log "   HOME affiché maintenant : $(sh_ro cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
log "   Rôle HOME après : $(sh_ro dumpsys role | grep -A1 'name=android.app.role.HOME' | sed -n 's/.*holders=//p' | head -1)"
sh_ro dumpsys package preferred > "$OUT/preferred-after.txt"
log "À tester par vous : bouton Home -> Google TV, puis lancer SFR TV, Netflix et YouTube."
log "Réinstallation possible : adb -s $SERIAL install \"$OUT/$PKG.apk\""
