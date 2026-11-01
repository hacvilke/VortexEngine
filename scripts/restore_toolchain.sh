#!/usr/bin/env bash
# Restores the Linux build toolchain used by scripts/build_studio_apk.sh and build_desktop.sh.
# Safe to re-run: skips anything already present. Needs sudo.
#
#   bash scripts/restore_toolchain.sh
#
# Installs: Temurin JDK 17 (/opt/jdk-17), Android SDK platform-34 + build-tools 34.0.0
# (/opt/android-sdk), plus mingw-w64 and xvfb from apt for the Windows .exe and GUI screenshots.
set -euo pipefail

JDK_DIR=/opt/jdk-17
SDK_DIR=/opt/android-sdk
JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

if [ ! -x "$JDK_DIR/bin/javac" ]; then
    echo "=== installing JDK 17 -> $JDK_DIR"
    curl -sSL -o /tmp/jdk17.tar.gz "$JDK_URL"
    sudo mkdir -p "$JDK_DIR"
    sudo tar xzf /tmp/jdk17.tar.gz -C "$JDK_DIR" --strip-components=1
    sudo chown -R "$(id -u):$(id -g)" "$JDK_DIR"
fi

if [ ! -d "$SDK_DIR/platforms/android-34" ] || [ ! -d "$SDK_DIR/build-tools/34.0.0" ]; then
    echo "=== installing Android SDK -> $SDK_DIR"
    if [ ! -d "$SDK_DIR/cmdline-tools/latest/bin" ]; then
        curl -sSL -o /tmp/cmdtools.zip "$CMDTOOLS_URL"
        sudo mkdir -p "$SDK_DIR/cmdline-tools"
        sudo unzip -q -o /tmp/cmdtools.zip -d "$SDK_DIR/cmdline-tools"
        sudo mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    fi
    sudo chown -R "$(id -u):$(id -g)" "$SDK_DIR"
    export JAVA_HOME="$JDK_DIR"
    yes 2>/dev/null | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
        "platforms;android-34" "build-tools;34.0.0" >/dev/null
fi

if ! command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 || ! command -v xvfb-run >/dev/null 2>&1; then
    echo "=== installing mingw-w64 + xvfb (apt)"
    sudo apt-get update -qq
    sudo apt-get install -y -qq mingw-w64 xvfb
fi

echo "=== toolchain ready"
echo "    JAVA_HOME=$JDK_DIR"
echo "    ANDROID_SDK_ROOT=$SDK_DIR"
