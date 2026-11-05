#!/usr/bin/env bash
# Upload the release key to the repository's Actions secrets, for .github/workflows/release.yml.
# Run it yourself, from Terminal: values are read from ~/.mibox-launcher and sent through stdin, so they never
# appear on a command line, in shell history or in this repository.
# Usage: mibox/scripts/release/set-github-secrets.sh
set -euo pipefail
REPO=89hr474hht-rgb/mibox-launcher
PROPS="$HOME/.mibox-launcher/keystore.properties"
[ -f "$PROPS" ] || { echo "missing $PROPS" >&2; exit 1; }
prop() { sed -n "s/^$1=//p" "$PROPS" | tr -d '\r\n'; }
store="$(prop storeFile)"
[ -f "$store" ] || { echo "missing keystore $store" >&2; exit 1; }
base64 -i "$store" | tr -d '\n' | gh secret set MIBOX_KEYSTORE_BASE64 -R "$REPO"
prop storePassword | gh secret set MIBOX_KEYSTORE_PASSWORD -R "$REPO"
prop keyAlias | gh secret set MIBOX_KEY_ALIAS -R "$REPO"
prop keyPassword | gh secret set MIBOX_KEY_PASSWORD -R "$REPO"
gh secret list -R "$REPO"
