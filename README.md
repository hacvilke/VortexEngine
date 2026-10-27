# Vortex Engine v3.0.0 ("Apex Edition") — 2D & 3D Game Engine + Cross-Platform Studio

**Create, wire visually with No-Code Flow or Lua 5.2, compose 16-step Chiptune music, paint sprites, simulate Box2D & Theta\* AI, and export real 2D & 3D games to Android (`.apk` via Termux, zero laptop needed!), HTML5 Web (`.html`), Windows PC (`.exe`), and Linux!**

---

## Massive New Upgrades in v3.0.0 ("Apex Edition")

1. **⚡ "Vortex Flow" Visual Event-Sheet Builder (No-Code ➔ Lua 5.2 Compiler)**
   - Build complete 2D & 3D game logic using Construct/GDevelop-style `WHEN [Event] ➔ DO [Action]` cards on phone or PC — then compile **1-to-1 into clean, editable Lua 5.2** with a single tap!
2. **🧠 Autonomous Theta\* & A\* AI Pathfinding + 2D/3D Raycasting (`xaguzman/pathfinding`, Apache-2.0)**
   - Dynamic `NavigationGrid` + `ThetaStarGridFinder` obstacle-avoiding enemy navigation (`vortex.chaseAI`, `vortex.chaseTagAI`) + analytical 2D/3D Slab Raycasting (`vortex.raycast`) for line-of-sight and hitscan lasers!
3. **✨ Pro Easing & Yoyo Animations (`Universal Tween Engine 6.3.3`, Apache-2.0)**
   - Animate positions, scales, and rotations with Robert Penner curves (`BOUNCE`, `ELASTIC`, `BACK`, `SINE`, `QUAD`, `EXPO`) and infinite Yoyo loops (`vortex.tweenPos`, `vortex.tweenScale`, `vortex.tweenRot`, `vortex.tweenTagYoyo`)!
4. **🎵 Built-in 16-Step Polyphonic Chiptune Music Composer & Synth**
   - 4-channel retro tracker (25%-pulse Square Lead + Triangle Bass + Pitch-Sweep Kick + Noise Hi-Hat) with interactive 16-step note grid and 4 instant genre presets (`CYBERPUNK`, `DUNGEON`, `BOSS`, `SKYCHILL`) — zero MP3 asset bloat!
5. **🌐 1-Click Single-File HTML5 Web Game Exporter (`.html`)**
   - Exports any 2D or 3D game into a **100% self-contained `.html` file** (Canvas 2D + Perspective 3D Renderer + WebAudio Chiptune & SFX Synth + Mobile Touch D-Pad + WASD Keyboard) ready for **itch.io**, Chrome, Discord, or mobile sharing!
6. **↶ / ↷ 40-State Undo / Redo History + 10 Built-in Starter Templates**
   - Includes **Cyber Hunters AI Maze 2D** (Theta\* AI + Tweens + Chiptune + Flow Sheet) and **Neon Citadel Siege 3D** (Universal Tween Crushers + 3D Raycast Boss Battle)!

---

## Core Engine Powerhouses (v2.0 + v3.0)
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

## Downloads (GitHub Releases — `v3.0.0`)

| Platform | File | Description |
|---|---|---|
| **Android (Phone/Tablet)** | `VortexEngine-Studio-v3.0.0.apk` | Full 2D/3D Mobile Game Engine IDE + Flow + Chiptune + Termux APK & HTML5 Exporter |
| **HTML5 Web (Instant Play)** | `CyberMazeHunters-Web.html` | Zero-dependency playable HTML5 Web export of *Cyber Hunters: Theta\* AI Maze* |
| **Windows 10/11 (64-bit)** | `VortexEngine-Studio-Windows-x64.exe` | Native Windows Studio IDE + `.html` / `.exe` / `.jar` / Android Exporter |
| **Linux (x86_64)** | `VortexEngine-Studio-Linux-x86_64` | Self-contained Linux Desktop Studio IDE (`chmod +x` & run) |
| **Cross-Platform JVM** | `VortexEngine-Studio-Desktop-v3.0.0.jar` | Portable Desktop Studio (`java -jar VortexEngine-Studio-Desktop-v3.0.0.jar`) |
| **Sample Game (Android)** | `CyberHuntersAI-Android-Sample.apk` | Standalone Theta\* AI + Tween + Chiptune + Box2D game compiled via Termux pipeline |
| **Sample Game (Windows)** | `CyberHuntersAI-Windows-Sample.exe` | Standalone Theta\* AI + Tween + Chiptune + Box2D game exported as a Windows `.exe` |

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
