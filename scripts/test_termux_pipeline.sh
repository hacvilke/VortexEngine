#!/usr/bin/env bash
# ============================================================================
# End-to-End Multi-Platform Pipeline Test (v3.0.0 — Apex Edition)
# Tests:
#   1) Termux APK --fast (vortex-runtime.dex) & --from-source (javac + d8 + 4 OSS JARs)
#   2) Standalone Windows .exe & HTML5 Web (.html) Exporters
#   3) Self-Contained Linux Binary Headless Verification Suite
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

echo "=== [1/4] Exporting 'Cyber Hunters AI Maze 2D' (Theta* AI + Tween + Chiptune + Box2D) ==="
cat > "$TEST_DIR/ExportTestBundle.java" <<'EOF'
import com.vortex.engine.core.export.Html5WebExporter;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.desktop.DesktopExporter;
import java.io.File;

public class ExportTestBundle {
    public static void main(String[] args) throws Exception {
        GameProject p = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters AI Maze", "com.vortex.game.cyberhunters");
        DesktopExporter.exportAndroidTermuxZip(p, new File(args[0]));
        DesktopExporter.exportStandaloneWindowsExe(p, new File(args[1]));
        Html5WebExporter.exportToFile(p, new File(args[2]));
    }
}
EOF

"$JAVA_HOME/bin/javac" -cp "$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v3.0.0.jar" \
    -d "$TEST_DIR" "$TEST_DIR/ExportTestBundle.java"
"$JAVA_HOME/bin/java" -cp "$TEST_DIR:$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v3.0.0.jar" \
    ExportTestBundle \
    "$TEST_DIR/CyberHunters-Termux.zip" \
    "$ROOT_DIR/dist/CyberHuntersAI-Windows-Sample.exe" \
    "$ROOT_DIR/dist/CyberMazeHunters-Web.html"

unzip -qo "$TEST_DIR/CyberHunters-Termux.zip" -d "$TEST_DIR/export_dir"
unzip -qo "$ROOT_DIR/build_studio/assets/toolchain/vortex-runtime-src.zip" -d "$TEST_DIR/export_dir"

echo "=== [2/4] Testing Termux --fast Build Mode (prebuilt vortex-runtime.dex) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_fast" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --fast --out-dir "$TEST_DIR/out_fast"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_fast/export_dir-release.apk"
echo "[PASS] Termux --fast APK verified!"

echo "=== [3/4] Testing Termux --from-source Build Mode (javac + d8 + LuaJ + JBox2D + Tween + Pathfinding) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_src" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --from-source --out-dir "$TEST_DIR/out_src"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_src/export_dir-release.apk"
cp "$TEST_DIR/out_src/export_dir-release.apk" "$ROOT_DIR/dist/CyberHuntersAI-Android-Sample.apk"
echo "[PASS] Termux --from-source APK verified!"

echo "=== [4/4] Testing Linux Self-Contained Binary Execution ==="
"$ROOT_DIR/dist/VortexEngine-Studio-Linux-x86_64" --headless-test | tail -n 8

ls -lh "$ROOT_DIR/dist/"
echo "[ALL V3.0.0 PIPELINE TESTS PASSED!]"
