#!/usr/bin/env bash
# ============================================================================
# Vortex Engine Studio v4.0.0 (Hyperion Edition) — Full Android APK Build Script
# Compiles Core + Rhino ES6 + LuaJ + JBox2D + FastNoiseLite + Tween + Pathfinding + LRM + Forge AI
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/34.0.0"
PLATFORM_JAR="$ANDROID_SDK_ROOT/platforms/android-34/android.jar"

JAVAC="$JAVA_HOME/bin/javac"
JAVA="$JAVA_HOME/bin/java"
KEYTOOL="$JAVA_HOME/bin/keytool"
AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

LUAJ_JAR="$ROOT_DIR/app/libs/luaj-jse-3.0.1.jar"
JBOX2D_JAR="$ROOT_DIR/app/libs/jbox2d-library-2.2.1.1.jar"
TWEEN_JAR="$ROOT_DIR/app/libs/universal-tween-engine-6.3.3.jar"
PATHFINDING_JAR="$ROOT_DIR/app/libs/pathfinding-0.2.6.jar"
RHINO_JAR="$ROOT_DIR/app/libs/rhino-1.7.15.1.jar"
BSH_JAR="$ROOT_DIR/app/libs/bsh-2.0b6.jar"
LIB_CP="$PLATFORM_JAR:$LUAJ_JAR:$JBOX2D_JAR:$TWEEN_JAR:$PATHFINDING_JAR:$RHINO_JAR:$BSH_JAR"

BUILD_DIR="$ROOT_DIR/build_studio"
DIST_DIR="$ROOT_DIR/dist"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/runtime_classes" \
         "$BUILD_DIR/runtime_dex" \
         "$BUILD_DIR/studio_gen" \
         "$BUILD_DIR/studio_classes" \
         "$BUILD_DIR/studio_dex" \
         "$BUILD_DIR/assets/toolchain" \
         "$BUILD_DIR/res/values" \
         "$DIST_DIR"

echo "=== [1/7] Compiling Standalone Game Runtime (GLES + LuaJ + JBox2D + FastNoiseLite) ==="
find app/src/main/java/com/vortex/engine/core \
     app/src/main/java/com/vortex/engine/runtime \
     -name "*.java" > "$BUILD_DIR/runtime_sources.txt"

"$JAVAC" --release 8 -encoding UTF-8 \
    -cp "$LIB_CP" \
    -d "$BUILD_DIR/runtime_classes" \
    @"$BUILD_DIR/runtime_sources.txt"

(cd "$BUILD_DIR/runtime_classes" && zip -qr "$BUILD_DIR/runtime.jar" .)
"$D8" --min-api 24 --lib "$PLATFORM_JAR" \
    --output "$BUILD_DIR/runtime_dex" \
    "$BUILD_DIR/runtime.jar" "$LUAJ_JAR" "$JBOX2D_JAR" "$TWEEN_JAR" "$PATHFINDING_JAR" "$RHINO_JAR" "$BSH_JAR"
cp "$BUILD_DIR/runtime_dex/classes.dex" "$BUILD_DIR/assets/toolchain/vortex-runtime.dex"
[ -f "$BUILD_DIR/runtime_dex/classes2.dex" ] && cp "$BUILD_DIR/runtime_dex/classes2.dex" "$BUILD_DIR/assets/toolchain/vortex-runtime-2.dex"
[ -f "$BUILD_DIR/runtime_dex/classes3.dex" ] && cp "$BUILD_DIR/runtime_dex/classes3.dex" "$BUILD_DIR/assets/toolchain/vortex-runtime-3.dex"

echo "=== [2/7] Packaging Termux Toolchain & Open-Source Runtime Bundle ==="
cp scripts/termux/build_termux.sh "$BUILD_DIR/assets/toolchain/build_termux.sh"
cp scripts/termux/setup_termux.sh "$BUILD_DIR/assets/toolchain/setup_termux.sh"
mkdir -p app/src/main/assets/toolchain
cp scripts/termux/build_termux.sh app/src/main/assets/toolchain/build_termux.sh
cp scripts/termux/setup_termux.sh app/src/main/assets/toolchain/setup_termux.sh

mkdir -p "$BUILD_DIR/src_bundle/src/main/java/com/vortex/engine" "$BUILD_DIR/src_bundle/libs"
cp -R app/src/main/java/com/vortex/engine/core "$BUILD_DIR/src_bundle/src/main/java/com/vortex/engine/"
cp -R app/src/main/java/com/vortex/engine/runtime "$BUILD_DIR/src_bundle/src/main/java/com/vortex/engine/"
cp "$LUAJ_JAR" "$JBOX2D_JAR" "$TWEEN_JAR" "$PATHFINDING_JAR" "$RHINO_JAR" "$BSH_JAR" "$BUILD_DIR/src_bundle/libs/"
(cd "$BUILD_DIR/src_bundle" && zip -qr "$BUILD_DIR/assets/toolchain/vortex-runtime-src.zip" src libs)

echo "=== [3/7] Generating Procedural Studio Launcher Icons ==="
mkdir -p "$BUILD_DIR/icon_tool"
cat > "$BUILD_DIR/icon_tool/GenStudioIcons.java" <<'EOF'
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenStudioIcons {
    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            int s = sizes[i];
            BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(11, 11, 14));
            g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.22f, s * 0.22f));
            g.setColor(new Color(255, 255, 255));
            g.setStroke(new BasicStroke(Math.max(2f, s * 0.055f)));
            float pad = s * 0.1f;
            g.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2, s - pad * 2, s * 0.16f, s * 0.16f));

            Path2D hex = new Path2D.Float();
            float cx = s * 0.5f, cy = s * 0.48f, r = s * 0.26f;
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(k * 60 - 90);
                float px = cx + (float)(Math.cos(a) * r), py = cy + (float)(Math.sin(a) * r);
                if (k == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
            }
            hex.closePath();
            g.setColor(new Color(255, 255, 255, 240));
            g.fill(hex);
            g.setColor(new Color(10, 10, 12));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, Math.round(s * 0.22f))));
            FontMetrics fm = g.getFontMetrics();
            String lbl = "V4";
            g.drawString(lbl, (s - fm.stringWidth(lbl)) / 2, Math.round(s * 0.56f));
            g.dispose();

            File outDir = new File(res, dirs[i]);
            outDir.mkdirs();
            ImageIO.write(img, "PNG", new File(outDir, "ic_launcher.png"));
        }
    }
}
EOF
"$JAVAC" -d "$BUILD_DIR/icon_tool" "$BUILD_DIR/icon_tool/GenStudioIcons.java"
"$JAVA" -cp "$BUILD_DIR/icon_tool" GenStudioIcons "$BUILD_DIR/res"
cp -R app/src/main/res/values/* "$BUILD_DIR/res/values/"

echo "=== [4/7] Compiling & Linking Studio APK Resources with aapt2 ==="
"$AAPT2" compile --dir "$BUILD_DIR/res" -o "$BUILD_DIR/resources.zip"

"$AAPT2" link \
    -I "$PLATFORM_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    -A "$BUILD_DIR/assets" \
    --java "$BUILD_DIR/studio_gen" \
    --min-sdk-version 24 \
    --target-sdk-version 34 \
    --auto-add-overlay \
    -o "$BUILD_DIR/studio-unaligned.apk" \
    "$BUILD_DIR/resources.zip"

echo "=== [5/7] Compiling Full Studio IDE + Engine Java Sources ==="
find app/src/main/java "$BUILD_DIR/studio_gen" -name "*.java" > "$BUILD_DIR/studio_sources.txt"
"$JAVAC" --release 8 -encoding UTF-8 \
    -cp "$LIB_CP" \
    -d "$BUILD_DIR/studio_classes" \
    @"$BUILD_DIR/studio_sources.txt"

(cd "$BUILD_DIR/studio_classes" && zip -qr "$BUILD_DIR/studio.jar" .)
"$D8" --min-api 24 --lib "$PLATFORM_JAR" \
    --output "$BUILD_DIR/studio_dex" \
    "$BUILD_DIR/studio.jar" "$LUAJ_JAR" "$JBOX2D_JAR" "$TWEEN_JAR" "$PATHFINDING_JAR" "$RHINO_JAR" "$BSH_JAR"

echo "=== [6/7] Injecting DEX & Aligning APK ==="
(cd "$BUILD_DIR/studio_dex" && zip -qj "$BUILD_DIR/studio-unaligned.apk" classes*.dex)
"$ZIPALIGN" -f -p 4 "$BUILD_DIR/studio-unaligned.apk" "$BUILD_DIR/studio-aligned.apk"

echo "=== [7/7] Signing Release APK (v1 + v2 + v3 schemes) ==="
KEYSTORE="$BUILD_DIR/studio-release.keystore"
"$KEYTOOL" -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -alias vortexstudio \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass vortex123 \
    -keypass vortex123 \
    -dname "CN=Vortex Engine Studio, OU=GameEngine, O=VortexEngine, L=Global, S=Mobile, C=US" >/dev/null 2>&1

OUT_APK="$DIST_DIR/VortexEngine-Studio-v4.7.0.apk"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:vortex123 \
    --key-pass pass:vortex123 \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT_APK" \
    "$BUILD_DIR/studio-aligned.apk"

"$APKSIGNER" verify --verbose "$OUT_APK" | head -n 10
ls -lh "$OUT_APK"
echo "[SUCCESS] Vortex Engine Studio v4.7.0 APK ready at: $OUT_APK"
