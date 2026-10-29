# Troubleshooting — the five things that actually go wrong

## 1. "My player does not move"

Check in this order (most common first):

1. **Body type.** A `STATIC` body ignores velocity. The Inspector shows Body Type — make it `DYNAMIC`.
2. **Stuck inside a collider.** If the player spawned inside the floor, gravity holds it there.
   Move it up with the Inspector's Y field and try again.
3. **Unsaved script.** A dot on the file tab means unsaved changes — play always uses the saved file.
4. **Wrong hook name.** `onUpdate(dt)` — case matters, and there is no `Update()`.
5. **An error earlier in the frame.** Read the *first* red console line; later ones are echoes.

## 2. "Objects fall through the floor"

* The floor needs a **collider** (`BOX` or `CIRCLE`). Collider `NONE` means "decoration only".
* Very thin + very fast objects tunnel. Make the platform thicker than the movement per frame.
* In 2D, `gravityY` should be negative for a platformer (down is -Y).

## 3. "The screen is black / everything is dark"

* `Scene ▸ LIGHTING`: sun power and ambient should not be 0.
* Sky colour of `#000000` with no ambient = a genuinely black world.
* Camera: it needs a valid `cameraTargetName`, otherwise it sits at the origin looking at nothing.

## 4. "It is slow on my phone"

* Voxel world: lower **render radius** (Scene dock). 46 blocks is the default; 30 is fine on older phones.
* Big scenes: increase `fogFar` only as far as you can see; delete off-screen objects.
* Long scripts in `onUpdate` add up — cache lookups (`var p = vortex.getX('Player')` once per frame).

## 5. "Forge AI repeats the same thing / gives me code I did not ask for"

Forge has two modes:

* **Without an API key** it runs a built-in generator. It now derives counts, colours and names from
  your prompt, and how-to questions are answered from the built-in documentation instead of
  generating a scene. If it still feels canned, ask a *specific* question ("add a double jump") or
  connect a provider key in the AI tab for the full model.
* **With a key** it sends your actual project snapshot (files, entities, syntax check) to the model,
  so answers reference your real code.

Every change Forge makes appears as a badge in the chat with an **Undo** button.

---

Still stuck? Open `Help ▸ Docs`, or ask: *"why does my player not move?"* — the docs search and
Forge both understand plain language.
