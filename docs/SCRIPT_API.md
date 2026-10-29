# Vortex Script API reference

Every project has one or more script files. All files **share one scope** and each may define the
hooks below, so you can split objects/features into `objects/enemy.js`, `ui/hud.js`, … without extra
wiring.

## Hooks

| Hook | When |
|---|---|
| `onStart()` | once, when play begins |
| `onUpdate(dt)` | every frame (`dt` = seconds since last frame) |
| `onButton(name)` | `'A'` (jump/action) or `'B'` (secondary) pressed |
| `onCollide(a, b, tagA, tagB)` | two bodies touched |
| `onTouch(x, y)` | tap/click in world space |
| `onBlockMined(name)` / `onBlockPlaced(name)` | voxel edits |

## API surface

All of these live on the `vortex` object (JavaScript / Java) or are global functions (Lua).
The list is generated from the engine source, so it is always exactly what ships.

- `joyX()`
- `inputX()`
- `joyY()`
- `inputY()`
- `btnA()`
- `btnB()`
- `touchDown()`
- `touchX()`
- `touchY()`
- `exists()`
- `getX()`
- `getY()`
- `getZ()`
- `getVX()`
- `getVY()`
- `getVZ()`
- `getRotY()`
- `getRotZ()`
- `getTag()`
- `isGrounded()`
- `dist()`
- `distance()`
- `countByTag()`
- `findNearestTag()`
- `setPos()`
- `move()`
- `setVel()`
- `addVel()`
- `applyImpulse()`
- `applyForce()`
- `applyRadialImpulse()`
- `setGravityScale()`
- `setBounce()`
- `setFriction()`
- `setMass()`
- `setAngularVel()`
- `setFixedRotation()`
- `jump()`
- `setRot()`
- `rotate()`
- `setScale()`
- `setSpin()`
- `setColor()`
- `setSpriteAnim()`
- `destroy()`
- `spawn3D()`
- `spawn2D()`
- `particles()`
- `emitParticles()`
- `playSound()`
- `playMusic()`
- `stopMusic()`
- `shake()`
- `shakeCamera()`
- `setCamTarget()`
- `setCamDist()`
- `setSky()`
- `addScore()`
- `setScore()`
- `getScore()`
- `setHealth()`
- `addHealth()`
- `getHealth()`
- `saveData()`
- `loadData()`
- `loadNum()`
- `setBanner()`
- `setHudText()`
- `showDialog()`
- `hideDialog()`
- `setLabel()`
- `setProgressBar()`
- `removeUI()`
- `clearUI()`
- `log()`
- `chaseAI()`
- `chaseTagAI()`
- `raycast()`
- `raycastDist()`
- `tweenPos()`
- `tweenScale()`
- `tweenRot()`
- `tweenTagYoyo()`
- `noise2D()`
- `noise3D()`
- `terrainHeight()`
- `rand()`
- `clamp()`
- `time()`
- `voxelGet()`
- `voxelSet()`
- `voxelName()`
- `voxelBlockId()`
- `voxelSolid()`
- `voxelHeight()`
- `voxelSeed()`
- `voxelBreak()`
- `voxelPlace()`
- `voxelRegenerate()`
- `playerX()`
- `playerY()`
- `playerZ()`
- `playerYaw()`
- `playerPitch()`
- `setPlayerPos()`
- `setPlayerLook()`
- `playerFlying()`
- `setPlayerFlying()`
- `blockBreakAtCursor()`
- `blockPlaceAtCursor()`
- `selectedBlock()`
- `selectBlock()`

## Common recipes

### Move a player with the controls
```js
function onUpdate(dt) {
  if (!vortex.exists('Player')) return;
  vortex.setVel('Player', vortex.joyX() * 9, vortex.getVY('Player'), -vortex.joyY() * 9);
}
function onButton(btn) { if (btn === 'A') vortex.jump('Player', 12); }
```

### Collect coins
```js
function onCollide(a, b, tagA, tagB) {
  if (tagB !== 'coin') return;
  vortex.particles(vortex.getX(b), vortex.getY(b), vortex.getZ(b), 20, '#FACC15');
  vortex.destroy(b);
  vortex.addScore(100);
  vortex.playSound('coin');
}
```

### Wave spawner
```js
var timer = 0, wave = 1;
function onUpdate(dt) {
  timer += dt;
  if (timer < 2.5) return;
  timer = 0;
  for (var i = 0; i < wave; i++) {
    vortex.spawn3D('Enemy_' + wave + '_' + i, 'enemy', 'PYRAMID',
      -8 + i * 3, 4, -10, 1.2, 1.6, 1.2, '#EF4444', 'DYNAMIC');
  }
  wave++;
}
```

### HUD
```js
vortex.setLabel('hud_score', 'SCORE ' + vortex.getScore(), 5, 5, 15, '#FFFFFF');
vortex.setProgressBar('hud_hp', 'HEALTH', vortex.getHealth(), 100, 5, 92, '#7BE3C1');
vortex.showDialog('GUIDE', 'Press A to jump.', 4.0);
```

### Voxel (see [VOXEL.md](VOXEL.md))
```js
vortex.voxelSet(10, 20, 10, 'GLASS');
vortex.setPlayerPos(48, 40, 48);
```

## Debugging

* Syntax is checked as you type; the status pill shows OK or the first error.
* `vortex.log(x)` writes to the Console (green text).
* `AI tab ▸ "run a playtest"` executes 60 headless frames and prints what happened.
