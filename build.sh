#!/usr/bin/env bash
set -euo pipefail

PROJ_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_NAME="${OUT_NAME:-android-credential-switcher-v1.0.0.apk}"

find_sdk() {
  for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [[ -n "$dir" && -d "$dir/platforms" && -d "$dir/build-tools" ]]; then
      echo "$dir"
      return 0
    fi
  done
  echo "Error: Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
}

SDK_DIR="$(find_sdk)"

ANDROID_JAR=""
while IFS= read -r plat; do
  if [[ -f "$plat/android.jar" ]]; then
    ANDROID_JAR="$plat/android.jar"
    break
  fi
done < <(find "$SDK_DIR/platforms" -maxdepth 1 -type d -name 'android-*' | sort -rV)

if [[ -z "$ANDROID_JAR" ]]; then
  echo "Error: No Android platform with android.jar found under $SDK_DIR/platforms" >&2
  exit 1
fi

BT_DIR="$(find "$SDK_DIR/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -rV | head -n 1)"
if [[ -z "$BT_DIR" ]]; then
  echo "Error: No build-tools found under $SDK_DIR/build-tools" >&2
  exit 1
fi

AAPT2="$BT_DIR/aapt2"
D8="$BT_DIR/d8"
ZIPALIGN="$BT_DIR/zipalign"
APKSIGNER="$BT_DIR/apksigner"

echo "Using Android JAR: $ANDROID_JAR"
echo "Using Build-Tools: $BT_DIR"

BUILD_DIR="$PROJ_DIR/build"
DIST_DIR="$PROJ_DIR/dist"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$DIST_DIR"

"$AAPT2" compile --dir "$PROJ_DIR/res" -o "$BUILD_DIR/res.zip"
"$AAPT2" link -o "$BUILD_DIR/base.apk" -I "$ANDROID_JAR" \
  --manifest "$PROJ_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/gen" \
  --auto-add-overlay "$BUILD_DIR/res.zip"

JAVA_SRCS=()
while IFS= read -r f; do JAVA_SRCS+=("$f"); done < <(find "$PROJ_DIR/src" "$BUILD_DIR/gen" -name '*.java')

javac -encoding UTF-8 --release 11 -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" "${JAVA_SRCS[@]}"

CLASS_FILES=()
while IFS= read -r f; do CLASS_FILES+=("$f"); done < <(find "$BUILD_DIR/classes" -name '*.class')

"$D8" --min-api 29 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "${CLASS_FILES[@]}"

(cd "$BUILD_DIR/dex" && zip -u "$BUILD_DIR/base.apk" classes.dex)

ALIGNED_APK="$BUILD_DIR/aligned.apk"
"$ZIPALIGN" -p -f 4 "$BUILD_DIR/base.apk" "$ALIGNED_APK"

KEYSTORE_PATH="${KEYSTORE_PATH:-$PROJ_DIR/debug.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KEY_PASS="${KEY_PASS:-android}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  keytool -genkeypair -v -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASS" -alias "$KEY_ALIAS" -keypass "$KEY_PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Credential Switcher, OU=OpenSource, O=Community, L=Local, ST=Local, C=US"
fi

OUT_APK="$DIST_DIR/$OUT_NAME"
"$APKSIGNER" sign \
  --ks "$KEYSTORE_PATH" \
  --ks-pass "pass:$KEYSTORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --key-pass "pass:$KEY_PASS" \
  --out "$OUT_APK" "$ALIGNED_APK"

"$APKSIGNER" verify --print-certs "$OUT_APK"

SIZE=$(wc -c < "$OUT_APK" | tr -d ' ')
echo "BUILD SUCCESS: $OUT_APK ($SIZE bytes)"
