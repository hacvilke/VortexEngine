#!/usr/bin/env bash
# ============================================================================
# Vortex Engine — One-Step Termux Toolchain Installer (Android ARM64 / ARM / x86_64)
# Installs native Termux APK build tools & enables RUN_COMMAND external app access.
# ============================================================================
set -e

echo "╔══════════════════════════════════════════════════════════╗"
echo "║   Vortex Engine — Termux APK Toolchain Setup v1.0.0      ║"
echo "╚══════════════════════════════════════════════════════════╝"

# 1. Enable allow-external-apps=true so Vortex Studio can trigger builds directly
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
echo "[✓] Enabled allow-external-apps = true in ~/.termux/termux.properties"

# 2. Request shared storage symlink if running interactively in Termux
if command -v termux-setup-storage >/dev/null 2>&1 && [ ! -d "$HOME/storage" ]; then
    echo "[*] Requesting shared storage access (tap Allow if prompted)..."
    termux-setup-storage || true
fi

# 3. Install native Termux build packages
if command -v pkg >/dev/null 2>&1; then
    echo "[*] Updating Termux package index..."
    pkg update -y
    echo "[*] Installing OpenJDK, aapt, aapt2, d8/dx, apksigner, zipalign..."
    pkg install -y openjdk-17 aapt aapt2 apksigner zip unzip curl || \
    pkg install -y openjdk-21 aapt aapt2 apksigner zip unzip curl
    # Optional dexers depending on Termux mirror state
    pkg install -y d8 2>/dev/null || true
    pkg install -y dx 2>/dev/null || true
fi

# 4. Prepare reusable signing keystore for Android 11-16 v1/v2/v3 signing
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

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║ [SUCCESS] Termux APK Build Toolchain is READY!           ║"
echo "║ Return to Vortex Engine Studio and tap 'Build Game APK'. ║"
echo "╚══════════════════════════════════════════════════════════╝"
