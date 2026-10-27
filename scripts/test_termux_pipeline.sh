#!/usr/bin/env bash
# ============================================================================
# End-to-End Termux APK Pipeline Test (v2.0.0 — LuaJ + JBox2D + FastNoiseLite)
# Tests BOTH --fast (vortex-runtime.dex) AND --from-source (javac + d8 + JARs)
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export VORTEX_NO_OPEN=1

TEST_DIR="$ROOT_DIR/build_termux_test"
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR/export_dir"

echo "=== [1/4] Exporting 'Angry Crates Siege (Lua 5.2 + JBox2D)' Bundle via DesktopExporter ==="
cat > "$TEST_DIR/ExportTestBundle.java" <<'EOF'
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.desktop.DesktopExporter;
import java.io.File;

public class ExportTestBundle {
    public static void main(String[] args) throws Exception {
        GameProject p = ProjectTemplates.create("ANGRY_BOX2D_LUA", "Angry Crates Box2D", "com.vortex.game.angrycrates");
        DesktopExporter.exportAndroidTermuxZip(p, new File(args[0]));
        DesktopExporter.exportStandaloneWindowsExe(p, new File(args[1]));
    }
}
EOF

"$JAVA_HOME/bin/javac" -cp "$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v2.0.0.jar" \
    -d "$TEST_DIR" "$TEST_DIR/ExportTestBundle.java"
"$JAVA_HOME/bin/java" -cp "$TEST_DIR:$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v2.0.0.jar" \
    ExportTestBundle "$TEST_DIR/AngryCrates-Termux.zip" "$ROOT_DIR/dist/AngryCratesBox2D-Windows-Sample.exe"

unzip -qo "$TEST_DIR/AngryCrates-Termux.zip" -d "$TEST_DIR/export_dir"
unzip -qo "$ROOT_DIR/build_studio/assets/toolchain/vortex-runtime-src.zip" -d "$TEST_DIR/export_dir"

echo "=== [2/4] Testing Termux --fast Build Mode (prebuilt vortex-runtime.dex) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_fast" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --fast --out-dir "$TEST_DIR/out_fast"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_fast/export_dir-release.apk"
echo "[PASS] Termux --fast APK verified!"

echo "=== [3/4] Testing Termux --from-source Build Mode (javac + d8 + LuaJ + JBox2D JARs) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_src" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --from-source --out-dir "$TEST_DIR/out_src"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_src/export_dir-release.apk"
cp "$TEST_DIR/out_src/export_dir-release.apk" "$ROOT_DIR/dist/AngryCratesBox2D-Android-Sample.apk"
echo "[PASS] Termux --from-source APK verified!"

echo "=== [4/4] Testing Linux Self-Contained Binary Execution ==="
"$ROOT_DIR/dist/VortexEngine-Studio-Linux-x86_64" --headless-test | tail -n 4

ls -lh "$ROOT_DIR/dist/"
echo "[ALL PIPELINE TESTS PASSED!]"
