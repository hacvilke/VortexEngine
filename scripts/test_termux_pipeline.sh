#!/usr/bin/env bash
# ============================================================================
# End-to-End Multi-Platform Pipeline Test (v4.0.0 — Hyperion Edition)
# Tests:
#   1) Termux APK --fast & --from-source for ES6+ JavaScript Rhino Game
#   2) Standalone Windows .exe & Native ES6 HTML5 Web (.html) Exporters
#   3) Browser JS Syntax Validation (node --check) on Exported Web Bundles
#   4) Self-Contained Linux Binary + Live hacvilke/lrm CLI Headless Suite
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export PATH="$JAVA_HOME/bin:/tmp/lrm_bin:$HOME/.local/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export VORTEX_NO_OPEN=1

TEST_DIR="$ROOT_DIR/build_termux_test"
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR/export_dir"

echo "=== [1/5] Exporting Flagship ES6+ JavaScript 2D & 3D Games ==="
cat > "$TEST_DIR/ExportTestBundle.java" <<'EOF'
import com.vortex.engine.core.export.Html5WebExporter;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.desktop.DesktopExporter;
import java.io.File;

public class ExportTestBundle {
    public static void main(String[] args) throws Exception {
        GameProject survivors = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.game.survivors");
        DesktopExporter.exportAndroidTermuxZip(survivors, new File(args[0]));
        DesktopExporter.exportStandaloneWindowsExe(survivors, new File(args[1]));
        Html5WebExporter.exportToFile(survivors, new File(args[2]));

        GameProject mecha3d = ProjectTemplates.create("STARHULL_MECHA_JS_3D", "Starhull Mecha Raiders 3D", "com.vortex.game.starhull");
        Html5WebExporter.exportToFile(mecha3d, new File(args[3]));

        GameProject javaGame = ProjectTemplates.create("IRON_SENTINEL_JAVA_2D", "Iron Sentinel Java", "com.vortex.game.ironsentinel");
        Html5WebExporter.exportToFile(javaGame, new File(args[4]));
    }
}
EOF

"$JAVA_HOME/bin/javac" -encoding UTF-8 -cp "$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v4.3.0.jar" \
    -d "$TEST_DIR" "$TEST_DIR/ExportTestBundle.java"
"$JAVA_HOME/bin/java" -cp "$TEST_DIR:$ROOT_DIR/dist/VortexEngine-Studio-Desktop-v4.3.0.jar" \
    ExportTestBundle \
    "$TEST_DIR/NeonSurvivors-Termux.zip" \
    "$TEST_DIR/NeonSurvivorsSwarm-Windows-Sample.exe" \
    "$TEST_DIR/NeonSurvivorsSwarm-Web.html" \
    "$TEST_DIR/StarhullMecha3D-Web.html" \
    "$TEST_DIR/IronSentinelJava-Web.html"

unzip -qo "$TEST_DIR/NeonSurvivors-Termux.zip" -d "$TEST_DIR/export_dir"
unzip -qo "$ROOT_DIR/build_studio/assets/toolchain/vortex-runtime-src.zip" -d "$TEST_DIR/export_dir"

echo "=== [2/5] Testing Termux --fast Build Mode (prebuilt vortex-runtime.dex + Rhino ES6) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_fast" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --fast --out-dir "$TEST_DIR/out_fast"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_fast/export_dir-release.apk"
echo "[PASS] Termux --fast ES6 JS APK verified!"

echo "=== [3/5] Testing Termux --from-source Build Mode (javac + d8 + Rhino + LuaJ + JBox2D + Tween + Pathfinding) ==="
VORTEX_WORK_DIR="$TEST_DIR/work_src" \
    bash "$TEST_DIR/export_dir/build_termux.sh" --from-source --out-dir "$TEST_DIR/out_src"
"$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner" verify "$TEST_DIR/out_src/export_dir-release.apk"
echo "[PASS] Termux --from-source ES6 JS APK verified!"

echo "=== [4/5] Validating Native Browser ES6 JavaScript in Exported HTML5 Bundles ==="
if command -v node >/dev/null 2>&1; then
    for html_file in "$TEST_DIR/NeonSurvivorsSwarm-Web.html" "$TEST_DIR/StarhullMecha3D-Web.html" "$TEST_DIR/IronSentinelJava-Web.html"; do
        python3 -c '
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r"<script>(.*)</script>", s, re.S)
assert m, "No <script> tag found"
open(sys.argv[2], "w", encoding="utf-8").write(m.group(1))
' "$html_file" "$TEST_DIR/extracted_check.js"
        node --check "$TEST_DIR/extracted_check.js"
        echo "  [PASS] node --check valid ES6 JS in $(basename "$html_file")"
    done
fi

echo "=== [5/5] Testing Linux Self-Contained Binary + Live LRM Headless Suite ==="
"$ROOT_DIR/dist/VortexEngine-Studio-Linux-x86_64" --headless-test | tail -n 14

rm -rf "$TEST_DIR"
ls -lh "$ROOT_DIR/dist/"
echo "[ALL V4.3.0 PIPELINE TESTS PASSED!]"
