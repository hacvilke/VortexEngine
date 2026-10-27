# Vortex Engine v2.0.0 — 2D & 3D Game Engine + Cross-Platform Studio

**Create, script, paint, simulate, and export real 2D & 3D games — on Android (with Termux `.apk` compilation, zero laptop needed!), Windows PC (`.exe`), and Linux!**

---

## What's New in v2.0.0

### 1. Open-Source Engine Powerhouses Integrated
- **Lua 5.2 Scripting VM (`LuaJ 3.0.1`, MIT)** alongside **VortexScript (`.vxs`)** — write full Lua 5.2 scripts (`math`, tables, closures, `vortex.*` bindings) or VortexScript in the same project!
- **Box2D 2D Rigid-Body Physics (`JBox2D 2.2.1.1`, BSD-3)** — realistic tumbling crates, rotational torque, restitution/bounce, friction, impulses (`vortex.applyImpulse`), and sensor triggers!
- **3D Procedural Worlds (`FastNoiseLite`, MIT)** — generate sunlit 3D mountain & valley heightfield terrains (`TERRAIN` primitive + `vortex.noise2D` / `vortex.terrainHeight` APIs) with 1-tap seed randomization!
- **Built-in 16×16 Pixel Art Sprite Painter** — paint custom sprites (`CUSTOM_SPRITE`) with Mirror-X symmetry, 14-color neon/retro palette, eraser, and 1-tap scene spawning!
- **Built-in Procedural SFX Lab** — 22.05kHz real-time PCM wave synthesizer (`jump`, `coin`, `laser`, `explosion`, `hit`, `powerup`) on both Android (`AudioTrack`) and Windows/Linux (`javax.sound.sampled`)!

### 2. Multi-Platform Desktop Builds (Windows `.exe` + Linux + Android `.apk`)
- **Android Studio IDE (`VortexEngine-Studio-v2.0.0.apk`)**: Full mobile touchscreen 2D/3D OpenGL ES IDE + on-device Termux `.apk` compiler for creators without a PC.
- **Windows x64 Studio (`VortexEngine-Studio-Windows-x64.exe`)**: Native Win32 x64 executable with embedded engine & **1-click Standalone Windows `.exe` Game Exporter** (export your own games as `.exe` files without needing a C compiler!).
- **Linux x86_64 Studio (`VortexEngine-Studio-Linux-x86_64`)** & **Portable Desktop JAR (`VortexEngine-Studio-Desktop-v2.0.0.jar`)**.
- **Universal `.vortex.json` Project Format**: Start a game on Windows/Linux, transfer to Android & build an `.apk`, or vice versa!

### 3. Mobile Portrait UI & Termux Permission Fixes
- **Redesigned Responsive Portrait Layout**: Zero text-wrapping or squished buttons on ~360dp portrait phone screens (`MainActivity` 2-row cards & stacked header; `EditorActivity` 2-row header & dedicated full-width syntax status banner).
- **Clear Termux Build Modes & Samsung One UI / Android 14–16 Permission Helper**:
  - **Method 1 (Works Immediately — No Special Permission Needed!):** Tap **`🚀 EXPORT PROJECT + COPY BUILD CMD & OPEN TERMUX`** → paste in Termux & press Enter!
  - **Method 2 (Optional 1-Tap Background Auto-Build):** If Android shows `RUN_COMMAND` not granted (common on Samsung One UI / Android 14+ when Studio was installed before Termux), tap **`🔓 APP PERMISSIONS`** → **Permissions** → **Additional permissions** (or top-right `⋮` → **All permissions**) → enable **`Run commands in Termux environment`**.
- **Smarter `setup_termux.sh`**: Automatically skips already-installed tools and installs `openjdk-21` cleanly without duplicate JDK downloads.

---

## Downloads (GitHub Releases — `v2.0.0`)

| Platform | File | Description |
|---|---|---|
| **Android (Phone/Tablet)** | `VortexEngine-Studio-v2.0.0.apk` | Full 2D/3D Mobile Game Engine IDE + Termux APK Builder |
| **Windows 10/11 (64-bit)** | `VortexEngine-Studio-Windows-x64.exe` | Native Windows Studio IDE + `.exe` / `.jar` / Android Exporter (requires 64-bit Java 11+ or `./jre`) |
| **Linux (x86_64)** | `VortexEngine-Studio-Linux-x86_64` | Self-contained Linux Desktop Studio IDE (`chmod +x` & run) |
| **Cross-Platform JVM** | `VortexEngine-Studio-Desktop-v2.0.0.jar` | Portable Desktop Studio (`java -jar VortexEngine-Studio-Desktop-v2.0.0.jar`) |
| **Sample Game (Android)** | `AngryCratesBox2D-Android-Sample.apk` | Standalone Lua 5.2 + JBox2D Physics game compiled via Termux pipeline |
| **Sample Game (Windows)** | `AngryCratesBox2D-Windows-Sample.exe` | Standalone Lua 5.2 + JBox2D Physics game exported as a Windows `.exe` |

---

## Lua 5.2 (`vortex.*`) Quick Reference

```lua
function onStart()
    vortex.setBanner("SMASH THE JBOX2D TOWER!")
end

function onUpdate(dt)
    -- Touch joystick on Android OR WASD/Arrow keys on Windows/Linux!
    vortex.setVel("Player", vortex.joyX() * 9.5, vortex.getVY("Player"))
end

function onButton(btn)
    -- Button A / B on Android OR Space/J (A) & Shift/K (B) on PC!
    if btn == "A" then
        vortex.jump("Player", 13.0)
        vortex.playSound("jump")
    end
end

function onCollide(a, b, tagA, tagB)
    if a == "Player" and tagB == "coin" then
        vortex.particles(vortex.getX(b), vortex.getY(b), vortex.getZ(b), 24, "#FFD740")
        vortex.destroy(b)
        vortex.addScore(100)
        vortex.playSound("coin")
    end
end
```

---

## Building From Source

```bash
# Build Android Studio APK (requires JDK 17 + Android SDK 34)
./scripts/build_studio_apk.sh

# Build Windows .exe, Linux Binary & Desktop .jar (requires JDK 17 + mingw-w64)
./scripts/build_desktop.sh

# Run full Termux (--fast & --from-source) + Headless LuaJ/JBox2D/FastNoiseLite tests
./scripts/test_termux_pipeline.sh
```

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for open-source attributions.
