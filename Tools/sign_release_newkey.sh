#!/usr/bin/env bash
# PrimeGram: re-signs a release APK with PrimeGram's own key ONLY - no old upstream key, no lineage.
# This is what the GitHub workflow now ships. A copy still signed by the old key (Android 7-8, or
# older than the rotated 13.0.2.2) cannot update over the result; the rotated builds from
# sign_release_rotated.sh can. Secrets come from the gitignored local.properties or the environment:
#   PRIME_KEYSTORE_FILE, PRIME_KEYSTORE_PASSWORD, PRIME_KEY_ALIAS, PRIME_KEY_PASSWORD
#
# Usage: Tools/sign_release_newkey.sh [path/to.apk]   (defaults to the afat release APK)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/TMessagesProj_AppStandalone/build/outputs/apk/afat/release/TMessagesProj_AppStandalone-afat-release.apk}"

prop() {
    local v=""
    if [[ "$1" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then v="${!1:-}"; fi
    if [ -z "$v" ] && [ -f "$2" ]; then v="$(grep "^$1=" "$2" | head -1 | cut -d= -f2- | tr -d '\r')"; fi
    printf '%s' "$v"
}

LOCAL="$ROOT/local.properties"
KS="$(prop PRIME_KEYSTORE_FILE "$LOCAL")"
ALIAS="$(prop PRIME_KEY_ALIAS "$LOCAL")"
export NEW_PASS="$(prop PRIME_KEYSTORE_PASSWORD "$LOCAL")"
export NEW_KEYPASS="$(prop PRIME_KEY_PASSWORD "$LOCAL")"
[ -n "$NEW_KEYPASS" ] || NEW_KEYPASS="$NEW_PASS"
for v in KS ALIAS NEW_PASS; do
    if [ -z "${!v}" ]; then echo "missing $v (env or local.properties)" >&2; exit 1; fi
done

BT="${ANDROID_BUILD_TOOLS:-}"
if [ -z "$BT" ]; then
    SDK="${ANDROID_HOME:-$(prop sdk.dir "$LOCAL" | sed -e 's/\\:/:/g' -e 's/\\\\/\//g')}"
    BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
fi
APKSIGNER="$BT/apksigner"
[ -f "$APKSIGNER.bat" ] && APKSIGNER="$APKSIGNER.bat"

OUT="${APK%.apk}-newkey.apk"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" --ks-pass env:NEW_PASS --key-pass env:NEW_KEYPASS \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$OUT" "$APK"
"$APKSIGNER" verify --verbose --print-certs "$OUT" | grep -iE "Verified using|Signer|SHA-256" || true
echo "signed: $OUT"
