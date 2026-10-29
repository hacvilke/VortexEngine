# Voxel worlds — mine, build, survive

Vortex has a first-class block world ("voxel") mode: a 96 × 96 × 64 grid of one-byte blocks with
greedy meshing, a first-person controller, mining/building and per-world saves. It runs on the phone
(OpenGL ES), on the desktop (software renderer) and in exported builds.

## Make one

`File ▸ New Project… ▸ 3D Voxel: Blockfall`, or turn any 3D project into a voxel world with
`Tools ▸ Voxel Regenerate World…` (asks for a seed).

## Controls

| Action | Desktop | Phone |
|---|---|---|
| Walk | `WASD` | joystick |
| Jump | `SPACE` | **A** |
| Sprint / sneak | `SHIFT` / `CTRL` | – |
| Fly | `F` (SPACE up, CTRL down) | – |
| Mine | left click | quick tap |
| Build | right click | long-press or second finger |
| Select block | `1`–`9`, `B` cycles, middle-click pipette | – |

## Sculpting in the editor

`Tools ▸ Voxel Block Palette…` picks the brush block. Then in the viewport: **left click mines,
right click builds**, middle-click picks the block you are pointing at. Changes are written into the
project file immediately, so they survive saving, exporting and reloading.

## Blocks

`Grass, Dirt, Stone, Sand, Wood, Leaves, Water, Plank, Glass, Brick, Lamp, Snow`

`Lamp` is emissive and lights the surface around it in the software renderer; `Water` is walk-through
and swim-speed; `Glass` and `Leaves` are transparent (you still see the inside faces).

## Scripting a block world

All of this works in JavaScript, Lua and Java:

```js
function onStart() {
  vortex.log('seed ' + vortex.voxelSeed());
  // a 5x5 stone platform under the player
  var px = vortex.playerX(), py = vortex.playerY(), pz = vortex.playerZ();
  for (var dx = -2; dx <= 2; dx++)
    for (var dz = -2; dz <= 2; dz++)
      vortex.voxelSet(floor(px) + dx, floor(py) - 1, floor(pz) + dz, 'STONE');
}

function onBlockMined(name) { vortex.addScore(10); vortex.playSound('coin'); }
function onBlockPlaced(name) { vortex.log('placed ' + name); }
```

### API

| Call | Meaning |
|---|---|
| `voxelGet(x, y, z)` | block id (0 = air) |
| `voxelSet(x, y, z, 'STONE' \| 3)` | place a block |
| `voxelName(id)` | `'Stone'` |
| `voxelBlockId('Brick')` | `10` |
| `voxelSolid(x, y, z)` | 1 / 0 |
| `voxelHeight(x, z)` | highest solid block in the column |
| `voxelBreak(x, y, z)` / `voxelPlace(x, y, z, id)` | edit with return value |
| `voxelSeed()` / `voxelRegenerate(seed)` | world seed control |
| `playerX() / playerY() / playerZ()` | feet-centre position |
| `playerYaw() / playerPitch()` | view angles |
| `setPlayerPos(x, y, z)` / `setPlayerLook(yaw, pitch)` | teleport / snap the view |
| `playerFlying()` / `setPlayerFlying(true)` | creative flight |
| `blockBreakAtCursor()` / `blockPlaceAtCursor()` | act on whatever the crosshair points at |
| `selectedBlock()` / `selectBlock('LAMP')` | hotbar |

### Events

`onBlockMined(blockName)`, `onBlockPlaced(blockName)` — both fire for player actions **and** for
script actions that go through `blockBreakAtCursor` / `blockPlaceAtCursor`.

## How it stays fast

* **Face culling** — only faces touching air (or transparent blocks) are meshed.
* **Greedy meshing** — neighbouring coplanar faces merge into one quad. A whole landscape is ~8–12k
  quads instead of 230k blocks.
* **Render radius** — the phone meshes a window around the player (`voxelRenderRadius`, default 46
  blocks); the desktop meshes everything.
* **Mesh caching** — the world bumps a version counter on every edit; renderers rebuild only when it
  changes or the player walks out of the window.

## Saving

The world is stored run-length encoded inside the project JSON (`voxelWorldData`), typically 60–120 KB
for a full landscape. Delete that field (or use Voxel Regenerate) and it is rebuilt from the seed
instead — the same seed always produces the same world.
