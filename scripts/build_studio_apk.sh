#!/usr/bin/env bash
# ============================================================================
# Vortex Engine — Full Studio APK & Bundled Runtime Toolchain Builder
# Produces:
#   - app/src/main/assets/toolchain/vortex-runtime.dex
#   - app/src/main/assets/toolchain/vortex-runtime-src.zip
#   - app/src/main/assets/toolchain/build_termux.sh & setup_termux.sh
#   - dist/VortexEngine-Studio-v1.0.0.apk (aligned & v1+v2+v3 signed)
# ============================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"

BUILD_TOOLS_DIR=$(find "$ANDROID_SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-34/android.jar"

AAPT2="$BUILD_TOOLS_DIR/aapt2"
D8="$BUILD_TOOLS_DIR/d8"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

WORK="/tmp/vortex-studio-build"
rm -rf "$WORK"
mkdir -p "$WORK"/{rt_classes,rt_dex,rt_src_stage,gen,studio_classes,studio_dex}
mkdir -p "$PROJECT_ROOT/app/src/main/assets/toolchain" "$PROJECT_ROOT/dist"

echo "=== [1/6] Building Standalone 2D/3D Engine Runtime DEX (vortex-runtime.dex) ==="
find app/src/main/java/com/vortex/engine/core app/src/main/java/com/vortex/engine/runtime -name "*.java" > "$WORK/rt_sources.txt"
javac --release 8 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/rt_classes" @"$WORK/rt_sources.txt"
(cd "$WORK/rt_classes" && zip -qr "$WORK/rt_classes.jar" .)
"$D8" --min-api 24 --lib "$ANDROID_JAR" --output "$WORK/rt_dex" "$WORK/rt_classes.jar"
cp "$WORK/rt_dex/classes.dex" "$PROJECT_ROOT/app/src/main/assets/toolchain/vortex-runtime.dex"

echo "=== [2/6] Packaging Standalone Engine Java Sources & Termux Build Scripts ==="
mkdir -p "$WORK/rt_src_stage/src/main/java/com/vortex/engine"
cp -R app/src/main/java/com/vortex/engine/core "$WORK/rt_src_stage/src/main/java/com/vortex/engine/"
cp -R app/src/main/java/com/vortex/engine/runtime "$WORK/rt_src_stage/src/main/java/com/vortex/engine/"
rm -f "$PROJECT_ROOT/app/src/main/assets/toolchain/vortex-runtime-src.zip"
(cd "$WORK/rt_src_stage" && zip -qr "$PROJECT_ROOT/app/src/main/assets/toolchain/vortex-runtime-src.zip" src)

cp scripts/termux/build_termux.sh "$PROJECT_ROOT/app/src/main/assets/toolchain/build_termux.sh"
cp scripts/termux/setup_termux.sh "$PROJECT_ROOT/app/src/main/assets/toolchain/setup_termux.sh"

echo "=== [3/6] Compiling Vortex Studio Resources & Assets with aapt2 ==="
"$AAPT2" compile --dir app/src/main/res -o "$WORK/res.zip"
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    -A app/src/main/assets \
    --java "$WORK/gen" \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --auto-add-overlay \
    -o "$WORK/studio-unaligned.apk" \
    "$WORK/res.zip"

echo "=== [4/6] Compiling Vortex Studio IDE + 2D/3D Engine Java Sources ==="
find app/src/main/java "$WORK/gen" -name "*.java" > "$WORK/studio_sources.txt"
javac --release 11 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/studio_classes" @"$WORK/studio_sources.txt"
(cd "$WORK/studio_classes" && zip -qr "$WORK/studio_classes.jar" .)
"$D8" --min-api 24 --lib "$ANDROID_JAR" --output "$WORK/studio_dex" "$WORK/studio_classes.jar"

cp "$WORK/studio_dex/classes.dex" "$WORK/classes.dex"
(cd "$WORK" && zip -qj studio-unaligned.apk classes.dex)

echo "=== [5/6] Aligning & Signing VortexEngine-Studio-v1.0.0.apk ==="
"$ZIPALIGN" -f -p 4 "$WORK/studio-unaligned.apk" "$WORK/studio-aligned.apk"

KEYSTORE="/tmp/vortex-studio-signing.p12"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storetype PKCS12 \
        -alias vortexstudio \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass vortex123 \
        -keypass vortex123 \
        -dname "CN=Vortex Engine Studio, OU=GameEngine, O=VortexEngine, L=Mobile, S=Android, C=US" >/dev/null 2>&1
fi

OUT_APK="$PROJECT_ROOT/dist/VortexEngine-Studio-v1.0.0.apk"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:vortex123 \
    --key-pass pass:vortex123 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT_APK" \
    "$WORK/studio-aligned.apk"

echo "=== [6/6] Verifying Signed APK ==="
"$APKSIGNER" verify --verbose "$OUT_APK" | head -n 12
"$AAPT2" dump badging "$OUT_APK" | head -n 6
ls -lh "$OUT_APK"
