#!/usr/bin/env bash
# ============================================================================
# Vortex Engine Studio v2.0.0 — Cross-Platform Desktop Builder
# Outputs:
#   1) dist/VortexEngine-Studio-Windows-x64.exe  (Native Win32 PE + Embedded Engine)
#   2) dist/VortexEngine-Studio-Linux-x86_64     (Self-contained Linux executable)
#   3) dist/VortexEngine-Studio-Desktop-v2.0.0.jar (Portable Runnable Fat JAR)
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export PATH="$JAVA_HOME/bin:$PATH"
JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
JAR="$JAVA_HOME/bin/jar"

LUAJ_JAR="$ROOT_DIR/app/libs/luaj-jse-3.0.1.jar"
JBOX2D_JAR="$ROOT_DIR/app/libs/jbox2d-library-2.2.1.1.jar"
JSON_JAR="$ROOT_DIR/desktop/libs/json-20240303.jar"

BUILD_DESK="$ROOT_DIR/build_desktop"
DIST_DIR="$ROOT_DIR/dist"
rm -rf "$BUILD_DESK"
mkdir -p "$BUILD_DESK/classes" "$BUILD_DESK/fatjar" "$BUILD_DESK/win" "$DIST_DIR"

echo "=== [1/5] Generating Multi-Resolution Windows .ICO Icon & Compiling Win32 PE Launcher ==="
cat > "$BUILD_DESK/win/GenWinIco.java" <<'EOF'
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

public class GenWinIco {
    public static void main(String[] args) throws Exception {
        int[] sizes = {16, 32, 48, 64, 256};
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(9, 13, 22));
            g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.22f, s * 0.22f));
            g.setColor(new Color(0, 229, 255));
            g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.06f)));
            float pad = s * 0.09f;
            g.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2, s - pad * 2, s * 0.16f, s * 0.16f));

            Path2D hex = new Path2D.Float();
            float cx = s * 0.5f, cy = s * 0.5f, r = s * 0.27f;
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(k * 60 - 90);
                float px = cx + (float)(Math.cos(a) * r), py = cy + (float)(Math.sin(a) * r);
                if (k == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
            }
            hex.closePath();
            g.setColor(new Color(0, 229, 255, 225));
            g.fill(hex);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            pngs[i] = baos.toByteArray();
        }

        // Write standard Windows ICO container with PNG payloads
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(args[0]))) {
            writeLE16(dos, 0); // Reserved
            writeLE16(dos, 1); // Type 1 = ICO
            writeLE16(dos, sizes.length);
            int offset = 6 + sizes.length * 16;
            for (int i = 0; i < sizes.length; i++) {
                int s = sizes[i];
                dos.writeByte(s >= 256 ? 0 : s);
                dos.writeByte(s >= 256 ? 0 : s);
                dos.writeByte(0); // Palette
                dos.writeByte(0); // Reserved
                writeLE16(dos, 1); // Planes
                writeLE16(dos, 32); // BPP
                writeLE32(dos, pngs[i].length);
                writeLE32(dos, offset);
                offset += pngs[i].length;
            }
            for (byte[] p : pngs) dos.write(p);
        }
    }
    static void writeLE16(DataOutputStream d, int v) throws IOException {
        d.writeByte(v & 0xFF); d.writeByte((v >> 8) & 0xFF);
    }
    static void writeLE32(DataOutputStream d, int v) throws IOException {
        d.writeByte(v & 0xFF); d.writeByte((v >> 8) & 0xFF); d.writeByte((v >> 16) & 0xFF); d.writeByte((v >> 24) & 0xFF);
    }
}
EOF
"$JAVAC" -d "$BUILD_DESK/win" "$BUILD_DESK/win/GenWinIco.java"
"$JAVA" -cp "$BUILD_DESK/win" GenWinIco "$BUILD_DESK/win/vortex.ico"
cp desktop/win/vortex.rc "$BUILD_DESK/win/vortex.rc"

x86_64-w64-mingw32-windres "$BUILD_DESK/win/vortex.rc" -O coff -o "$BUILD_DESK/win/vortex.res"
x86_64-w64-mingw32-gcc -O2 -s -mwindows -municode \
    desktop/win/launcher.c "$BUILD_DESK/win/vortex.res" \
    -lshell32 -o "$BUILD_DESK/win/win-launcher-stub.exe"
ls -lh "$BUILD_DESK/win/win-launcher-stub.exe"

echo "=== [2/5] Compiling Pure-Java Core Engine + Desktop Studio IDE ==="
# Note: Compile ONLY pure-Java core packages (math, model, noise, physics, script) + desktop/*
# Zero Android SDK classes required!
find app/src/main/java/com/vortex/engine/core/math \
     app/src/main/java/com/vortex/engine/core/model \
     app/src/main/java/com/vortex/engine/core/noise \
     app/src/main/java/com/vortex/engine/core/physics \
     app/src/main/java/com/vortex/engine/core/script \
     desktop/src \
     -name "*.java" > "$BUILD_DESK/sources.txt"

"$JAVAC" --release 11 -encoding UTF-8 \
    -cp "$LUAJ_JAR:$JBOX2D_JAR:$JSON_JAR" \
    -d "$BUILD_DESK/classes" \
    @"$BUILD_DESK/sources.txt"

echo "=== [3/5] Assembling All-in-One Desktop Fat JAR (Core + LuaJ + JBox2D + org.json + Toolchain) ==="
(cd "$BUILD_DESK/fatjar" && unzip -qo "$LUAJ_JAR" && unzip -qo "$JBOX2D_JAR" && unzip -qo "$JSON_JAR")
rm -rf "$BUILD_DESK/fatjar/META-INF"
cp -R "$BUILD_DESK/classes"/* "$BUILD_DESK/fatjar/"

mkdir -p "$BUILD_DESK/fatjar/toolchain"
cp "$BUILD_DESK/win/win-launcher-stub.exe" "$BUILD_DESK/fatjar/toolchain/win-launcher-stub.exe"
cp scripts/termux/build_termux.sh "$BUILD_DESK/fatjar/toolchain/build_termux.sh"
cp scripts/termux/setup_termux.sh "$BUILD_DESK/fatjar/toolchain/setup_termux.sh"
if [ -f "build_studio/assets/toolchain/vortex-runtime.dex" ]; then
    cp "build_studio/assets/toolchain/vortex-runtime.dex" "$BUILD_DESK/fatjar/toolchain/vortex-runtime.dex"
fi

cat > "$BUILD_DESK/manifest.mf" <<'EOF'
Manifest-Version: 1.0
Main-Class: com.vortex.engine.desktop.VortexDesktopStudio
EOF

FAT_JAR="$DIST_DIR/VortexEngine-Studio-Desktop-v2.0.0.jar"
"$JAR" cfm "$FAT_JAR" "$BUILD_DESK/manifest.mf" -C "$BUILD_DESK/fatjar" .

echo "=== [4/5] Building Self-Contained Windows x64 .EXE & Linux x86_64 Binary ==="
WIN_EXE="$DIST_DIR/VortexEngine-Studio-Windows-x64.exe"
cat "$BUILD_DESK/win/win-launcher-stub.exe" "$FAT_JAR" > "$WIN_EXE"

LINUX_BIN="$DIST_DIR/VortexEngine-Studio-Linux-x86_64"
printf '#!/usr/bin/env sh\nexec java -Xmx1024m -jar "$0" "$@"\n' > "$LINUX_BIN"
cat "$FAT_JAR" >> "$LINUX_BIN"
chmod +x "$LINUX_BIN"

echo "=== [5/5] Running Automated Headless Engine Verification Suite ==="
"$JAVA" -Djava.awt.headless=true -jar "$FAT_JAR" --headless-test

file "$WIN_EXE"
ls -lh "$FAT_JAR" "$WIN_EXE" "$LINUX_BIN"
echo "[SUCCESS] Desktop builds ready in $DIST_DIR!"
