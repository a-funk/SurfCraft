# SurfCraft MODLOG

The journal for this mod (universal-modder `mod-any-game` loop). Confirmed facts, decisions, failures and
their causes, and the next step. Newest entries at the bottom of each section.

## Intake
- **Game:** Minecraft Java Edition **26.3** (latest stable, 2026-10-01), Fabric. macOS 26.2, Apple M4.
  Minecraft is not installed through the launcher on this Mac; development and testing use Loom's dev client
  (`./gradlew runClient`, offline dev account, its own `run/` folder). No user saves are touched.
- **Idea:** CS:S/CS:GO surf in Minecraft. Craftable surf ramp blocks that join into ramps of any size, and
  movement that is CS:S's own surf physics (air strafing, ramp clipping), ported from the browser port in
  `/Users/funk/code/sandbox/surf` (which replays 54 real CS:S server recordings).
- **Online/offline:** single player and servers the user runs. The mod must be on the server and client
  (registry sync refuses mismatched clients), so it cannot be used as a client-side movement cheat.
- **Done means:** the jar builds; the physics core replays real CS:S recordings within the surf repo's
  tolerances; ramps craft, place, join and drop in game; a scripted run in the real client surfs a built
  ramp without falling through, rampbugs or rubber-banding; screenshots of it.

## Recon
- `um kb search minecraft`: `games/gta-v/minecraft-passthrough.md` (Minecraft 26.3 + Fabric, JDK 25) and
  `techniques/oracles-how-agents-know-a-mod-works.md` (trace replay is the oracle for ports).
- Versions (from `meta.fabricmc.net`, maven, and the 26.3 example mod):
  Minecraft 26.3, Fabric Loader 0.19.5, Loom 1.18.2 (`net.fabricmc.fabric-loom`, no mappings: 26.x is
  unobfuscated, Mojang names), Fabric API 0.161.0+26.3, Gradle 9.7.1, JDK 25 (Homebrew `openjdk@25`
  25.0.4.1: `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`), JUnit 6.1.3.
- Source of truth, kept in the gitignored `local-content/decomp/` (never committed; the user wants all files inside this folder, not in `~/`):
  `common/`, `client/` (decompiled Minecraft, from `./gradlew genSources`), `jar-common/`, `jar-client/`
  (vanilla data and asset JSON), `fabric-api/<module>/` (Fabric API sources for 0.161.0+26.3),
  `fabric-repo/` (Fabric API test mods at tag `0.161.0+26.3`).
- Vanilla 26.3 has no smooth stone stairs (only `smooth_stone_slab`), so a stairs-shaped smooth stone recipe
  is free.

## Route
**Loader API + Mixin (Fabric).** Blocks, items, recipes, models and tests go through Fabric API and data
packs. Player movement has no API, so it is patched with Mixin. The client is authoritative for its own
movement in Minecraft, so the surf physics runs on the client; the server only needs collision that agrees
with it and fall damage that ignores ramp contact.

## Physics evidence (from the surf repo, `docs/MOVEMENT_REFERENCE.md`)
- Reference engine: CS:S dedicated server build 11003710. Tick interval float 0.015 s (66.67 Hz).
- Surf cvars: gravity 800, accelerate 5, airaccelerate 150, friction 4, stopspeed 75, maxvelocity 3500,
  knife speed 250, air wish speed cap 30, jump `fround(sqrt(2*800*57))`, step 18, ground normal z >= 0.7,
  hull 32x32x62, auto bunny hop on, no bunny hop speed cap.
- Ramp clipping leaves a 1/32 unit/s outward component and is computed in float in Source axis order;
  velocity and feet origin are stored as float in Source units at tick boundaries.
- Checked-in recordings: `surf/tests/fixtures/css-reference/*.csv` (before/after rows per tick).
- Real-map ramp angles (local KSF maps, histogram of surf faces): the mode is **49-51 degrees**, then 55-56,
  60-65. A 5:4 rise:run slope is 51.3 degrees; 2:1 is 63.4 degrees. A 1x1 45-degree wedge is walkable ground
  in Source (normal z 0.707 >= 0.7), not a surf ramp.
- `local-content/css-reference/kitsune-ramp.json` (gitignored) holds the surf_kitsune brushes around the
  ramp captures, from `node tools/extract-kitsune-brushes.mjs` (needs the user's own
  `surf/local-content/maps/surf_kitsune.bsp`, SHA-256 checked). The region also intersects `func_brush *56`
  (solidbsp=0, collides through its compiled VPhysics hull, not raw planes); if the ramp replays need it,
  they will show it.

## Design decisions
- **Units:** 1 block = 1 m, 1 Source unit = 0.0254 m (the surf repo's scale): 39.37 units per block.
  Source (x, y, z-up) = (MC x, -MC z, MC y) x 39.37. Source yaw = -MC yaw - 90 degrees.
- **Physics core** (`dev.afunk.surfcraft.physics`, no Minecraft imports): a Java port of the surf repo's CS:S
  movement (`src/physics/player.ts` cssMovement path without duck/ladder/water/push, `source-move.ts`,
  BSP branch of `source-hull.ts`), in Source units and axes. Validated by replaying the CS:S recordings.
- **Ramp blocks:** `surf_ramp` (5:4, 51.3 degrees) and `steep_surf_ramp` (2:1, 63.4 degrees). Each cell
  stores `facing` (the direction the slope faces, i.e. descends toward) and `cut` c in 1..p+q: the cell's solid
  is `p*u + q*y <= c` in cell coordinates (u along facing from the back edge, y up), c = p+q is a full cell.
  Placement continues the plane of a neighbouring ramp of the same type and facing
  (`c = c_n + p*(U_n - U) + q*(y_n - y)`), so blocks join into one smooth slope of any size; with no
  neighbour, c = p (the slope starts at the cell's bottom front edge).
- **Collision:** vanilla collision (mobs, items, `isPlayerCollidingWithAnythingNew`) sees an inscribed
  staircase (8 slices), which lies entirely under the true plane. Players never use it near ramps: a
  `Entity.collide` hook slides players against the exact brushes (both sides), and the surf controller
  traces the exact brushes. Exact cell brushes carry axial planes at the cell's **tight** AABB (Source's
  axial bevels); without them a box sweep hits phantom geometry above the slope's top edge.
- **Controller:** client only. Runs CS:S movement in 0.015 s substeps per 50 ms tick (time accumulator),
  view yaw interpolated across substeps, when the player is eligible (on foot, not flying, gliding, riding,
  swimming, climbing, or spectating) and near a ramp, or airborne/briefly grounded after surfing. The
  published Minecraft position is the core state swept forward to the tick boundary, so 3/4-substep ticks
  do not judder.
- **Server:** no movement code; the collide hook makes its re-simulated move agree with the client, and
  fall distance resets on ramp contact.

## Wave A results (2026-10-01)
### Physics core (`dev.afunk.surfcraft.physics`)
- Java port of the surf repo's cssMovement path + BSP hull trace, in Source units. Replays against the real CS:S
  server: 14 flat recordings (worst 0.000183 u, 0.000046 u/s, stamina exact, grounded exact every tick) and
  4 surf_kitsune ramp recordings (worst 0.006348 u, 0.000671 u/s; 53 and 79 surf-contact ticks). 17 recordings
  that duck are skipped (duck is not ported); route recordings need triggers/teleports.
- Kitsune wall recordings pass only with `func_brush *57` modelled as its compiled VPhysics hull (its raw planes
  shrunk 0.5 u on every face, checked to 1e-5 against the BSP physics lump; not general: 14 of 58 func_brush
  brushes compile differently). Next step: export compiled hulls in `tools/extract-kitsune-brushes.mjs`.
- **Seam rampbug (the main finding).** One brush per ramp cell rampbugs exactly like CS:S multi-brush ramps:
  Source's box trace has a ~1/32-unit dead zone at seams the hull's contact edge crosses (the cell being left
  counts the hull gone, the cell being entered reports an internal axial face; a later brush with a negative raw
  entry fraction overrides an earlier hit clamped to 0; full cells touching the slope at a corner report axial
  faces). 41-59 of 300 random runs per slope left the one-brush path. **Fix:** `RampBrushes.of(cells)` merges
  identical cells along the length into prisms, adds one 1/8-unit seamless slab per run of cut cells (listed
  last) and bevels corner-touching full cells with the slope plane: 0/300 diverge, bit-identical to one brush.
  Integration rule: block boxes first, `RampBrushes` last and in order. Non-ramp solid blocks touching a slope
  at a corner (stone used as ramp support) still need the slope plane as a bevel.
- Strafing hard into a level surf ramp climbs (+12.8 u/s on 51°, +6.1 on 63°) to the apex: real CS:S behaviour.
- Float origins: run physics in a rebased local frame (|coords| < ~16384 u). Trace cost ~0.1-0.4 µs; 1-2 µs/tick.
### Blocks, rendering, data (`dev.afunk.surfcraft.block`, `client.RampModels`)
- `SurfRampBlock(p, q)`: `surf_ramp` 5:4 and `steep_surf_ramp` 2:1, state `facing` + `cut` (1..p+q). The cut
  range must exist before `super()`: JDK 25 flexible constructor bodies.
- Joining: the 26-neighbour rule mis-cut ~80% of random build orders (full cells store p+q and lose the plane);
  the shipped rule searches breadth-first through the same-type/facing ramp (<= 8 blocks) for the nearest partial
  cell: 0/1000 wrong.
- Exact-geometry rendering: `ModelLoadingPlugin.registerBlockStateResolver` + `MeshQuadCollection` per state (no
  blockstate JSON); items via an `UnbakedModelDeserializer` (`"fabric:type": "surfcraft:ramp"`). UV lock gives
  world-aligned textures; vertices snapped to the 1/(p*q) grid so neighbours meet exactly.
- Recipes: 6 smooth stone (stairs pattern) -> 6 Surf Ramp; 4 (steep L) -> 4 Steep Surf Ramp; stonecutting 1 -> 1.
- Tests: `./gradlew build` runs JUnit (37) and the server game tests (7); `./gradlew runClientGameTest` opens a
  client, builds a showcase and writes screenshots to `build/gametest/screenshots/`.
- Gotcha: game tests run millions of blocks out (x ~5.3e6), where world-space plane constants carry ~1e-9 of
  double rounding: compare relatively, and compute physics in a local frame.
### Movement research
- `docs/dev/mc26-movement.md`: the 26.3 pipeline, server checks (moved wrongly = 0.25 blocks *horizontal*
  re-simulation error, y ignored; creative exempt; moved too quickly 100 sq. blocks per packet, singleplayer
  owner exempt), and the mixin plan (applyInput TAIL, Player.travel HEAD, Entity.collide HEAD, checkFallDamage).
### Dev log
- `devlog/` (screenshots + `index.html`), published with `tools/devlog.py deploy` to Railway project
  `surfcraft-devlog`: https://devlog-production-6292.up.railway.app
- Homebrew's python3 is broken on this Mac (pyexpat symbol mismatch; uv refuses it): use `.tools/venv/bin/python`.
