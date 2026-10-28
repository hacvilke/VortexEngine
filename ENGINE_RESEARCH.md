# Vortex Engine — Research: What a Custom Mobile + PC Engine Actually Needs

_Compiled 2026-10-28 for the v4.7.0 cycle. Every subsystem a game engine must own, the permissive
open-source projects worth building on, what Vortex already ships, and the honest gaps._

---

## 1. The subsystem checklist

An engine is a set of **runtimes** (must work on the device) plus **tools** (must work for you).
Tools are ~half the value — that is what separates an engine from a demo.

| # | Subsystem | Runtime requirement | Tool requirement | Vortex status |
|---|-----------|--------------------|------------------|---------------|
| 1 | **Platform layer** | window/GL context, input (touch, mouse, keyboard, gamepad), file IO, suspend/resume, safe areas, DPI | device preview | ✅ Android `GLSurfaceView` + desktop Swing; ⚠️ no gamepad, no safe-area insets |
| 2 | **Render: 2D** | batched sprites, sprite sheets, atlas, camera 2D, z-order | pixel-art editor, atlas packer | ✅ sprites + flipbook anim + pixel editor; ⚠️ no atlas packing |
| 3 | **Render: 3D** | meshes, materials, directional/point lights, shadows, fog, sky | material inspector, light gizmos | ✅ mesh prims + OBJ import, directional rig, fog; ⚠️ no point/spot lights, no shadow maps |
| 4 | **Shader/material system** | PBR-ish or stylised shading, instancing | material presets | 🟡 procedural shading + material presets; ❌ no custom shader authoring |
| 5 | **Culling / batching** | frustum cull, sort, merge draws | stats overlay | 🟡 painter's sort + draw stats; ❌ no frustum culling for tiles |
| 6 | **Physics 2D** | rigid bodies, joints, CCD, layers | body/collider inspector, debug draw | ✅ JBox2D (BSD) + custom solver, layers/masks, raycast |
| 7 | **Physics 3D** | box/sphere/capsule, constraints, CCD, heightfield | collider overlay | ✅ custom substepped solver + terrain-normal response; ⚠️ no joints, no true 3D CCD |
| 8 | **Animation** | sprite flipbook, skeletal, tweens | timeline | ✅ flipbook + Universal Tween (Apache); ❌ no skeletal |
| 9 | **Audio** | SFX + music, mixing, no leaks | chiptune/SFX lab | ✅ chiptune synth + procedural SFX, instant cutoff |
| 10 | **Input/UI runtime** | hit-testing, HUD, dialogs, touch overlay | GUI blocks | ✅ HUD/dialog/progress bars + touch overlay |
| 11 | **Scene model** | hierarchy, transforms, prefabs, serialisation | scene tree, drag-parent | ✅ `SceneHierarchy` tree, drag-reparent, prefabs = templates |
| 12 | **Assets** | import, cache, hot reload | importers | 🟡 OBJ, sprites, audio, **Tiled `.tmx` (v4.7.0)**; ❌ no glTF/FBX |
| 13 | **Scripting** | sandboxed runtime, hot reload, errors with line numbers | console, AI agent | ✅ JS (Rhino), Java, Lua (LuaJ), VXS + Forge AI tools |
| 14 | **Terrain / tilemaps** | heightfield + tile layers + tile collision | tile painter / import | ✅ FastNoiseLite terrain; ✅ **Tiled import (v4.7.0)**; ❌ no in-editor tile brush |
| 15 | **AI/pathfinding** | navmesh or grid, steering | agent debug | ✅ Theta*/A\* (`pathfinding`), `chaseTagAI` |
| 16 | **Particles** | pooled billboards | emitter editor | ✅ 2D/3D particle system |
| 17 | **Persistence** | save games, project files, versioning | undo/redo | ✅ project JSON, save store, undo stack |
| 18 | **Build/export** | APK, EXE, Linux, HTML5 | one-click export, CI | ✅ all four + GitHub Actions |
| 19 | **Profiling/debug** | FPS, frame time, entity counts, console | metrics panel | 🟡 status pill + console; ❌ no frame-graph profiler |
| 20 | **Networking** | state sync, RPC, prediction | lobby | 🟡 LRM P2P workspace sync only |
| 21 | **Localisation / accessibility** | string tables, UI scaling, contrast | — | 🟡 UI scaling; ❌ no string tables |

---

## 2. Open-source projects worth building on (licenses checked)

### Rendering
| Project | License | Why / why not for Vortex |
|---|---|---|
| **bgfx** | BSD-2 | Cross-platform renderer (D3D/Vulkan/Metal/GL). C++, needs JNI. Great long-term backend if we leave Java. |
| **wgpu** (Rust) | MIT/Apache-2 | Modern cross-platform GPU abstraction (WebGPU), mobile-ready. JNI or rewrite. |
| **raylib** | zlib | Perfect "small engine" reference: 2D/3D, text, audio, shaders in ~25k LOC. Our shading/pipeline decisions are modelled on it. |
| **Filament** (Google) | Apache-2 | Mobile-first PBR + IBL. Heavy, C++/JNI. The lighting model we approximate in software shading. |
| **SDL2** | zlib | Windowing/input/audio baseline; the de-facto platform layer reference. |
| **LWJGL** | BSD-3 | The Java path to real GL/Vulkan on desktop if Vortex gets a GPU desktop renderer. |
| **libGDX** | Apache-2 | The most mature Java game framework: batch renderers, atlases, FreeType, Box2D wrapper, asset manager. Best source of proven Java patterns. |
| **stb / stb_image** | MIT/public domain | Texture decode reference; we use `ImageIO`/`BitmapFactory` instead. |
| **KTX2 + Basis Universal** | Apache-2 | GPU-compressed textures (ASTC/ETC2 for mobile). The right answer when we ship real 3D content. |

### Physics
| Project | License | Notes |
|---|---|---|
| **JBox2D** | BSD-3 | Already integrated (2D). Direct Box2D port, deterministic, tiny. |
| **Box2D / Box2D v3** | MIT | Reference for solver behaviour; ERIN CATTO's docs are the best physics spec available. |
| **dyn4j** | BSD-3 | Pure-Java 2D with joints/motors — candidate upgrade if we need vehicles/ragdolls in 2D. |
| **Jolt Physics** | MIT | Best modern 3D engine (Horizon, Death Stranding 2). **No maintained Java binding** → would need JNI + per-ABI `.so` in the APK. |
| **Bullet** | zlib | Bigger, older, has Java ports (`jbullet`) but stale. |
| **ode4j** | BSD-3 | Pure-Java ODE port; evaluated this cycle. Works, but the C-port API needs manual contact-joint management and its resting-contact behaviour needed tuning we could not validate to our own bar → **not adopted**; our substepped 3D solver stays. |

### Audio
| Project | License | Notes |
|---|---|---|
| **miniaudio** | public domain/MIT | Single-header cross-platform audio; the model for our zero-leak device handling. |
| **SoLoud** | zlib | Small 3D audio engine with mixing — candidate for positional audio. |
| **TinySound / OpenAL Soft** | MIT / LGPL | LGPL is a distribution constraint on Android — avoided. Our own `SynthAudioEngine` + `ChiptuneGenerator` stays. |

### Assets & content pipeline
| Project | License | Notes |
|---|---|---|
| **Tiled + TMX/TSX/JSON format** | tool GPL, **format is open** | We implement the format, not the tool → no licence obligation. **Imported in v4.7.0.** |
| **Aseprite** | EULA/source-available | Pixel-art workflow reference; our built-in pixel editor mirrors its basics. |
| **glTF 2.0** | open spec (Khronos) | The one 3D format worth supporting next (JSON + bin + PNG). |
| **ufbx / Assimp** | MIT / BSD-3 | FBX/OBJ reference implementations. OBJ already supported; FBX is a JNI project. |
| **Universal Tween Engine** | Apache-2 | Already integrated (easing/anim). |
| **Spine / DragonBones runtimes** | proprietary / MIT | DragonBones is the permissive skeletal option if we add 2D bones. |

### Architecture, scripting, AI, tooling
| Project | License | Notes |
|---|---|---|
| **Artemis-odb** | BSD-2 | Pure-Java ECS. We keep a class-based entity model (script + editor ergonomics), ECS is the alternative if we ever need 100k entities. |
| **Ashley** | Apache-2 | Same story, more libGDX-bound. |
| **Rhino / LuaJ / BeanShell** | MPL-2 / MIT / Apache-2 | Already integrated — this is what makes multi-language scripting possible with zero native code. |
| **Godot** | MIT | **Primary UX reference**: scene tree, inspector property rows, dock layout, `DirectionalLight3D` naming. Studied (not copied code) across v4.4–v4.7. |
| **Unreal** | EULA (reference only) | Reference for physics feature set (channels, CCD, coyote time) and material/light vocabulary. |
| **`pathfinding` (Theta\*)** | Apache-2 | Already integrated for AI steering. |
| **OpenRouter / Groq APIs** | commercial, BYOK | Forge AI providers; keys stay local, no engine-side account. |

### Build, CI, distribution
| Project | License | Notes |
|---|---|---|
| **Android SDK build-tools (aapt2, zipalign, apksigner)** | Apache-2 | Our APK pipeline uses them directly — no Gradle, no network, builds on a phone via Termux. |
| **Termux** `com.termux.RUN_COMMAND` | Apache-2 | On-device build path. |
| **GitHub Actions** | — | CI builds all four targets per tag. |
| **Temurin JDK 17**, **MinGW-w64** | GPL+CE / GPL | Compiler/launcher-stub toolchain used by the build scripts. |

---

## 3. What "good" looks like per subsystem (targets Vortex is aiming at)

- **Frame budget:** 16.6 ms @60 fps on a mid-range phone (≈2019 ARM). Per-frame targets: physics ≤ 4 ms, render submit ≤ 6 ms, scripting ≤ 2 ms.
- **Memory:** < 120 MB RSS with a full scene; no per-frame allocation in the hot loop (pool particles, reuse buffers).
- **Determinism:** fixed 1/60 timestep, physics substepped 4× (6× with CCD) so behaviour matches across devices.
- **Startup:** < 1.5 s cold start on mobile; project load < 300 ms for 1k entities.
- **Text/UI:** ≥ 14 pt equivalent, ≥ 44 dp touch targets, contrast ≥ 4.5:1 — non-negotiable (the v4.4 lesson).
- **Export parity:** the same project must run identically on Android, Windows, Linux and HTML5 (with the documented differences: no filesystem, no threaded JIT).

---

## 4. Honest gap list & the plan

**Do next (high value, no native code):**
1. **glTF 2.0 import** (JSON + `.bin` + PNG) → real 3D content without a JNI pipeline.
2. **Point/spot lights + one shadow map slot** in the 3D pipeline (software + GLES), authored in the Scene dock.
3. **Atlas packer + tile brush** so Tiled is optional, not required.
4. **Frame-graph profiler** (per-phase timing HUD) — we already collect timings for the FPS pill.
5. **Gamepad/controller mapping** + safe-area insets on Android.
6. **String tables + a11y pass** (colour-blind-safe palette toggle).

**Needs native code (JNI + per-ABI binaries, decided against for now):**
- Jolt or Bullet for industrial-grade 3D physics.
- Filament/bgfx/wgpu as a GPU renderer (our GLES path is fine until we need real PBR + shadows).
- Basis/ASTC texture compression.
The blocker is identical for all three: shipping `arm64-v8a`, `armeabi-v7a`, `x86_64` binaries inside a
single APK built **on-device by Termux** — that is a distribution and build-pipeline project, not a
feature patch. It is deliberately deferred until the Java engine stops being the bottleneck.

---

## 5. Sources used this cycle

- Engine subsystem checklists and "tooling is half the engine" argument: r/gameenginedevs and Hacker News
  threads on building custom engines; Playgama's essential-systems list; SDL2 starter kit module list
  (window/timer/ECS/atlas/tilemap/profiler categories used as the checklist skeleton).
- Physics licences & feature comparison (Bullet zlib / Box2D MIT / Jolt MIT / PhysX BSD-3, Jolt's
  determinism and job system): 2026 physics-engine comparisons.
- Tiled **TMX map format** reference: element/attribute semantics, CSV + base64 encoding, gzip/zlib
  compression, global tile IDs and flip flags (`0x80000000` / `0x40000000` / `0x20000000`).
- Godot (MIT) editor sources for dock layout, node tree, inspector rows and `DirectionalLight3D`.
- Repo/code facts re-verified in this workspace: `ode4j` 0.4.2 **exists** on Maven Central
  (`org.ode4j:core`), is pure Java, and was smoke-tested (contact joints must be built manually; resting
  behaviour needed tuning → not adopted).
