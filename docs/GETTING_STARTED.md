# Getting started with Vortex Engine (10 minutes)

You do **not** need to know how to code to make something move in Vortex. You need three things:
a project, a play button, and a way to ask questions. All three are in the app.

---

## 1. Install / open the app

| You have | Open |
|---|---|
| Windows | `VortexEngine-Studio-Windows-x64.exe` |
| Linux | `VortexEngine-Studio-Linux-x86_64` (chmod +x first) |
| Android | `VortexEngine-Studio-v4.x.x.apk` |
| Nothing installed | the desktop `…-Desktop-v4.x.x.jar` (`java -jar …`) |

No installer, no account, no internet needed.

---

## 2. Make a project

**Fastest path to the block game:** press the **Blockfall** button in the top toolbar (desktop) or
**▶ PLAY BLOCKFALL** on the Android home screen. That is the 3D voxel game, already set up and
running — nothing to configure.

Otherwise: `File ▸ New Project…` → pick a starter → **Create**.

| Starter | What it teaches |
|---|---|
| **3D Voxel: Blockfall** | a Minecraft-style block world: mine, build, fly, survive |
| **3D Explorer: Green Hollow Valley** | a walking simulator with lighting, gems and an NPC |
| **2D Action: Starbound Cavern** | platforms, gravity, enemies, collecting |
| **3D Sandbox / 2D Sandbox** | empty scenes for when you already know what you want |

Press **▶ PLAY GAME** immediately. Play the starter for a minute *before* changing anything —
you learn what the engine already gives you for free.

---

## 3. Learn the four places on screen

```
┌───────────────────────────── VORTEX ─────────────────────────────┐
│ menu bar: File Edit Create View Tools Help        [▶ PLAY GAME]  │
├──────────┬──────────────────────────────┬────────────────────────┤
│ SCENE    │                              │  Scene / Script / AI   │
│ TREE     │        VIEWPORT              │  Logic / Assets / Build│
│ (nodes)  │   (drag objects, click them) │                        │
├──────────┤                              │  INSPECTOR             │
│ INSPECTOR│                              │  Transform / Rendering │
│ (values) │                              │  Physics               │
├──────────┴──────────────────────────────┴────────────────────────┤
│ Console (green = ok, red = error)  │  Terminal                   │
└──────────────────────────────────────────────────────────────────┘
```

* **Scene tree** — every object in your game is a node. Click to select. Drag one node onto
  another to make it a child (children move with the parent).
* **Viewport** — the world. Drag objects to move them; scroll to zoom.
* **Inspector** — the selected node's numbers: position, size, colour, body type, and (3D)
  material + surface pattern.
* **Console** — always check it. The first red line is the real error.

---

## 4. Your first change

1. Click the ground/terrain node in the scene tree.
2. In the Inspector, change **Tint Colour**.
3. Press **▶** again.

That is the whole loop: **change one thing → play → look**. Everything else is this loop with
more interesting things to change.

---

## 5. Where to get answers (no internet required)

* `Help ▸ Docs & Getting Started…` — 10 topics with real API names.
* `Help ▸ How do I…?` — type a question in your own words ("how do I jump?").
* **AI tab ▸ Forge AI** — ask in plain language; it reads your actual project first, and it will
  quote the same docs when you ask a how-to question.
* A phone has the same thing: bottom bar `More ⋯ ▸ Docs: …`.

---

## 6. Adding gameplay with code (when you want it)

Open the **Script** tab. A script has a few optional functions:

```js
function onStart() {              // runs once
  vortex.setBanner('Collect 3 gems');
}

function onUpdate(dt) {           // every frame, dt = seconds since last frame
  if (!vortex.exists('Player')) return;
  vortex.setVel('Player', vortex.joyX() * 8, vortex.getVY('Player'), -vortex.joyY() * 8);
}

function onCollide(a, b, tagA, tagB) {
  if (tagB === 'coin') { vortex.destroy(b); vortex.addScore(100); vortex.playSound('coin'); }
}
```

That is a complete game loop: move, react, score. Copy it, change the numbers, see what happens.

---

## 7. Voxel starter cheat sheet

| Action | Desktop | Phone |
|---|---|---|
| Move | `WASD` | left joystick |
| Jump | `SPACE` | **A** button |
| Fly | `F` | – |
| Mine | left click | tap the world |
| Build | right click | long-press / second finger |
| Pick block | `1`–`9`, middle click | – |
| Look around | drag | drag on the world |

In the editor (not playing) you can also sculpt: `Tools ▸ Voxel Block Palette…`, then left-click
to mine and right-click to build straight in the viewport.

---

## 8. Exporting

`Build` tab → Windows / Linux / Android / HTML5. Play for a minute before exporting; a bug you
exported is a bug someone else now owns.

---

## Stuck?

1. Read the first red line in the Console.
2. `Help ▸ Docs`, search the topic.
3. Ask Forge AI: "why is my player stuck?" — paste the console line if you have one.
4. Still stuck: `Tools ▸ Documentation ▸ Troubleshooting` covers the five most common causes.
