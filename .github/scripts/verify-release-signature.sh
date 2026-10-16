#!/usr/bin/env bash
# Verifies the signature of a release APK. Usage:
#   KEYSTORE_PASSWORD=... verify-release-signature.sh <apk> <keystore> <key alias>
# Fails unless: apksigner verifies the APK (real exit code checked) for its whole minSdk..max range,
# with exactly one signer, using APK Signature Scheme v2 or v3; the certificate is not an Android
# Debug certificate; and its SHA-256 equals the certificate of <key alias> in <keystore> (the
# permanent release key from the repository secrets). Passwords are read from the environment and
# never printed; the certificate DN and SHA-256 are public parts of any signed APK.
set -uo pipefail
apk="$1"
keystore="$2"
alias_name="$3"
build_tools="$(ls -d "${ANDROID_HOME}"/build-tools/* | sort -V | tail -1)"
report="$(mktemp)"

"${build_tools}/apksigner" verify --verbose --print-certs "${apk}" > "${report}" 2>&1
status=$?
if [ "${status}" -ne 0 ]; then
  cat "${report}"
  echo "::error::$(basename "${apk}"): apksigner verify failed (exit ${status}); the APK is unsigned or its signature is invalid"
  exit 1
fi

grep -E "^Verifies$|^Verified using v[0-9.]+ scheme .*: true$|^Number of signers:" "${report}"
# apksigner prints "Signer #1 certificate DN: ..." (older build-tools) or
# "V2 Signer: certificate DN: ..." (newer); accept either, require both lines.
dn=$(grep -m1 -E "certificate DN: " "${report}" | sed -E 's/.*certificate DN: //')
apk_sha256=$(grep -m1 -E "certificate SHA-256 digest: " "${report}" | sed -E 's/.*SHA-256 digest: //' | tr -d ': ' | tr 'A-F' 'a-f')
signers=$(grep -m1 -E "^Number of signers: " "${report}" | sed -E 's/.*: //')
echo "Certificate DN: ${dn}"
echo "Certificate SHA-256: ${apk_sha256}"

if [ -z "${dn}" ] || [ -z "${apk_sha256}" ]; then
  echo "::error::$(basename "${apk}"): no signer certificate in the apksigner output"
  exit 1
fi
if [ "${signers}" != "1" ]; then
  echo "::error::$(basename "${apk}"): expected exactly 1 signer, found '${signers}'"
  exit 1
fi
if ! grep -qE "^Verified using v(2|3) scheme .*: true$" "${report}"; then
  echo "::error::$(basename "${apk}"): not signed with APK Signature Scheme v2 or v3"
  exit 1
fi

key_sha256=$(keytool -list -v -keystore "${keystore}" -storepass:env KEYSTORE_PASSWORD -alias "${alias_name}" 2>/dev/null \
  | grep -m1 -E "^\s*SHA256: " | sed -E 's/.*SHA256: //' | tr -d ': ' | tr 'A-F' 'a-f')
if [ -z "${key_sha256}" ]; then
  echo "::error::Could not read the release certificate from the keystore (alias or password mismatch)"
  exit 1
fi
if [ "${apk_sha256}" != "${key_sha256}" ]; then
  echo "::error::$(basename "${apk}"): signed with a certificate that is not the release key from the secrets"
  exit 1
fi
echo "Certificate matches the release key in the keystore."

if echo "${dn}" | grep -q "CN=Android Debug"; then
  echo "::error::$(basename "${apk}"): signed with an Android Debug certificate"
  exit 1
fi
echo "Signature OK: $(basename "${apk}")"
