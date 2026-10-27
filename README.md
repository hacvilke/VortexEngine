# Vortex Engine Studio — v4.1.0 ("Hyperion UI Edition")

**Vortex Engine** is an open-source, mobile-first **AND** desktop-ready **2D & 3D Game Engine + Visual/Code Studio IDE** that runs natively on **Android**, **Windows (`.exe`)**, **Linux**, and **HTML5 Web**, and compiles standalone Android `.apk` games directly on-device via **Termux** — zero PC or cloud backend required.

---

## What's New in v4.1.0 ("Hyperion UI Edition")

### 1. Unified Dark Obsidian + Electric Cyan Design System (Zero Rainbow Buttons, Zero Default OS Widgets)
- **Single Cohesive Visual Language Across Android & Desktop**: Unified the entire Studio under a clean **Dark Obsidian (`#090D16` / `#0D1320` / `#141E30`) + Electric Cyan (`#00E5FF`)** theme with 4 semantic button variants (`PRIMARY` solid cyan, `ACCENT` cyan-outlined obsidian, `SECONDARY` slate-bordered obsidian, and `DANGER` muted rose).

### 2. Android Mobile Layout & Custom UI Overhaul (`VortexMobileUi`)
- **Responsive Portrait & Landscape Ergonomics**:
  - `fitsSystemWindows` + dark status/navigation bar integration (`#0D1320` / `#090D16`) so system bars and gesture insets never clip headers or bottom toolbars on Android 14/15/16.
  - **Split-Preview Portrait Inspector**: In portrait mode, the Inspector opens as a **56% height bottom drawer** (`⤢ FULL HEIGHT` / `⤡ SPLIT VIEW` toggle) so you can see live 2D/3D viewport changes while editing transforms, physics, and flipbook animations!
  - **Density-Scaled HUD & Arcade Touch Controls**: Converted raw-pixel HUD text/pills to `dp`/`sp` scaling so 1080p/1440p screens stay crisp and readable, and unified the `A` / `B` touch pads with spring-animated cyan pulse rings.
- **Custom Dark Mobile Controls & Animations**:
  - Replaced 100% of default white/gray Android `Spinner`s, `CheckBox`es, and `AlertDialog`s with custom **`VortexDropdown`** dark `PopupWindow` selectors (with `180°` animated chevrons), **`VortexToggle`** animated pill switches, glowing focus inputs, custom dark modals, tactile spring press feedback, and smooth tab auto-centering + entrance transitions.

### 3. Modern Custom-Painted Desktop Studio UI (`DesktopUiKit`)
- **Zero Swing Metal Blue Gradients**: Replaced default Swing `JButton`, `JComboBox`, `JCheckBox`, `JOptionPane`, `JScrollBar`, and `JSplitPane` widgets with custom `Graphics2D` anti-aliased components:
  - **60 FPS Sliding-Pill Workspace Dock (`VortexTabStrip`)**: All 7 Studio workspaces (**Code**, **Flow**, **Forge AI**, **LRM P2P**, **Pixel**, **Audio**, **Export**) live cleanly in the right dock with smooth animated tab indicators.
  - **Custom `VortexDropdown` & `VortexToggle`**: 60 FPS rotating vector chevrons, custom dark popup lists, dynamic-width animated pill switches, sleek 7px dark scrollbars, and 1px hairline splitters.

---

## Core Highlights from v4.0.0 ("Hyperion Edition")

### 1. ES6+ JavaScript Scripting Runtime (**Mozilla Rhino 1.7.15.1** + Native Browser V8)
- **Quad-Runtime Scripting Architecture**: Choose per-project between **ES6+ JavaScript (`JS`)**, **Lua 5.2 (`LUA` via LuaJ)**, **Vortex Flow Visual Event-Sheet (`FLOW`)**, or **VortexScript (`VXS`)**:
  - **ES6+ JavaScript (`JS`)**: Powered on Android (Dalvik/ART bytecode-free interpreter mode) and Desktop JVM by **[Mozilla Rhino 1.7.15.1](https://github.com/mozilla/rhino)** (`MPL-2.0`, 0 CVEs) plus a built-in ES6 class pre-pass — supporting `class` / `constructor` / `extends` / `static`, `let` / `const`, arrow functions (`=>`), template literals (`` `Wave ${n}` ``), `for (const x of arr)`, `Map` / `Set`, object shorthand, and `console.log(...)`.
  - **Hardened Sandbox & Anti-Freeze Guard**: Custom `ClassShutter` blocks arbitrary Java reflection/process escape while an instruction-count quota (`250,000` ops per hook) cleanly aborts accidental infinite `while(true)` loops without freezing the Studio UI!
  - **Zero-Transpile Native Web Execution**: On HTML5 export, JavaScript games execute **natively at full V8 / SpiderMonkey JIT speed** in the browser (`0` regex transpilation overhead!).
- **Dual-Target Visual Event-Sheet (`⚡ Vortex Flow`)**: Visual `WHEN -> DO` rules now compile 1:1 into **clean, readable ES6+ JavaScript** *or* **Lua 5.2** with one tap.

### 2. Native Integration with `hacvilke` Open-Source Ecosystem (`lrm`, `lrm-mobile`, & `roforge`)
- **Serverless P2P Version Control & Cross-Device Sync (`🔄 LRM P2P` Tab & Desktop Hub)** — powered by **[`hacvilke/lrm`](https://github.com/hacvilke/lrm)** & **[`hacvilke/lrm-mobile`](https://github.com/hacvilke/lrm-mobile)**:
  - **Git/LRM-Friendly Multi-File Project Unpacking (`LrmWorkspaceBridge`)**: Automatically decomposes monolithic `.vortex.json` projects into diff-friendly directory trees (`vortex.manifest.json`, `scripts/main.js`, `scene/entities.json`, `flow/rules.json`, `assets/sprites/*.sprite`, `assets/meshes/*.obj`) for clean per-file Blake3 content-addressed commits and vector-clock merges!
  - **One-Tap P2P Port Key Sync (`lrm1_...`)**: Commit snapshots, view the DAG commit graph, discover LAN peers via mDNS (`lrm peers`), generate encrypted QUIC/TLS 1.3 Port Keys (`lrm share`), or pull a project straight from your phone to Windows/Linux (`lrm join <PORTKEY> --init`) — zero servers or accounts!
  - **Zero-Config `inbox/` Asset Drop**: Drop `.obj` 3D meshes, `.sprite` pixel art, or `.js`/`.lua` scripts from any phone/laptop browser via `lrm receive` (`http://<device-ip>:8788`), then tap **Import Repo + Inbox** in Studio to auto-register every dropped asset into your scene!
  - `setup_termux.sh` now automatically installs Android 14/15/16 PIE-compatible `lrm-mobile` alongside `aapt2`, `d8`, and `apksigner`.
- **Vortex Forge AI Studio Agent (`🤖 FORGE AI`)** — inspired by **[`hacvilke/roforge`](https://github.com/hacvilke/roforge)**:
  - **Zero-Backend BYOK + Offline Generator**: Describe any 2D or 3D game mechanic in natural language! Works **100% offline** out-of-the-box via a procedural scene + ES6 JS generator, OR connect your own API key directly over HTTPS to **Groq**, **Google Gemini**, **OpenRouter**, or **OpenAI** — with **automatic Undo Checkpoints** before every AI edit.

### 3. Multi-Frame Flipbook Sprite Animation & Cross-Platform Save Storage
- **Multi-Frame Flipbook Animation**: Entities now support comma-separated frame sequences (`spriteFrames = "knight_1,knight_2"`, `animFps = 8`) plus live runtime switching via `setSpriteAnim(name, "run_1,run_2", 10)` across **Android OpenGL ES 2.0/3.0**, **Desktop 60 FPS Viewport**, and **HTML5 Canvas**!
- **Persistent Save-Data (`VortexSaveStore`)**: Save high scores, unlocked levels, and player loadouts across sessions with `saveData(key, value)`, `loadData(key, defaultVal)`, and `loadNum(key, defaultNum)` — backed by JSON disk persistence on Android/Desktop and `localStorage` on HTML5 Web!
- **Spatial Target Queries**: Added `findNearestTag(fromName, tag, maxRange)` for instant auto-aim turrets, roguelite weapons, and homing missiles in 2D and 3D.

### 4. Two New Flagship ES6+ JavaScript Game Templates (12 Built-In Templates Total!)
1. **Neon Survivors Swarm (ES6 JS 2D)**: Bullet-heaven cyber arena written in modern ES6 classes (`WaveDirector`), featuring multi-frame animated Knight & Drone sprites, `findNearestTag` auto-aim plasma bolts, `A = Dash Burst`, `B = Shockwave Nova`, XP gem leveling, and persistent high-score storage!
2. **Starhull Mecha Raiders 3D (ES6 JS 3D)**: Procedural FastNoiseLite 3D canyon raider with `MechaRig` ES6 class, 3D raycast railgun lock-on, Universal Tween Engine hovering drone squadrons, jump-jets, and persistent best score!

---

## Integrated Open-Source Libraries & Ecosystem Tools

| Subsystem | Project / Library | License | Role in Vortex Engine v4.0 |
| :--- | :--- | :--- | :--- |
| **ES6+ JavaScript VM** | **[Mozilla Rhino 1.7.15.1](https://github.com/mozilla/rhino)** | MPL-2.0 | Sandboxed ES6+ JS interpreter (`VERSION_ES6`, `ClassShutter`, 250k-op loop guard) on Android & Desktop |
| **P2P Sync & VCS** | **[hacvilke/lrm](https://github.com/hacvilke/lrm)** & **[lrm-mobile](https://github.com/hacvilke/lrm-mobile)** | Open Source | Serverless P2P encrypted DAG version control, Port Key sync & Wi-Fi `inbox/` asset drops |
| **BYOK AI Agent Pattern** | **[hacvilke/roforge](https://github.com/hacvilke/roforge)** | Open Source | Zero-backend local BYOK + offline AI scene/ES6 generator with automatic Undo checkpoints |
| **Lua 5.2 VM** | **[LuaJ 3.0.1](https://github.com/luaj/luaj)** | MIT | Pure-Java Lua 5.2 scripting compiler & runtime |
| **2D Physics** | **[JBox2D 2.2.1.1](https://github.com/jbox2d/jbox2d)** | BSD-3-Clause | Rigid-body solver, impulses, restitution, friction & triggers |
| **AI Pathfinding** | **[xaguzman Pathfinding 0.2.6](https://github.com/xaguzman/pathfinding)** | Apache-2.0 | Grid A* & Theta* any-angle line-of-sight obstacle navigation |
| **Tween Animation** | **[Universal Tween Engine 6.3.3](https://github.com/AurelienRibon/universal-tween-engine)** | Apache-2.0 | Robert Penner easing curves (`SINE`, `BOUNCE`, `ELASTIC`, `BACK`) & yoyo loops |
| **3D Terrain Noise** | **[FastNoiseLite](https://github.com/Auburn/FastNoiseLite)** | MIT | OpenSimplex2 / Perlin / Cellular FBm procedural 3D heightmaps |

---

## Multi-Platform Build & Test Commands

```bash
# 1. Build Android Studio v4.0.0 APK (includes Rhino ES6 + LuaJ + JBox2D + LRM + Forge AI + Termux bundle)
./scripts/build_studio_apk.sh

# 2. Build Windows x64 .EXE, Linux x86_64 Binary & Portable Desktop Fat JAR + Run Headless Suite
./scripts/build_desktop.sh

# 3. Run End-to-End Termux APK (--fast & --from-source), Browser JS Check & LRM Verification
./scripts/test_termux_pipeline.sh
```

### One-Line Termux Mobile Toolchain + `lrm-mobile` Setup (On Android)
```bash
pkg update -y && pkg install -y curl && bash /sdcard/Download/VortexEngine/setup_termux.sh
```

---

## License
Released under the **MIT License** (with bundled permissively licensed open-source libraries under MIT, BSD-3-Clause, Apache-2.0, and MPL-2.0 unmodified JAR binary library).
