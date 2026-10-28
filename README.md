# Vortex Engine Studio — v4.7.0 ("Tile & Material Edition")

**Vortex Engine** is an open-source, mobile-first **AND** desktop-ready **2D & 3D Game Engine + Visual/Code Studio IDE** that runs natively on **Android**, **Windows (`.exe`)**, **Linux**, and **HTML5 Web**, and compiles standalone Android `.apk` games directly on-device via **Termux** — zero PC or cloud backend required.

---

## What's New in v4.7.0 (Tile Maps, Prefabs & Procedural Materials)

### 1. Tiled `.tmx` import — and a painter so you never need Tiled
- **Import Tiled Map (.tmx)** in the File menu reads orthogonal maps with embedded **or external `.tsx`** tilesets, `CSV` and `base64` (raw / **zlib** / **gzip**) layer data, flip/rotation flags and object groups. Tile colours are sampled from the real tileset PNG (with corner probing for sprites with transparent centres and a deliberate zero-blue palette fallback when the image is missing).
- The import lands in the scene tree as a **`TileWorld_<map>`** node: one child per layer, horizontally merged tile runs (a 6×4 test map becomes 29 entities instead of 54), `Collision`/`Solid`/`Wall`/`Block` layers become static colliders, and `<object>` entries become props, solid static bodies or the playable **Hero** when named `spawn`/`player`/`start`.
- **Built-in tile brush** (Tools ▸ Tile Paint Mode): pick a swatch from the strip at the bottom of the viewport, drag to paint, right-drag to erase, toggle **solid** cells for walls, name the layer, and **Merge Tile Runs** to compact a layer into horizontal runs. Every painted cell is an ordinary scene-tree entity, so it is selectable, tintable and scriptable.
- The **phone** gets the same painter: `🧱 TILES` on the 2D toolbar opens the swatch grid, then tapping/dragging on the canvas paints into the chosen layer (`Merge Runs` included).

### 2. Scene-tree prefabs
- Select any node → **Tools ▸ Create Prefab from Selection** stores the whole subtree (root + children + local transforms). **Tools ▸ Prefab Palette** instantiates a copy under the selected node with fresh ids and rewired parent links, so a `Turret/Barrel/Muzzle` assembly drops in with one line of UI.
- Seven built-ins ship with every project (Player Starter, Enemy Walker, Pickup Coin, Platform Block, Light Marker, Crate, Pillar). Prefabs travel inside the `.vortex` project JSON — no extra asset files, no storage bloat.

### 3. Procedural materials & surface patterns (no texture files)
- **Material presets** per entity: `MATTE`, `METAL`, `GLASS`, `EMISSIVE`, `UNLIT` drive specular strength, shininess, ambient response and emissive shading. Emissive objects bloom in the 2D viewport too.
- **Surface patterns** derived from world position: `CHECKER`, `STRIPES`, `GRID`, `BRICK`, `NOISE`. Deterministic, resolution independent, zero assets — stone floors, brushed metal and tiled walls without downloading a single PNG.
- **Sky & atmosphere pass**: vertical sky gradient from the light rig, warm horizon band that widens as the sun drops, sun disc with multi-ring bloom, and stars once the sky is genuinely dark.

### 4. Engine research dossier
- `ENGINE_RESEARCH.md` now documents all **21 subsystems** a custom mobile+PC engine needs, Vortex's honest status per row, a table of **permissively licensed** projects worth adopting (bgfx/wgpu/raylib/Filament/SDL2/LWJGL/libGDX/KTX2/Jolt/Bullet/dyn4j/Box2D/miniaudio/SoLoud/glTF/ufbx/DragonBones/Artemis…), per-subsystem performance targets, and the ranked gap list (glTF import → point lights + shadow maps → atlas packer → frame-graph profiler → gamepad/safe-area → string tables).

## What's New in v4.6.0 (Honest Forge AI, Real Lighting Rig & Compact Chat)

### 1. Forge AI that answers instead of thinking out loud
- **No more scratchpad leaks.** Reasoning models (gpt-oss / qwen3 / deepseek-r1) used to dump their planning ("We need answer user. Need parse. We are Vortex Forge AI, JSON only…") into the chat bubble. The client now requests `reasoning.exclude`, only accepts `reasoning` text when it actually contains the JSON answer, and `sanitizeReply()` strips fenced blocks, JSON scaffolding and planning sentences. If a model still returns nothing usable you get one short line with a retry suggestion — never a monologue.
- **1-tap questions.** The agent can now return `questions: [{text, options[], allowText}]`, rendered as answer chips plus a text box inside the chat. Answering a chip sends it straight back as the next turn, so "make a game" becomes *2D or 3D?* → *Explorer / Platformer / Shooter / Survival* instead of a guess. Wired on desktop **and** phone.
- **Prompt rules enforced**: one JSON object, terse `reply`, act when the request is clear, ask when it is genuinely ambiguous, never invent API names.
- **Robust parsing**: brace-walking JSON extraction plus trailing-comma repair; empty/broken model answers fall back to the offline generator instead of an error wall. Small talk ("hello", "thanks") is answered like a person, with a next-step question card.
- **Compact chat UI**: bubbles are measured and wrapped to the live dock width (no more giant half-empty boxes), user messages are right-aligned, tool executions render as `✓ create_file: objects/boss.js` badges with **Undo changes** / **Playtest** buttons.

### 2. Control chrome that actually scales
- Hover pills, borders, icon sizes and corner radii now derive from `scaledF(...)` and the control's own height, so nothing turns into a tiny circle inside a big button when the UI is resized; buttons gained a soft outer hover glow, dropdowns/fields/tabs scale their radii too.

### 3. Unreal-inspired lighting rig for 3D
- New project-level rig: **sun yaw / elevation / power / colour, ambient fill, contact shadows**. Scene dock gets Day / Dusk / Night presets plus numeric scrubbers (Godot's `DirectionalLight3D` + environment ambient, without the renderer rewrite).
- Shading is now **two-sided normal-corrected, half-Lambert** with hemisphere ambient, Blinn-Phong specular, rim light, warm highlights and cool shadow tint — plus **soft projected contact shadows** that fall off with height. Spheres and back faces no longer render nearly black; terrain triangle seams no longer show as black cracks.
- All settings persist in the project JSON and are exported with the game.

### 4. New starter: **3D Explorer — Green Hollow Valley**
- Walkable FastNoiseLite valley with a daylight rig, a cabin (roof parented to the house), fence, five spinning gems, a patrolling critter and a live HUD — a working "explorer" template you can grow into a full game.

## What's New in v4.5.0 (Godot-Style Scene Tree, Real Parenting & Mobile Tree Inspect)

### 1. Godot-Style Scene Tree (the real thing: nesting, not a flat list)
- The old flat entity list is gone. The left dock is now a **nested scene tree** with disclosure chevrons, tree guide lines, per-node 3D/2D badges and a green dot for dynamic bodies.
- **Drag a node onto another to reparent it** — the child keeps its exact on-screen transform, exactly like Godot's Scene dock. Dropping is blocked when it would nest a node inside its own descendant (the graph can never cycle).
- **Right-click**: `Rename` (or `F2` / double-click), `Duplicate`, `Add child`, `Detach to root`, `Toggle visible`, `Delete`. Deleting a parent removes its subtree; duplicating a parent copies its subtree with the internal parent links rewired.
- **Filter box** (Godot's scene-tree search) matches name, primitive, tag or full path, auto-expanding hits.
- The inspector banner now prints the **node path** (`PlayerShip/Turret/Muzzle`) so nesting is unambiguous while editing.
- **Phone**: the entity picker and inspector dropdown render the same indented tree, and a new **Scene Tree / Parent Node** section reparents or detaches with one tap — no drag precision needed on a 6" screen.

### 2. Transform-Driven Parenting (no renderer or solver rewrite)
- `SceneHierarchy` keeps a **local** transform per node next to the existing **world** transform, so every renderer (OpenGL ES, software 3D viewport, HTML5 export) and the JBox2D/3D solvers keep reading world coordinates untouched.
- Each frame publishes parent → child; externally edited worlds (gizmo drag, inspector scrubbers, scripts, physics results) are reconciled and folded back into the local transform, so nothing snaps back.
- Moving a parent drags its children instantly in the editor, during play, and in exported HTML5 builds (transforms are baked before export).

### 3. Readable Typography Everywhere (no more 8–10pt micro text)
- **14pt editorial scale**: the Desktop Studio now renders at a true **14pt UI scale with 14pt code**, using the first installed quality face (`Inter → Segoe UI → Ubuntu → Cantarell → DejaVu Sans`) and true monospace (`JetBrains Mono → Cascadia Mono → Consolas → DejaVu Sans Mono`). LCD/ClearType hinting is enabled so glyphs stay sharp instead of blurry.
- **Menu → Toolbar → Dock hierarchy** with generous spacing, so labels stop colliding and nothing clips at `1366×768`.
- **Android** got the same treatment: every Studio text size was raised (≈+1.5sp, dialog inputs to 14.5sp, buttons 13–14sp) for real phone readability.

### 4. Honest, Engine-Shaped Layout (fewer, purposeful tabs)
- **Menu bar** (`File · Edit · Create · View · Tools · Help`) plus a compact **icon-first tool strip** — the same shape as Godot/Unreal, instead of one long row of duplicate buttons.
- Only **9 verbs** fill the whole IDE: File · Edit · Create · View · Tools · Help, plus icon actions (New, Open, Save, Undo, Redo, 2D/3D, Gizmo, Colliders, Snap, Play) and two CTAs (`Play`, `Build`).
- Right dock consolidated **7 tabs → 6** (`Scene · Script · Logic · AI · Assets · Build`), with `Blueprint`/`Event Flow` nested under **Logic** and `Forge AI`/`LRM Sync` nested under **AI** — nothing removed, just de-duplicated.
- The **Viewport is now always the Viewport** (no tab next to a Blueprint graph), which frees the whole center column for the scene.
- **Godot-style property rows**: fixed-width label column + stretchable control (see `createPropertyRow`), so the Inspector reads like a real DCC tool.
- Phone layout rebuilt: cramped top tab strip replaced with a **thumb-reachable bottom navigation bar** (Scene · Inspect · Code · Logic · Play + More), giving the viewport ~34dp more height.

### 5. Godot/Unreal-inspired Viewport, Gizmos & Physics Tooling
- **Transform gizmos** in the viewport: `MOVE` (X/Y arrows), `SCALE` (handles), `SELECT`, with `Shift` for vertical axis drags, plus **grid snapping** and **collider overlay** (solid green = solid, dashed amber = trigger) and velocity vectors.
- **New physics solver (Unreal-Chaos-inspired)**, replacing the naive AABB-push resolver:
  - **Substepped integration** (4 substeps; **6 with CCD** for bullets/fast projectiles) so fast objects no longer tunnel through walls.
  - **Impulse + restitution + Coulomb tangential friction** resolution with **Baumgarte positional correction** (slop `5mm`, `0.82` percent).
  - **Sphere vs Sphere / Sphere vs Box / Capsule / AABB (SAT)** manifolds, deep-penetration axis fallback.
  - **Collision layers & masks** (`collisionLayer` / `collisionMask`) on every entity — the Unreal channel system, now in your scene JSON.
  - **Mass-aware impulses**, **coyote-time grounding** (`Entity.canJump()`), slope-normal deflection & friction on **FastNoiseLite 3D terrain**, and **JBox2D CCD bullets** in 2D with `World.setContinuousPhysics(true)`.
  - New script APIs: `applyForce`, `applyRadialImpulse` (shockwaves), `setGravityScale`, `setBounce`, `setFriction`, `setMass`, `raycastDist`, and a rewritten `raycast` (sphere + AABB + terrain) — mirrored in **JS, Java, Lua and HTML5 exports**.

### 6. Cleaner, Reticent Console & Chrome Copy
- Removed tutorial-style walls of text: the terminal banner and Studio console now emit a single-line status instead of three paragraphs of instructions.
- Kept the color-coded log semantics (emerald success / coral error / amber warning) and the live status pill.

---

## Core Highlights from v4.3.0

### 1. Multi-File Code Directory Sidebar & Modular Hook Chaining
- **Multi-File Script Organization (`scripts/main.*`, `objects/*`, `ui/*`)**: Replaced the single monolithic script buffer with a dedicated **File Directory Sidebar** (Desktop `FILES` explorer + open file tab bar; Android mobile horizontal file chips + `+ File`).
- **Seamless Multi-Module Compilation**: `VortexScriptEngine` automatically links and chains lifecycle hooks (`onStart`, `onUpdate(dt)`, `onCollision(a, b, tagA, tagB)`) across all project files in **ES6+ JavaScript**, **Native Java (`GameScript`)**, and **Lua 5.2**, and syncs every module file cleanly into `hacvilke/lrm` workspaces (`scripts/**`) and single-file HTML5 Web exports.

### 2. Laptop Screen Scaling (`85%`–`115%`) & Drag/Drop/Snap Split Code Tabs
- **Auto-Adaptive Laptop UI Scaling**: Automatically detects compact laptop screens (`1280×720`, `1366×768`, `1536×864`) and scales fonts, paddings, toolbars, and split proportions cleanly, plus an interactive **`85% / 92% / 100% / 115%`** UI Zoom selector in the header.
- **Drag, Drop & Snap Code Panes**: Drag any open code file tab directly over the right or bottom half of the Scene Viewport (with live dashed drop-zone preview) — or click **Snap Split** — to dock a live second code editor right beside or beneath the viewport.

### 3. Blueprint Visual Flow Diagram + In-Game GUI Blocks (Dialogs, Labels & Bars)
- **Lightweight Blueprint Node Graph (Alongside Classic `Flow`)**: Kept the existing `Flow` event-sheet tab intact and added a dedicated **`Blueprint Graph`** stage tab with draggable Event, Condition, Action, and **GUI** blocks connected via anti-aliased Bezier execution wires, compiling cleanly to JS, Java, or Lua.
- **In-Game GUI Blocks & Runtime HUD System**: Added visual & scripted **Dialog Boxes** (`showDialog(speaker, message, duration)` / `hideDialog()`), **Text Labels** (`setLabel(id, text, xPct, yPct, size, color)`), and **Progress / Health / XP Bars** (`setProgressBar(id, label, value, max, xPct, yPct, color)`) rendered across Desktop, Android Touch/HUD Overlay, and HTML5 Web!

### 4. Continuous Multi-Turn Forge AI Chat with Contextual IDE Tools
- **Multi-Turn Conversational Agent**: Upgraded Forge AI from single-shot prompts into a persistent multi-turn chat session (`Clear Chat` to reset) on both Desktop and Android.
- **Contextual IDE Tool Execution**: Forge AI inspects your active file tree, entities, Blueprint nodes, and console state and executes structured IDE tools (`create_file`, `edit_file`, `delete_file`, `upsert_entity`, `remove_entity`, `add_blueprint_node`, `run_test`) with automatic Undo checkpoints and live 60-frame headless verification badges.

### 5. Instant (`0ms`) Audio Cutoff, Playtest-Only FPS & Zero-Leak Engine Polish
- **Instant Audio Cutoff on Stop**: Tracked active `SourceDataLine` (Desktop) and `AudioTrack` (Android) handles with atomic generation tokens so stopping Play calls `stop() + flush() + close()` immediately (`0ms` trailing chiptune buffer bleed).
- **Playtest-Only FPS & Zero Idle Churn**: Stopped the `16ms` viewport tick loop in Editor mode (`RENDERMODE_WHEN_DIRTY` on Android OpenGL ES) so idle Editor mode uses ~0% CPU and never displays fake FPS counters — FPS telemetry runs strictly during live game playtests.

---

## Core Highlights from v4.2.0 ("Monolith White Edition")

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
