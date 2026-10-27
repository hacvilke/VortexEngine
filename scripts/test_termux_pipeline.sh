#!/usr/bin/env bash
# ============================================================================
# End-to-End Integration Test:
#  1) Verifies VortexScript Engine lexer/parser/runtime & ObjLoader
#  2) Runs the exact Termux build_termux.sh pipeline in BOTH --fast & --from-source
#     modes to compile a standalone 3D OpenGL ES Game APK (NeonHorizon3D-Sample.apk)
# ============================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk-17}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/build-tools/34.0.0:$PATH"

STAGE="/tmp/vortex-termux-e2e/neon_horizon_3d"
rm -rf "/tmp/vortex-termux-e2e"
mkdir -p "$STAGE"/{res/values,assets/scripts,assets/meshes,prebuilt}

# Copy icons from Studio for sample game
for d in mipmap-mdpi mipmap-hdpi mipmap-xhdpi mipmap-xxhdpi; do
    mkdir -p "$STAGE/res/$d"
    cp "app/src/main/res/$d/ic_launcher.png" "$STAGE/res/$d/ic_launcher.png"
done

cat > "$STAGE/AndroidManifest.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.vortex.game.neonhorizon"
    android:versionCode="1"
    android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="34" />
    <uses-feature android:glEsVersion="0x00020000" android:required="true" />
    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.NoTitleBar.Fullscreen">
        <activity
            android:name="com.vortex.engine.runtime.StandaloneGameActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:screenOrientation="sensorLandscape"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > "$STAGE/res/values/strings.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Neon Horizon 3D</string>
</resources>
EOF

cat > "$STAGE/assets/project.vortex.json" << 'EOF'
{
  "id": "neon3d01",
  "title": "Neon Horizon 3D",
  "packageName": "com.vortex.game.neonhorizon",
  "versionName": "1.0.0",
  "versionCode": 1,
  "orientation": "landscape",
  "dimensionMode": "3D",
  "skyColorHex": "#080C1A",
  "ambientColorHex": "#1E3050",
  "fogEnabled": true,
  "fogNear": 18,
  "fogFar": 68,
  "gravityX": 0,
  "gravityY": -22,
  "gravityZ": 0,
  "cameraMode": "FOLLOW",
  "cameraTargetName": "Player",
  "cameraDistance": 9.5,
  "cameraHeight": 4.6,
  "cameraFov": 60,
  "showGrid": true,
  "showJoystick": true,
  "showActionButtons": true,
  "customObjMeshes": {
    "starfighter": "v 0.0 0.15 -0.85\nv -0.65 -0.10 0.65\nv 0.65 -0.10 0.65\nv 0.0 0.42 0.35\nv 0.0 -0.22 0.45\nf 1 2 4\nf 1 4 3\nf 4 2 3\nf 1 5 2\nf 1 3 5\nf 5 3 2\n"
  },
  "entities": [
    {"id":"flr1","name":"TrackFloor","tag":"solid","is3D":true,"primitive":"CUBE","x":0,"y":-0.5,"z":-35,"scaleX":18,"scaleY":1,"scaleZ":120,"colorHex":"#101B2D","secondaryColorHex":"#09101C","checkerTexture":true,"bodyType":"STATIC","collider":"BOX"},
    {"id":"plr1","name":"Player","tag":"player","is3D":true,"primitive":"CUSTOM_OBJ","customObjName":"starfighter","x":0,"y":0.8,"z":4,"scaleX":1.4,"scaleY":1.1,"scaleZ":1.6,"colorHex":"#00E5FF","emissive":true,"bodyType":"DYNAMIC","collider":"BOX"},
    {"id":"bar1","name":"Barrier_1","tag":"hazard","is3D":true,"primitive":"PYRAMID","x":-3.5,"y":1,"z":-8,"scaleX":2,"scaleY":2.2,"scaleZ":2,"colorHex":"#FF4081","emissive":true,"bodyType":"STATIC","collider":"BOX"},
    {"id":"cry1","name":"Crystal_1","tag":"coin","is3D":true,"primitive":"SPHERE","x":0,"y":1.1,"z":-5,"scaleX":1,"scaleY":1,"scaleZ":1,"colorHex":"#FFD740","emissive":true,"isTrigger":true,"spinY":140,"bodyType":"STATIC","collider":"BOX"},
    {"id":"wpg1","name":"WarpGate","tag":"goal","is3D":true,"primitive":"CYLINDER","x":0,"y":2.2,"z":-42,"scaleX":4.5,"scaleY":4.4,"scaleZ":1.2,"colorHex":"#00E676","emissive":true,"isTrigger":true,"spinY":65,"bodyType":"STATIC","collider":"BOX"}
  ]
}
EOF

cat > "$STAGE/assets/scripts/main.vxs" << 'EOF'
let speed = 13.0;
let finished = false;
fn onStart() { setBanner('COLLECT CRYSTALS & REACH WARP GATE!'); }
fn onUpdate(dt) {
    if (!exists('Player') || finished) { return; }
    let boost = 1.0;
    if (btnB()) { boost = 1.85; particles(getX('Player'), getY('Player'), getZ('Player') + 1.1, 3, '#00E5FF'); }
    setVel('Player', joyX() * 11.5, getVY('Player'), -speed * boost);
    setRot('Player', 0, 0, -joyX() * 24);
}
fn onButton(btn) { if (btn == 'A') { jump('Player', 11.5); } }
fn onCollide(a, b, tagA, tagB) {
    if (a == 'Player' && tagB == 'coin') { particles(getX(b), getY(b), getZ(b), 22, '#FFD740'); destroy(b); addScore(100); playSound('coin'); }
    if (a == 'Player' && tagB == 'goal') { finished = true; addScore(500); playSound('powerup'); setBanner('SECTOR CLEARED! SCORE: ' + getScore()); }
}
EOF

cp app/src/main/assets/toolchain/vortex-runtime.dex "$STAGE/prebuilt/vortex-runtime.dex"
unzip -q -o app/src/main/assets/toolchain/vortex-runtime-src.zip -d "$STAGE"
cp app/src/main/assets/toolchain/build_termux.sh "$STAGE/build_termux.sh"

echo "--- Testing Termux Build Pipeline Mode 1: Fast Prebuilt DEX ---"
VORTEX_NO_OPEN=1 bash "$STAGE/build_termux.sh" --fast --out-dir "$PROJECT_ROOT/dist"

echo "--- Testing Termux Build Pipeline Mode 2: Full Java Source Compile (--from-source) ---"
VORTEX_NO_OPEN=1 bash "$STAGE/build_termux.sh" --from-source --out-dir "$PROJECT_ROOT/dist"

mv "$PROJECT_ROOT/dist/neon_horizon_3d-release.apk" "$PROJECT_ROOT/dist/NeonHorizon3D-Sample.apk"
apksigner verify --verbose "$PROJECT_ROOT/dist/NeonHorizon3D-Sample.apk" | head -n 10
aapt2 dump badging "$PROJECT_ROOT/dist/NeonHorizon3D-Sample.apk" | head -n 5
ls -lh "$PROJECT_ROOT/dist/"
