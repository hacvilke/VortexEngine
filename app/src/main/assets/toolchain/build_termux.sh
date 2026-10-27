#!/usr/bin/env bash
# ============================================================================
# Vortex Engine v2.0 — Standalone Game APK Builder (LuaJ + JBox2D + FastNoiseLite + GLES)
# Usage:
#   bash build_termux.sh [--from-source | --fast] [--zip /path/to/bundle.zip]
# ============================================================================
set -euo pipefail

MODE="auto"
ZIP_BUNDLE=""
CUSTOM_OUT_DIR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --from-source) MODE="source"; shift ;;
        --fast)        MODE="fast"; shift ;;
        --zip)         ZIP_BUNDLE="$2"; shift 2 ;;
        --out-dir)     CUSTOM_OUT_DIR="$2"; shift 2 ;;
        *)             shift ;;
    esac
done

echo "============================================================"
echo "  Vortex Engine v4.0 APK Builder (ES6 JS Rhino + LuaJ + Box2D)"
echo "============================================================"

find_tool() {
    local name="$1"
    if command -v "$name" >/dev/null 2>&1; then
        command -v "$name"
        return 0
    fi
    if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
        local found
        found=$(find "$ANDROID_SDK_ROOT/build-tools" -name "$name" 2>/dev/null | sort -V | tail -n 1 || true)
        if [ -n "$found" ] && [ -x "$found" ]; then
            echo "$found"
            return 0
        fi
    fi
    return 1
}

AAPT2_BIN=$(find_tool aapt2 || true)
ZIPALIGN_BIN=$(find_tool zipalign || true)
APKSIGNER_BIN=$(find_tool apksigner || true)
JAVAC_BIN=$(command -v javac || true)
KEYTOOL_BIN=$(command -v keytool || true)
D8_BIN=$(find_tool d8 || true)
DX_BIN=$(find_tool dx || true)

if [ -z "$AAPT2_BIN" ] || [ -z "$ZIPALIGN_BIN" ] || [ -z "$APKSIGNER_BIN" ]; then
    echo "[!] Missing Android build tools (aapt2 / zipalign / apksigner)."
    echo "    In Termux, run: pkg install -y openjdk-21 aapt aapt2 d8 apksigner zip unzip"
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_PROJECT_DIR="$SCRIPT_DIR"

if [ -n "$ZIP_BUNDLE" ] && [ -f "$ZIP_BUNDLE" ]; then
    WORK_ROOT="${HOME}/.vortex-build/from_zip_$(date +%s)"
    mkdir -p "$WORK_ROOT"
    unzip -q -o "$ZIP_BUNDLE" -d "$WORK_ROOT"
    SRC_PROJECT_DIR="$WORK_ROOT"
fi

if [ ! -f "$SRC_PROJECT_DIR/AndroidManifest.xml" ]; then
    echo "[!] AndroidManifest.xml not found in $SRC_PROJECT_DIR"
    exit 3
fi

SLUG=$(basename "$SRC_PROJECT_DIR" | tr -cd 'a-zA-Z0-9_-')
[ -z "$SLUG" ] && SLUG="vortex_game"

WORK_DIR="${VORTEX_WORK_DIR:-$HOME/.vortex-build/$SLUG}"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cp -R "$SRC_PROJECT_DIR"/. "$WORK_DIR"/
cd "$WORK_DIR"
mkdir -p build/gen build/classes build/dex dist

ANDROID_JAR="${ANDROID_JAR:-}"
if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
    for candidate in \
        "${ANDROID_SDK_ROOT:-/nonexistent}/platforms/android-34/android.jar" \
        "${PREFIX:-/data/data/com.termux/files/usr}/share/aapt/android.jar" \
        "$HOME/.vortex-sdk/android.jar" \
        "/system/framework/framework-res.apk"; do
        if [ -f "$candidate" ]; then
            ANDROID_JAR="$candidate"
            break
        fi
    done
fi

if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "[!] Could not locate android.jar or /system/framework/framework-res.apk"
    exit 4
fi
echo "[1/6] Using Android framework: $ANDROID_JAR"

echo "[2/6] Compiling resources with aapt2..."
"$AAPT2_BIN" compile --dir res -o build/resources.zip

echo "[3/6] Linking APK manifest & 2D/3D game assets..."
"$AAPT2_BIN" link \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    --java build/gen \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --auto-add-overlay \
    -o build/unaligned.apk \
    build/resources.zip

CAN_COMPILE_JAVA="false"
if [ -n "$JAVAC_BIN" ] && [ -n "$D8_BIN" ] && [[ "$ANDROID_JAR" == *.jar ]]; then
    if [ -d "src/main/java" ] && [ -n "$(find src/main/java -name '*.java' -print -quit 2>/dev/null)" ]; then
        CAN_COMPILE_JAVA="true"
    fi
fi

EXTRA_CP="$ANDROID_JAR"
EXTRA_JARS=()
if [ -d "libs" ]; then
    for jf in libs/*.jar; do
        if [ -f "$jf" ]; then
            EXTRA_CP="$EXTRA_CP:$jf"
            EXTRA_JARS+=("$jf")
        fi
    done
fi

if [ "$MODE" = "source" ] && [ "$CAN_COMPILE_JAVA" = "true" ]; then
    echo "[4/6] Compiling Engine + Mozilla Rhino ES6 + LuaJ + JBox2D Java sources from scratch..."
    find src/main/java build/gen -name "*.java" > build/sources.txt
    "$JAVAC_BIN" --release 8 -encoding UTF-8 -cp "$EXTRA_CP" -d build/classes @build/sources.txt
    (cd build/classes && zip -qr ../classes.jar .)
    "$D8_BIN" --min-api 24 --lib "$ANDROID_JAR" --output build/dex build/classes.jar "${EXTRA_JARS[@]}"
    cp build/dex/classes*.dex "$WORK_DIR/"
elif [ -f "prebuilt/vortex-runtime.dex" ]; then
    echo "[4/6] Injecting prebuilt Vortex v4.0 Runtime DEX (GLES + Rhino ES6 + LuaJ + JBox2D + AI + Tween)..."
    cp prebuilt/vortex-runtime.dex "$WORK_DIR/classes.dex"
    [ -f "prebuilt/vortex-runtime-2.dex" ] && cp prebuilt/vortex-runtime-2.dex "$WORK_DIR/classes2.dex"
elif [ "$CAN_COMPILE_JAVA" = "true" ]; then
    echo "[4/6] Compiling Engine Java sources..."
    find src/main/java build/gen -name "*.java" > build/sources.txt
    "$JAVAC_BIN" --release 8 -encoding UTF-8 -cp "$EXTRA_CP" -d build/classes @build/sources.txt
    (cd build/classes && zip -qr ../classes.jar .)
    "$D8_BIN" --min-api 24 --lib "$ANDROID_JAR" --output build/dex build/classes.jar "${EXTRA_JARS[@]}"
    cp build/dex/classes*.dex "$WORK_DIR/"
else
    echo "[!] Neither prebuilt/vortex-runtime.dex nor full Java SDK compiler available."
    exit 5
fi

zip -qj build/unaligned.apk classes*.dex

echo "[5/6] Optimizing alignment with zipalign..."
"$ZIPALIGN_BIN" -f -p 4 build/unaligned.apk build/aligned.apk

SDK_CACHE="$HOME/.vortex-sdk"
mkdir -p "$SDK_CACHE"
KEYSTORE="${VORTEX_KEYSTORE:-$SDK_CACHE/vortex-release.keystore}"
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL_BIN" -genkeypair \
        -keystore "$KEYSTORE" \
        -storetype PKCS12 \
        -alias vortexkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass vortex123 \
        -keypass vortex123 \
        -dname "CN=Vortex Game, OU=Engine, O=Vortex, L=Mobile, S=Android, C=US" >/dev/null 2>&1
fi

FINAL_APK="$WORK_DIR/dist/${SLUG}-release.apk"
echo "[6/6] Signing standalone APK (v1 + v2 + v3 schemes)..."
"$APKSIGNER_BIN" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:vortex123 \
    --key-pass pass:vortex123 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$FINAL_APK" \
    build/aligned.apk

EXPORT_DEST=""
if [ -n "$CUSTOM_OUT_DIR" ]; then
    mkdir -p "$CUSTOM_OUT_DIR"
    cp "$FINAL_APK" "$CUSTOM_OUT_DIR/${SLUG}-release.apk"
    EXPORT_DEST="$CUSTOM_OUT_DIR/${SLUG}-release.apk"
elif [ -d "/sdcard/Download" ] && [ -w "/sdcard/Download" ]; then
    mkdir -p "/sdcard/Download/VortexEngine/Builds"
    cp "$FINAL_APK" "/sdcard/Download/VortexEngine/Builds/${SLUG}-release.apk"
    EXPORT_DEST="/sdcard/Download/VortexEngine/Builds/${SLUG}-release.apk"
fi

if [ -w "$SRC_PROJECT_DIR" ]; then
    mkdir -p "$SRC_PROJECT_DIR/dist" 2>/dev/null || true
    cp "$FINAL_APK" "$SRC_PROJECT_DIR/dist/${SLUG}-release.apk" 2>/dev/null || true
fi

APK_SIZE=$(du -h "$FINAL_APK" | cut -f1)
echo "============================================================"
echo "[SUCCESS] Built Standalone Game APK ($APK_SIZE)!"
echo "  Workspace APK : $FINAL_APK"
[ -n "$EXPORT_DEST" ] && echo "  Shared APK    : $EXPORT_DEST"
echo "============================================================"

if [ -n "$EXPORT_DEST" ] && command -v termux-open >/dev/null 2>&1 && [ "${VORTEX_NO_OPEN:-0}" != "1" ]; then
    termux-open "$EXPORT_DEST" 2>/dev/null || true
fi
