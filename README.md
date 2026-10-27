# Vortex Engine Studio — v4.2.0 ("Monolith White Edition")

**Vortex Engine** is an open-source, mobile-first **AND** desktop-ready **2D & 3D Game Engine + Visual/Code Studio IDE** that runs natively on **Android**, **Windows (`.exe`)**, **Linux**, and **HTML5 Web**, and compiles standalone Android `.apk` games directly on-device via **Termux** — zero PC or cloud backend required.

---

## What's New in v4.2.0 ("Monolith White Edition")

### 1. Zero-Blue Monochrome Carbon + Crisp White (`#FFFFFF`) Design System & Vector Icons
- **Pure Neutral Carbon + Crisp White UI**: Unified every Desktop and Android Mobile screen, control, HUD pill, sprite default, and launcher icon under a zero-blue **Monochrome Carbon (`#09090B` / `#101013` / `#18181C`) + Crisp White (`#FFFFFF`)** aesthetic (`PRIMARY` solid white with carbon ink, `ACCENT` crisp white-outlined carbon, `SECONDARY` neutral zinc, `DANGER` crimson).
- **Custom Vector Iconography & 60 FPS Micro-Animations**: Anti-aliased procedural vector icons (`PLAY`, `STOP`, `PLUS`, `FOLDER`, `SAVE`, `EXPORT`, `TERMINAL`, `CODE`, `FLOW`, `SPARKLE`, `SYNC`, `GRID`, `WAVE`, `CUBE`, `UNDO`, `REDO`) with smooth hover/press interpolation and sliding white active-tab underline pills.
- **Smoothly Draggable 8px Splitters**: Fixed Desktop `JSplitPane` divider order and panel minimum sizes with custom 3-dot white grip handles and resize cursors so left Scene/Inspector, center Viewport/Terminal, and right Tools docks drag effortlessly across the entire window.

### 2. Native Java Game Scripting (`VM: Java`) + 5-Runtime Architecture
- **Write Games Directly in Native Java (`public class GameScript`)**: Powered by embedded pure-Java **[BeanShell 2.0b6](https://github.com/beanshell/beanshell)** (`Apache-2.0`) alongside **ES6+ JavaScript (Mozilla Rhino)**, **Lua 5.2 (LuaJ)**, **Vortex Flow (`WHEN -> DO` compiling to Java, JS, or Lua)**, and **VortexScript (`VXS`)**.
- **Full Cross-Platform Parity**: Native Java games (`IRON_SENTINEL_JAVA_2D` starter template + Forge AI Java generation) run identically on Desktop `.exe`/Linux, Android Studio & Termux standalone `.apk`s, and single-file **HTML5 Web (`.html`)** via automatic Java-to-ES6 web transpilation.

### 3. Built-In Interactive OS Shell Terminal & Hardened Run Commands
- **Interactive Shell Dock (`DesktopTerminalPanel` + Mobile Shell Bar)**: Run `powershell.exe` (Windows) or POSIX `sh` (Linux/Android) commands interactively inside Studio with live streaming stdout/stderr, `cd <dir>` persistence, Up/Down command history, `Stop` process kill, and automatic `PATH`/`Path` enrichment for active JDK, Go (`~/go/bin`, `%USERPROFILE%\go\bin`), and LRM (`%LOCALAPPDATA%\Programs\lrm`, `~/.local/bin`) directories.
- **UTF-8 Native Windows `.exe` Launcher**: Automatically prepends the resolved `javaw.exe` `bin\` folder to `PATH` and launches with `-Dfile.encoding=UTF-8`.

### 4. Automatic System Prober & One-Click `hacvilke/lrm` Dependency Setup Wizard
- **Startup Dependency Check & Auto-Installer**: Automatically inspects OS/arch, Java runtime, Go (`golang`) toolchain, PowerShell/curl, and `hacvilke/lrm` CLI status (filtering out unrelated `npm i -g lrm` shims). On Windows, offers one-click installation via official PowerShell `irm https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1 | iex` (with `go install github.com/hacvilke/lrm/cmd/lrm@latest` source-build fallback), verifies `lrm version`, initializes `~/.vortex-lrm-workspaces/<project>`, and manages background **`lrm daemon`**, **`lrm receive`**, and **`lrm dashboard`** processes directly from Studio!

### 5. Fixed & Upgraded Vortex Forge AI (Groq + OpenRouter Free + Saved BYOK)
- **Groq Reasoning & Live Model Discovery**: Replaced retired `llama-3.3-70b-versatile` (`HTTP 404`) with active **`openai/gpt-oss-120b`** (`max_completion_tokens >= 8192` reasoning budget + `<think>` block stripping) and live `/openai/v1/models` auto-fallback + `Fetch Models` dropdown.
- **OpenRouter Zero-Cost Auto-Fallback**: Replaced high-reservation defaults (`HTTP 402`) with `$0` **`openrouter/free`** (`max_tokens: 2200`) and automatic `HTTP 402` retry on free tier, plus persistent API key & model preferences across sessions.

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
