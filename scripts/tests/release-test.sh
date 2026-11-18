#!/usr/bin/env bash
# Exercise the release entry point with a fake build, signer and publisher.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/repo/scripts" "$tmp/repo/android" "$tmp/bin" "$tmp/config"
cp "$root/scripts/release.sh" "$root/scripts/release-key.py" "$tmp/repo/scripts/"
printf '# Test release notes\n' > "$tmp/repo/README.md"
cat > "$tmp/repo/android/gradlew" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$RELEASE_TEST_LOG"
mkdir -p app/build/outputs/apk/release
printf 'signed-apk' > app/build/outputs/apk/release/app-release.apk
EOF
cat > "$tmp/bin/apksigner" <<'EOF'
#!/usr/bin/env bash
printf 'verify\n' >> "$RELEASE_TEST_LOG"
EOF
cat > "$tmp/bin/git" <<'EOF'
#!/usr/bin/env bash
printf 'abcdef0123456789abcdef0123456789abcdef01\n'
EOF
cat > "$tmp/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$RELEASE_TEST_LOG"
[[ "$*" == *'release create v1.2.3'* && "$*" == *'--notes-file'* ]]
EOF
chmod +x "$tmp/repo/android/gradlew" "$tmp/bin/"*
export PATH="$tmp/bin:$PATH" RELEASE_TEST_LOG="$tmp/log"
# Isolated external config: no real home or release service is used.
export REGIE_RELEASE_CONFIG="$tmp/config/release.properties"
# The key exists already, so this test never invokes keytool.
printf 'storeFile=%s\nstorePassword=fixture\nkeyAlias=regie\nkeyPassword=fixture\n' "$tmp/config/release.keystore" > "$REGIE_RELEASE_CONFIG"
touch "$tmp/config/release.keystore"
bash "$tmp/repo/scripts/release.sh" 1.2.3 --build-only
grep -q -- '-PregieVersionCode=1002004' "$tmp/log"
! grep -q 'release create' "$tmp/log"
bash "$tmp/repo/scripts/release.sh" 1.2.3
grep -q 'release create v1.2.3' "$tmp/log"
if bash "$tmp/repo/scripts/release.sh" '../bad'; then exit 1; fi
[[ -f "$tmp/repo/android/app/build/outputs/apk/release/regie-1.2.3.apk.sha256" ]]
echo 'release packaging: OK'
