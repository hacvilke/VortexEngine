# Vortex Engine (`VortexEngine`)

**Vortex Engine** is a custom-built **2D & 3D OpenGL ES 2.0/3.0 Android Game Engine** and **On-Device Mobile Game Studio IDE** that lets you design, script, play-test, and **compile standalone installable `.apk` games directly on your Android phone using Termux**.

---

## Key Features

### 1. Unified 2D & 3D OpenGL ES Engine (`com.vortex.engine.core`)
- **3D Rendering Pipeline**: Custom GLSL Blinn-Phong directional + hemisphere ambient shaders, specular highlights, emissive neon materials, procedural checkerboard surface textures, and distance fog.
- **3D Mesh Primitives & Wavefront `.OBJ` Support**: Built-in `CUBE`, `SPHERE`, `PYRAMID`, `CYLINDER`, and `PLANE` generators **plus** a real-time Wavefront `.obj` 3D mesh parser (`ObjLoader`) with an in-app `.obj` mesh editor.
- **2D Hardware Pipeline**: Aspect-correct orthographic camera, smooth follow & screen shake, geometric primitives, and procedural arcade sprites (`SPRITE_HERO`, `SPRITE_SHIP`, `SPRITE_SLIME`, `SPRITE_COIN`).
- **2D & 3D Physics Solver (`PhysicsWorld`)**: Dynamic, Kinematic, and Static bodies, 2D/3D AABB collision resolution, restitution (bounce), surface friction, grounded detection (`isGrounded`), continuous spin, and trigger callbacks.
- **Zero-Allocation GPU Particle System**: Up to 384 simultaneous 2D/3D burst & thruster particles.
- **Procedural 16-bit PCM Synth Audio (`SynthAudioEngine`)**: Zero-asset synthesized sound effects (`jump`, `coin`, `laser`, `explosion`, `hit`, `powerup`) via Android `AudioTrack`.

### 2. Custom Game Scripting Language (`VortexScript` — `.vxs`)
- Built-in recursive-descent Lexer, AST Parser, Syntax Validator, and Runtime VM (`VortexScriptEngine`) with instruction-budget loop protection.
- Supports variables (`let`), compound assignments (`+=`, `-=`, `*=`, `/=`), `if / else`, `while`, `for`, user-defined functions (`fn`), recursion, and ~40 built-in engine functions.

### 3. Full On-Device Game Studio APK (`com.vortex.engine.studio`)
- **6 Starter Templates**:
  - `Neon Horizon 3D` (3D starfighter runner using a custom `.obj` mesh, pyramids, crystals & warp gate)
  - `Sky Islands 3D` (3D floating island parkour)
  - `Starbound Cavern 2D` (2D platformer with patrolling slimes & coins)
  - `Nova Blaster 2D` (2D top-down space shooter with plasma lasers)
  - `Blank 3D Sandbox` & `Blank 2D Sandbox`
- **Interactive 2D/3D Scene Viewport**: Touch raycast entity picking, drag-to-move with grid snap, X/Y/Z axis gizmo, two-finger pan & pinch zoom.
- **Entity & World Inspector**: Edit transforms, tags, physics, colors, sky, fog, gravity, and camera settings.
- **Script IDE**: Monospace `.vxs` editor with quick snippets, one-tap syntax validation, `.obj` mesh editor, and built-in API docs.
- **Instant 60 FPS Play Mode**: Test immediately inside the app with multi-touch virtual analog joystick, `A`/`B` arcade buttons, live FPS/Score/HP HUD, and console output.

### 4. Dual-Mode Termux On-Device APK Compiler (`TermuxBridge`)
- **Direct One-Tap Build (`com.termux.RUN_COMMAND`)**: Dispatches builds directly to Termux (`RunCommandService`) in either background mode (streaming `stdout`, `stderr`, and `exitCode` back into Vortex Studio via `PendingIntent`) or an interactive Termux terminal session.
- **Universal Script Fallback (`MediaStore` + `/sdcard/Download/`)**: Exports the complete Android project (plus a zero-permission `MediaStore` ZIP bundle for Android 10–16) and copies the exact `bash .../build_termux.sh` command to your clipboard.
- **Two Compilation Modes in Termux**:
  - **Fast Mode (~3 seconds)**: Compiles custom `AndroidManifest.xml`, custom generated PNG launcher icons, and your 2D/3D scenes/scripts with `aapt2`, injects the prebuilt `vortex-runtime.dex`, aligns with `zipalign`, and signs with `apksigner` (v1 + v2 + v3 schemes). Works completely offline using Termux's `$PREFIX/share/aapt/android.jar` or the phone's `/system/framework/framework-res.apk`!
  - **Full Java Source Mode (`--from-source`)**: Compiles the exported Java engine & runtime sources from scratch using `javac` + `d8`/`dx` + `aapt2` + `zipalign` + `apksigner`.

---

## How to Use on Android with Termux

1. **Install APKs**:
   - Install **`VortexEngine-Studio-v1.0.0.apk`** on your Android phone.
   - Install **Termux** (from **F-Droid** or Termux's GitHub Releases — avoid the outdated Play Store build).
2. **One-Time Termux Toolchain Setup**:
   - Open **Vortex Engine**, tap **`🔧 TERMUX SETUP`** → **`Copy Setup Cmd & Open Termux`**, paste into Termux, and press Enter.
   - Or run directly in Termux:
     ```bash
     mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties && \
     termux-reload-settings && termux-setup-storage && \
     pkg update -y && pkg install -y openjdk-17 aapt aapt2 apksigner zip unzip
     ```
3. **Create & Export Any Game to a Standalone `.apk`**:
   - Create or edit a 2D or 3D game in **Vortex Engine**.
   - Open the **`📦 BUILD APK`** tab, customize your **Game Name**, **Package ID** (e.g. `com.mygames.cyberdrift`), **Orientation**, and **Launcher Icon**.
   - Tap **`⚡ ONE-TAP BUILD APK IN TERMUX`** (or **`📋 EXPORT + COPY CMD`** to paste into Termux).
   - Once built, tap **`📲 INSTALL BUILT GAME APK`** (or open `/sdcard/Download/VortexEngine/Builds/<game>-release.apk`).

---

## VortexScript (`.vxs`) Quick API Reference

```javascript
// Lifecycle Entrypoints
fn onStart() { setBanner("READY!"); }
fn onUpdate(dt) {
    setVel("Player", joyX() * 10.0, getVY("Player"), -12.0);
}
fn onButton(btn) {
    if (btn == "A") { jump("Player", 12.0); }
}
fn onCollide(a, b, tagA, tagB) {
    if (a == "Player" && tagB == "coin") {
        particles(getX(b), getY(b), getZ(b), 20, "#FFD740");
        destroy(b);
        addScore(100);
        playSound("coin");
    }
}
```

| Category | Built-in Functions |
|---|---|
| **Input** | `joyX()`, `joyY()`, `btnA()`, `btnB()`, `touchDown()`, `touchX()`, `touchY()` |
| **Queries** | `exists(name)`, `getX(n)`, `getY(n)`, `getZ(n)`, `getVX(n)`, `getVY(n)`, `getVZ(n)`, `getTag(n)`, `isGrounded(n)`, `dist(a, b)`, `countByTag(tag)` |
| **Transform / Physics** | `setPos(n, x, y, [z])`, `move(n, dx, dy, [dz])`, `setVel(n, vx, vy, [vz])`, `addVel(...)`, `jump(n, impulse)`, `setRot(n, rx, ry, rz)`, `rotate(...)`, `setScale(...)`, `setSpin(...)`, `setColor(n, hex)`, `destroy(n)` |
| **Spawning** | `spawn3D(name, tag, prim, x, y, z, sx, sy, sz, hex, bodyType)`, `spawn2D(name, tag, prim, x, y, w, h, hex, bodyType)` |
| **VFX / Audio / HUD** | `particles(x, y, z, count, hex)`, `shake(intensity, duration)`, `playSound(sfx)`, `addScore(n)`, `getScore()`, `addHealth(n)`, `getHealth()`, `setBanner(msg)`, `log(msg)` |
| **Math** | `sin(x)`, `cos(x)`, `atan2(y, x)`, `sqrt(x)`, `abs(x)`, `min(a, b)`, `max(a, b)`, `clamp(v, lo, hi)`, `floor(x)`, `rand(lo, hi)`, `time()` |

---

## Building from Source (Linux / macOS)

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk
./scripts/build_studio_apk.sh
./scripts/test_termux_pipeline.sh
```

Outputs:
- `dist/VortexEngine-Studio-v1.0.0.apk` — Main 2D/3D Game Studio IDE APK
- `dist/NeonHorizon3D-Sample.apk` — Standalone 3D game APK compiled via the Termux pipeline
