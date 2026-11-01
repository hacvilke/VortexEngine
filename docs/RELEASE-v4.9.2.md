# Vortex Engine Studio v4.9.2 — "Studio Kit Edition"

## Theme changer
- **Palette dropdown** in the desktop top bar (swatch strip + menu): pick a theme, flip **Dark mode**,
  **Import theme…**, export the current theme, or open **All themes…** for the full picker.
- **Three built-in palettes**: Paper (light), Midnight ink (dark), Drafting grey — plus any theme you
  import. A theme is 25 colour slots; import accepts theme JSON, a bare slot map, or a plain list of
  `#hex` colours (auto-mapped by lightness/chroma).
- Everything repaints: docks, inspector, console, scrollbars, the 2D paper grid, the grain texture
  (brightens on dark sheets), and the wooden desk (walnut → espresso). The **Blueprint graph stays
  blueprint-blue**, tinted deeper in dark mode. Game colours are untouched.
- Phone: `◐ PALETTE` chip in the header opens the same sheet; the Activity rebuilds on change.

## Docs that teach first
- `index.html` is now a **guided tour**: the real studio with 7 highlighted, numbered regions; an
  actual in-game UI with 6 highlighted elements, each with the exact call that draws it; a first
  game in four steps; the theme changer; Forge AI; healthy audio levels.
- The full topic corpus and the generated `vortex.*` index moved to **`api.html`**.
- Figures are real captures (`scripts/make_docs_figures.py`), embedded as data URIs → one offline file.

## Forge AI knows the workspace
- Sends **every file in full** (28 KB budget, active file first) instead of a 2.4 KB preview.
- Sends every entity (tag, mesh, pos, scale, rot, colour, material, body, collider), blueprint
  nodes/wires, flow rules, custom assets, and all project facts + a runtime environment report.
- New **WORKSPACE LAW** rules: extend the existing tree, reuse paths/names, `edit_file` before
  `write_file`, keep every hook, never replace the project with a fresh standalone sample.

## Icon
- `scripts/make_icon.py` draws the mark at 8× supersampling and writes every density:
  `mipmap-*/ic_launcher.png` (48–192), `desktop/resources/icon.png` + `icon.ico`, `docs/icon-512.png`.
- APK launcher, Windows `.exe` (windres), Linux binary and desktop taskbar/alt-tab all use it.

## Calmer audio
- Lead 0.22→0.125, bass 0.20→0.11, kick 0.24→0.15, hats 0.08→0.042; soft-knee limiter with a
  −2.2 dBFS ceiling; separate buses (desktop SFX 0.72 / music 0.42, Android music 0.55).
- Result: music bed peaks at **−13.6 dBFS** and sits well under effects — the game-audio convention
  (whole mix ≈ −23 LUFS, music 6–12 LU under SFX). Measured by a headless test.

## Build fixes
- The APK never bundled `app/src/main/assets` — paper fonts and docs figures were missing from every
  build. They ship now, and the freshly built runtime dex is no longer shadowed by the stale repo copy.
- New CLI hooks: `--theme <id>`, `--palette-shot <png>`.
- Headless suites extended: **Theme Changer**, **Forge Workspace Snapshot**, **Audio Mix** — all pass.

## Assets
| File | Size |
|---|---|
| VortexEngine-Studio-v4.9.2.apk | 6.4 MB (signed, v2+v3) |
| VortexEngine-Studio-Windows-x64.exe | 5.7 MB |
| VortexEngine-Studio-Linux-x86_64 | 5.6 MB |
| VortexEngine-Studio-Desktop-v4.9.2.jar | 5.6 MB |
