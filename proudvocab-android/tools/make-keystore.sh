#!/usr/bin/env bash
# Optional: create the signing key on your own computer instead of using the "Generate signing keystore" workflow.
# Needs a JDK (keytool). Prints the values to put into GitHub repository secrets.
set -euo pipefail
OUT="${1:-release.jks}"
PASS="$(openssl rand -hex 16)"
keytool -genkeypair -v -keystore "$OUT" -alias proudvocab -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=ProudVocab, O=ProudVocab, C=US"
echo
echo "Add these repository secrets:"
echo "KEY_ALIAS=proudvocab"
echo "KEYSTORE_PASSWORD=$PASS"
echo "KEY_PASSWORD=$PASS"
echo "KEYSTORE_BASE64 = (contents of the next line)"
base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
echo "Back up $OUT somewhere safe."
