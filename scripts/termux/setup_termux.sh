#!/usr/bin/env bash
# ============================================================================
# Vortex Engine v2.0 — Smart Termux Toolchain Setup (Android ARM64 / ARM / x86_64)
# Skips already-installed tools so re-running finishes in ~1 second!
# ============================================================================
set -e

echo "╔══════════════════════════════════════════════════════════╗"
echo "║   Vortex Engine v2.0 — Termux APK Toolchain Setup        ║"
echo "╚══════════════════════════════════════════════════════════╝"

mkdir -p "$HOME/.termux"
PROPS="$HOME/.termux/termux.properties"
touch "$PROPS"
if grep -qE "^[[:space:]]*#?[[:space:]]*allow-external-apps[[:space:]]*=" "$PROPS"; then
    sed -i 's/^[[:space:]]*#*[[:space:]]*allow-external-apps[[:space:]]*=.*/allow-external-apps = true/' "$PROPS"
else
    echo "" >> "$PROPS"
    echo "allow-external-apps = true" >> "$PROPS"
fi
if command -v termux-reload-settings >/dev/null 2>&1; then
    termux-reload-settings || true
fi
echo "[✓] Configured allow-external-apps = true"

if command -v termux-setup-storage >/dev/null 2>&1 && [ ! -d "$HOME/storage" ]; then
    echo "[*] Requesting shared storage access..."
    termux-setup-storage || true
fi

NEED_INSTALL=0
for tool in javac aapt2 zipalign apksigner zip unzip; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        NEED_INSTALL=1
    fi
done

if [ "$NEED_INSTALL" -eq 1 ] && command -v pkg >/dev/null 2>&1; then
    echo "[*] Installing missing Android APK build packages..."
    pkg update -y
    # apksigner depends on openjdk-21 in Termux, avoiding duplicate JDK downloads!
    pkg install -y openjdk-21 aapt aapt2 apksigner zip unzip || \
    pkg install -y openjdk-17 aapt aapt2 apksigner zip unzip
    if ! command -v d8 >/dev/null 2>&1 && ! command -v dx >/dev/null 2>&1; then
        pkg install -y d8 2>/dev/null || pkg install -y dx 2>/dev/null || true
    fi
else
    echo "[✓] Termux build tools (javac, aapt2, zipalign, apksigner) already installed!"
fi

SDK_CACHE="$HOME/.vortex-sdk"
mkdir -p "$SDK_CACHE"
KEYSTORE="$SDK_CACHE/vortex-release.keystore"
if [ ! -f "$KEYSTORE" ] && command -v keytool >/dev/null 2>&1; then
    echo "[*] Generating PKCS12 release signing keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storetype PKCS12 \
        -alias vortexkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass vortex123 \
        -keypass vortex123 \
        -dname "CN=Vortex Engine Developer, OU=Mobile Games, O=VortexEngine, L=Studio, S=Mobile, C=US"
fi

if [ -d "/sdcard/Download" ] && [ -w "/sdcard/Download" ]; then
    mkdir -p "/sdcard/Download/VortexEngine"
    date > "/sdcard/Download/VortexEngine/.toolchain_ready" 2>/dev/null || true
fi

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║ [SUCCESS] Termux APK Toolchain is 100% READY!            ║"
echo "║ Tip: For 1-tap background builds without copy/paste,     ║"
echo "║ grant 'Run commands in Termux environment' in Android    ║"
echo "║ Settings -> Apps -> Vortex Engine -> Permissions.        ║"
echo "╚══════════════════════════════════════════════════════════╝"
